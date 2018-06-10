package com.quantxt.nlp.types;


import lombok.NonNull;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.enums.ListenerEvent;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by matin on 6/9/18.
 */

public class FastSequenceVectors<T extends SequenceElement> extends SequenceVectors<T> {

    /**
     * Starts training over
     */
    public void fit() {
        Properties props = Nd4j.getExecutioner().getEnvironmentInformation();
        if (props.getProperty("backend").equals("CUDA")) {
            if (Nd4j.getAffinityManager().getNumberOfDevices() > 1)
                throw new IllegalStateException("Multi-GPU word2vec/doc2vec isn't available atm");
            //if (!NativeOpsHolder.getInstance().getDeviceNativeOps().isP2PAvailable())
            //throw new IllegalStateException("Running Word2Vec on multi-gpu system requires P2P support between GPUs, which looks to be unavailable on your system.");
        }

        Nd4j.getRandom().setSeed(configuration.getSeed());

        AtomicLong timeSpent = new AtomicLong(0);
        if (!trainElementsVectors && !trainSequenceVectors)
            throw new IllegalStateException(
                    "You should define at least one training goal 'trainElementsRepresentation' or 'trainSequenceRepresentation'");
        if (iterator == null)
            throw new IllegalStateException("You can't fit() data without SequenceIterator defined");

        if (resetModel || (lookupTable != null && vocab != null && vocab.numWords() == 0)) {
            // build vocabulary from scratches
            buildVocab();
        }

        WordVectorSerializer.printOutProjectedMemoryUse(vocab.numWords(), configuration.getLayersSize(),
                configuration.isUseHierarchicSoftmax() && configuration.getNegative() > 0 ? 3 : 2);

        if (vocab == null || lookupTable == null || vocab.numWords() == 0)
            throw new IllegalStateException("You can't fit() model with empty Vocabulary or WeightLookupTable");

        // if model vocab and lookupTable is built externally we basically should check that lookupTable was properly initialized
        if (!resetModel || existingModel != null) {
            lookupTable.resetWeights(false);
        } else {
            // otherwise we reset weights, independent of actual current state of lookup table
            lookupTable.resetWeights(true);

            // if preciseWeights used, we roll over data once again
            if (configuration.isPreciseWeightInit()) {
                log.info("Using precise weights init...");
                iterator.reset();

                while (iterator.hasMoreSequences()) {
                    Sequence<T> sequence = iterator.nextSequence();

                    // initializing elements, only once
                    for (T element : sequence.getElements()) {
                        T realElement = vocab.tokenFor(element.getLabel());

                        if (realElement != null && !realElement.isInit()) {
                            Random rng = Nd4j.getRandomFactory().getNewRandomInstance(
                                    configuration.getSeed() * realElement.hashCode(),
                                    configuration.getLayersSize() + 1);

                            INDArray randArray = Nd4j.rand(new int[]{1, configuration.getLayersSize()}, rng).subi(0.5)
                                    .divi(configuration.getLayersSize());

                            lookupTable.getWeights().getRow(realElement.getIndex()).assign(randArray);
                            realElement.setInit(true);
                        }
                    }

                    // initializing labels, only once
                    for (T label : sequence.getSequenceLabels()) {
                        T realElement = vocab.tokenFor(label.getLabel());

                        if (realElement != null && !realElement.isInit()) {
                            Random rng = Nd4j.getRandomFactory().getNewRandomInstance(
                                    configuration.getSeed() * realElement.hashCode(),
                                    configuration.getLayersSize() + 1);
                            INDArray randArray = Nd4j.rand(new int[]{1, configuration.getLayersSize()}, rng).subi(0.5)
                                    .divi(configuration.getLayersSize());

                            lookupTable.getWeights().getRow(realElement.getIndex()).assign(randArray);
                            realElement.setInit(true);
                        }
                    }
                }

                this.iterator.reset();
            }
        }


        initLearners();

        log.info("Starting learning process...");
        timeSpent.set(System.currentTimeMillis());
        if (this.stopWords == null)
            this.stopWords = new ArrayList<>();

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        final AtomicLong wordsCounter = new AtomicLong(0);
        for (int currentEpoch = 1; currentEpoch <= numEpochs; currentEpoch++) {
            final AtomicLong linesCounter = new AtomicLong(0);
            FastSequenceVectors.AsyncSequencer sequencer = new FastSequenceVectors.AsyncSequencer(this.iterator,
                    this.stopWords, currentEpoch, wordsCounter, linesCounter);
            executor.execute(sequencer);
            executor.shutdown();

            while (executor.getActiveCount() > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {

                }
            }

            // TODO: fix this to non-exclusive termination
            if (trainElementsVectors && elementsLearningAlgorithm != null
                    && (!trainSequenceVectors || sequenceLearningAlgorithm == null)
                    && elementsLearningAlgorithm.isEarlyTerminationHit()) {
                break;
            }

            if (trainSequenceVectors && sequenceLearningAlgorithm != null
                    && (!trainElementsVectors || elementsLearningAlgorithm == null)
                    && sequenceLearningAlgorithm.isEarlyTerminationHit()) {
                break;
            }
            log.info("Epoch [" + currentEpoch + "] finished; Elements processed so far: [" + wordsCounter.get()
                    + "];  Sequences processed: [" + linesCounter.get() + "]");

            if (eventListeners != null && !eventListeners.isEmpty()) {
                for (VectorsListener listener : eventListeners) {
                    if (listener.validateEvent(ListenerEvent.EPOCH, currentEpoch))
                        listener.processEvent(ListenerEvent.EPOCH, this, currentEpoch);
                }
            }
        }

        log.info("Time spent on training: {} ms", System.currentTimeMillis() - timeSpent.get());
    }

    /**
     * This class is used to fetch data from iterator in background thread, and convert it to List<VocabularyWord>
     * <p>
     * It becomes very usefull if text processing pipeline behind iterator is complex, and we're not loading data from simple text file with whitespaces as separator.
     * Since this method allows you to hide preprocessing latency in background.
     * <p>
     * This mechanics will be change to PrefetchingSentenceIterator wrapper.
     */
    protected class AsyncSequencer implements Runnable {

        private final SequenceIterator<T> iterator;

        private final int limitUpper;
        private AtomicLong linesCounter;
        private AtomicLong wordsCounter;
        private Collection<String> stopList;
        private int epoch;

        public AsyncSequencer(SequenceIterator<T> iterator,
                              @NonNull Collection<String> stopList,
                              int epoch,
                              AtomicLong wordsCounter,
                              AtomicLong linesCounter) {
            this.iterator = iterator;
            this.linesCounter = linesCounter;
            this.wordsCounter = wordsCounter;
            this.epoch = epoch;
            this.iterator.reset();
            this.stopList = stopList;
            limitUpper = workers * batchSize * 2;
        }

        @Override
        public void run() {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

            List<Sequence<T>> buffer = new ArrayList<>();
            while (this.iterator.hasMoreSequences()) {
                // if buffered level is below limitLower, we're going to fetch limitUpper number of strings from fetcher
                if (buffer.size() < batchSize) {
                    update();
                    AtomicInteger linesLoaded = new AtomicInteger(0);

                    while (linesLoaded.getAndIncrement() < limitUpper && this.iterator.hasMoreSequences()) {
                        Sequence<T> document = this.iterator.nextSequence();

                        /*
                            We can't hope/assume that underlying iterator contains synchronized elements
                            That's why we're going to rebuild sequence from vocabulary
                          */
                        Sequence<T> newSequence = new Sequence<>();

                        if (document.getSequenceLabel() != null) {
                            T newLabel = vocab.wordFor(document.getSequenceLabel().getLabel());
                            if (newLabel != null)
                                newSequence.setSequenceLabel(newLabel);
                        }

                        for (T element : document.getElements()) {
                            if (stopList.contains(element.getLabel())) {
                                continue;
                            }
                            T realElement = vocab.wordFor(element.getLabel());

                            // please note: this serquence element CAN be absent in vocab, due to minFreq or stopWord or whatever else
                            if (realElement != null) {
                                newSequence.addElement(realElement);
                            } else if (useUnknown && unknownElement != null) {
                                newSequence.addElement(unknownElement);
                            }
                        }

                        // due to subsampling and null words, new sequence size CAN be 0, so there's no need to insert empty sequence into processing chain
                        if (!newSequence.getElements().isEmpty()) {
                            buffer.add(newSequence);
                        }
                        linesLoaded.incrementAndGet();
                    }

                } else {
                    executor.execute(new FastSequenceVectors.VectorCalculationsThread(epoch, wordsCounter, vocab.totalWordOccurrences(),
                            linesCounter, buffer, numEpochs));
                    buffer.clear();
                }

                if (executor.getActiveCount() > workers * 2) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {

                    }
                }
            }
            if (buffer.size() >0) {
                executor.execute(new FastSequenceVectors.VectorCalculationsThread(epoch, wordsCounter, vocab.totalWordOccurrences(),
                        linesCounter, buffer, numEpochs));
            }

            while (executor.getActiveCount() > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {

                }
            }
            executor.shutdown();
        }
    }

    /**
     * VectorCalculationsThreads are used for vector calculations, and work together with AsyncIteratorDigitizer.
     * Basically, all they do is just transfer of digitized sentences into math layer.
     * <p>
     * Please note, they do not iterate the sentences over and over, each sentence processed only once.
     * Training corpus iteration is implemented in fit() method.
     */
    private class VectorCalculationsThread implements Runnable {
        private final int epochNumber;
        private final AtomicLong wordsCounter;
        private final long totalWordsCount;
        private final AtomicLong totalLines;

        private final AtomicLong nextRandom;
        private final AtomicLong timer;
        private final long startTime;
        private final int totalEpochs;
        private final List<Sequence<T>> buffer;

        /*
                Long constructors suck, so this should be reduced to something reasonable later
         */
        public VectorCalculationsThread(int epoch, AtomicLong wordsCounter, long totalWordsCount,
                                        AtomicLong linesCounter, List<Sequence<T>> sequences, int totalEpochs) {
            this.totalEpochs = totalEpochs;
            this.epochNumber = epoch;
            this.wordsCounter = wordsCounter;
            this.totalWordsCount = totalWordsCount;
            this.totalLines = linesCounter;
            this.buffer = new ArrayList<>(sequences);
            this.timer = new AtomicLong(System.currentTimeMillis());
            this.startTime = timer.get();
            int x = ThreadLocalRandom.current().nextInt(0, workers + 1);
            this.nextRandom = new AtomicLong(x);
        }

        @Override
        public void run() {
            try {

                // getting back number of iterations
                for (int i = 0; i < numIterations; i++) {

                    // we roll over sequences derived from digitizer, it's NOT window loop
                    for (Sequence<T> sequence : buffer) {

                        //log.info("LR before: {}; wordsCounter: {}; totalWordsCount: {}", learningRate.get(), this.wordsCounter.get(), this.totalWordsCount);
                        double alpha = Math.max(minLearningRate,
                                learningRate.get() * (1 - (1.0 * this.wordsCounter.get()
                                        / ((double) this.totalWordsCount) / (numIterations
                                        * totalEpochs))));
                        trainSequence(sequence, nextRandom, alpha);

                        // increment processed word count, please note: this affects learningRate decay
                        totalLines.incrementAndGet();
                        this.wordsCounter.addAndGet(sequence.getElements().size());

                        if (totalLines.get() % 100000 == 0) {
                            long currentTime = System.currentTimeMillis();
                            long timeSpent = currentTime - timer.get();

                            timer.set(currentTime);
                            long totalTimeSpent = currentTime - startTime;

                            double seqSec = (100000.0 / ((double) timeSpent / 1000.0));
                            double wordsSecTotal = this.wordsCounter.get() / ((double) totalTimeSpent / 1000.0);

                            log.info("Epoch: [{}]; Words vectorized so far: [{}];  Lines vectorized so far: [{}]; Seq/sec: [{}]; Words/sec: [{}]; learningRate: [{}]",
                                    this.epochNumber, this.wordsCounter.get(), this.totalLines.get(),
                                    String.format("%.2f", seqSec), String.format("%.2f", wordsSecTotal),
                                    alpha);
                        }
                        if (eventListeners != null && !eventListeners.isEmpty()) {
                            for (VectorsListener listener : eventListeners) {
                                if (listener.validateEvent(ListenerEvent.LINE, totalLines.get()))
                                    listener.processEvent(ListenerEvent.LINE, FastSequenceVectors.this,
                                            totalLines.get());
                            }
                        }
                    }

                    if (eventListeners != null && !eventListeners.isEmpty()) {
                        for (VectorsListener listener : eventListeners) {
                            if (listener.validateEvent(ListenerEvent.ITERATION, i))
                                listener.processEvent(ListenerEvent.ITERATION, FastSequenceVectors.this, i);
                        }
                    }
                }


            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (trainElementsVectors) {
                elementsLearningAlgorithm.finish();
            }

            if (trainSequenceVectors) {
                sequenceLearningAlgorithm.finish();
            }
        }
    }
}