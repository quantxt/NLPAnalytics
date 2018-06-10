package com.quantxt.nlp;

import com.quantxt.nlp.types.FastSequenceVectors;
import lombok.Getter;
import lombok.NonNull;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.ElementsLearningAlgorithm;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.embeddings.reader.ModelUtils;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.documentiterator.DocumentIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.invertedindex.InvertedIndex;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.StreamLineIterator;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultStreamTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collection;
import java.util.List;

/**
 * Created by matin on 6/9/18.
 */
public class FastWord2Vec extends FastSequenceVectors<VocabWord> {
    final private static Logger logger = LoggerFactory.getLogger(FastWord2Vec.class);

    @Getter
    protected transient TokenizerFactory tokenizerFactory;

    public static class Builder extends FastSequenceVectors.Builder<VocabWord> {
        protected SentenceIterator sentenceIterator;
        protected LabelAwareIterator labelAwareIterator;
        protected TokenizerFactory tokenizerFactory;
        protected boolean allowParallelTokenization = true;


        public Builder() {

        }

        /**
         * This method has no effect for Word2Vec
         *
         * @param vec existing WordVectors model
         * @return
         */
        @Override
        protected FastWord2Vec.Builder useExistingWordVectors(@NonNull WordVectors vec) {
            return this;
        }

        public Builder(@NonNull VectorsConfiguration configuration) {
            super(configuration);
            this.allowParallelTokenization = configuration.isAllowParallelTokenization();
        }

        public FastWord2Vec.Builder iterate(@NonNull DocumentIterator iterator) {
            this.sentenceIterator = new StreamLineIterator.Builder(iterator).setFetchSize(100).build();
            return this;
        }

        /**
         * This method used to feed SentenceIterator, that contains training corpus, into ParagraphVectors
         *
         * @param iterator
         * @return
         */
        public FastWord2Vec.Builder iterate(@NonNull SentenceIterator iterator) {
            this.sentenceIterator = iterator;
            return this;
        }

        /**
         * This method defines TokenizerFactory to be used for strings tokenization during training
         * PLEASE NOTE: If external VocabCache is used, the same TokenizerFactory should be used to keep derived tokens equal.
         *
         * @param tokenizerFactory
         * @return
         */
        public FastWord2Vec.Builder tokenizerFactory(@NonNull TokenizerFactory tokenizerFactory) {
            this.tokenizerFactory = tokenizerFactory;
            return this;
        }

        @Deprecated
        public FastWord2Vec.Builder index(@NonNull InvertedIndex<VocabWord> index) {
            return this;
        }

        /**
         * This method used to feed SequenceIterator, that contains training corpus, into ParagraphVectors
         *
         * @param iterator
         * @return
         */
        @Override
        public FastWord2Vec.Builder iterate(@NonNull SequenceIterator<VocabWord> iterator) {
            super.iterate(iterator);
            return this;
        }

        /**
         * This method used to feed LabelAwareIterator, that is usually used
         *
         * @param iterator
         * @return
         */
        public FastWord2Vec.Builder iterate(@NonNull LabelAwareIterator iterator) {
            this.labelAwareIterator = iterator;
            return this;
        }

        /**
         * This method defines mini-batch size
         * @param batchSize
         * @return
         */
        @Override
        public FastWord2Vec.Builder batchSize(int batchSize) {
            super.batchSize(batchSize);
            return this;
        }

        /**
         * This method defines number of iterations done for each mini-batch during training
         * @param iterations
         * @return
         */
        @Override
        public FastWord2Vec.Builder iterations(int iterations) {
            super.iterations(iterations);
            return this;
        }

        /**
         * This method defines number of epochs (iterations over whole training corpus) for training
         * @param numEpochs
         * @return
         */
        @Override
        public FastWord2Vec.Builder epochs(int numEpochs) {
            super.epochs(numEpochs);
            return this;
        }

        /**
         * This method defines number of dimensions for output vectors
         * @param layerSize
         * @return
         */
        @Override
        public FastWord2Vec.Builder layerSize(int layerSize) {
            super.layerSize(layerSize);
            return this;
        }

        /**
         * This method defines initial learning rate for model training
         *
         * @param learningRate
         * @return
         */
        @Override
        public FastWord2Vec.Builder learningRate(double learningRate) {
            super.learningRate(learningRate);
            return this;
        }

        /**
         * This method defines minimal word frequency in training corpus. All words below this threshold will be removed prior model training
         *
         * @param minWordFrequency
         * @return
         */
        @Override
        public FastWord2Vec.Builder minWordFrequency(int minWordFrequency) {
            super.minWordFrequency(minWordFrequency);
            return this;
        }

        /**
         * This method defines minimal learning rate value for training
         *
         * @param minLearningRate
         * @return
         */
        @Override
        public FastWord2Vec.Builder minLearningRate(double minLearningRate) {
            super.minLearningRate(minLearningRate);
            return this;
        }

        /**
         * This method defines whether model should be totally wiped out prior building, or not
         *
         * @param reallyReset
         * @return
         */
        @Override
        public FastWord2Vec.Builder resetModel(boolean reallyReset) {
            super.resetModel(reallyReset);
            return this;
        }

        /**
         * This method sets vocabulary limit during construction.
         *
         * Default value: 0. Means no limit
         *
         * @param limit
         * @return
         */
        @Override
        public FastWord2Vec.Builder limitVocabularySize(int limit) {
            super.limitVocabularySize(limit);
            return this;
        }

        /**
         * This method allows to define external VocabCache to be used
         *
         * @param vocabCache
         * @return
         */
        @Override
        public FastWord2Vec.Builder vocabCache(@NonNull VocabCache<VocabWord> vocabCache) {
            super.vocabCache(vocabCache);
            return this;
        }

        /**
         * This method allows to define external WeightLookupTable to be used
         *
         * @param lookupTable
         * @return
         */
        @Override
        public FastWord2Vec.Builder lookupTable(@NonNull WeightLookupTable<VocabWord> lookupTable) {
            super.lookupTable(lookupTable);
            return this;
        }

        /**
         * This method defines whether subsampling should be used or not
         *
         * @param sampling set > 0 to subsampling argument, or 0 to disable
         * @return
         */
        @Override
        public FastWord2Vec.Builder sampling(double sampling) {
            super.sampling(sampling);
            return this;
        }

        /**
         * This method defines whether adaptive gradients should be used or not
         *
         * @param reallyUse
         * @return
         */
        @Override
        public FastWord2Vec.Builder useAdaGrad(boolean reallyUse) {
            super.useAdaGrad(reallyUse);
            return this;
        }

        /**
         * This method defines whether negative sampling should be used or not
         *
         * PLEASE NOTE: If you're going to use negative sampling, you might want to disable HierarchicSoftmax, which is enabled by default
         *
         * Default value: 0
         *
         * @param negative set > 0 as negative sampling argument, or 0 to disable
         * @return
         */
        @Override
        public FastWord2Vec.Builder negativeSample(double negative) {
            super.negativeSample(negative);
            return this;
        }

        /**
         * This method defines stop words that should be ignored during training
         *
         * @param stopList
         * @return
         */
        @Override
        public FastWord2Vec.Builder stopWords(@NonNull List<String> stopList) {
            super.stopWords(stopList);
            return this;
        }

        /**
         * This method is hardcoded to TRUE, since that's whole point of Word2Vec
         *
         * @param trainElements
         * @return
         */
        @Override
        public FastWord2Vec.Builder trainElementsRepresentation(boolean trainElements) {
            throw new IllegalStateException("You can't change this option for Word2Vec");
        }

        /**
         * This method is hardcoded to FALSE, since that's whole point of Word2Vec
         *
         * @param trainSequences
         * @return
         */
        @Override
        public FastWord2Vec.Builder trainSequencesRepresentation(boolean trainSequences) {
            throw new IllegalStateException("You can't change this option for Word2Vec");
        }

        /**
         * This method defines stop words that should be ignored during training
         *
         * @param stopList
         * @return
         */
        @Override
        public FastWord2Vec.Builder stopWords(@NonNull Collection<VocabWord> stopList) {
            super.stopWords(stopList);
            return this;
        }

        /**
         * This method defines context window size
         *
         * @param windowSize
         * @return
         */
        @Override
        public FastWord2Vec.Builder windowSize(int windowSize) {
            super.windowSize(windowSize);
            return this;
        }

        /**
         * This method defines random seed for random numbers generator
         * @param randomSeed
         * @return
         */
        @Override
        public FastWord2Vec.Builder seed(long randomSeed) {
            super.seed(randomSeed);
            return this;
        }

        /**
         * This method defines maximum number of concurrent threads available for training
         *
         * @param numWorkers
         * @return
         */
        @Override
        public FastWord2Vec.Builder workers(int numWorkers) {
            super.workers(numWorkers);
            return this;
        }

        /**
         * Sets ModelUtils that gonna be used as provider for utility methods: similarity(), wordsNearest(), accuracy(), etc
         *
         * @param modelUtils model utils to be used
         * @return
         */
        @Override
        public FastWord2Vec.Builder modelUtils(@NonNull ModelUtils<VocabWord> modelUtils) {
            super.modelUtils(modelUtils);
            return this;
        }

        /**
         * This method allows to use variable window size. In this case, every batch gets processed using one of predefined window sizes
         *
         * @param windows
         * @return
         */
        @Override
        public FastWord2Vec.Builder useVariableWindow(int... windows) {
            super.useVariableWindow(windows);
            return this;
        }

        /**
         * This method allows you to specify SequenceElement that will be used as UNK element, if UNK is used
         *
         * @param element
         * @return
         */
        @Override
        public FastWord2Vec.Builder unknownElement(VocabWord element) {
            super.unknownElement(element);
            return this;
        }

        /**
         * This method allows you to specify, if UNK word should be used internally
         *
         * @param reallyUse
         * @return
         */
        @Override
        public FastWord2Vec.Builder useUnknown(boolean reallyUse) {
            super.useUnknown(reallyUse);
            if (this.unknownElement == null) {
                this.unknownElement(new VocabWord(1.0, Word2Vec.DEFAULT_UNK));
            }
            return this;
        }

        /**
         * This method sets VectorsListeners for this SequenceVectors model
         *
         * @param vectorsListeners
         * @return
         */
        @Override
        public FastWord2Vec.Builder setVectorsListeners(@NonNull Collection<VectorsListener<VocabWord>> vectorsListeners) {
            super.setVectorsListeners(vectorsListeners);
            return this;
        }

        @Override
        public FastWord2Vec.Builder elementsLearningAlgorithm(@NonNull String algorithm) {
            super.elementsLearningAlgorithm(algorithm);
            return this;
        }

        @Override
        public FastWord2Vec.Builder elementsLearningAlgorithm(@NonNull ElementsLearningAlgorithm<VocabWord> algorithm) {
            super.elementsLearningAlgorithm(algorithm);
            return this;
        }

        /**
         * This method enables/disables parallel tokenization.
         *
         * Default value: TRUE
         * @param allow
         * @return
         */
        public FastWord2Vec.Builder allowParallelTokenization(boolean allow) {
            this.allowParallelTokenization = allow;
            return this;
        }

        /**
         * This method ebables/disables periodical vocab truncation during construction
         *
         * Default value: disabled
         *
         * @param reallyEnable
         * @return
         */
        @Override
        public FastWord2Vec.Builder enableScavenger(boolean reallyEnable) {
            super.enableScavenger(reallyEnable);
            return this;
        }

        /**
         * This method enables/disables Hierarchic softmax
         *
         * Default value: enabled
         *
         * @param reallyUse
         * @return
         */
        @Override
        public FastWord2Vec.Builder useHierarchicSoftmax(boolean reallyUse) {
            super.useHierarchicSoftmax(reallyUse);
            return this;
        }

        @Override
        public FastWord2Vec.Builder usePreciseWeightInit(boolean reallyUse) {
            super.usePreciseWeightInit(reallyUse);
            return this;
        }

        public FastWord2Vec build() {
            presetTables();

            FastWord2Vec ret = new FastWord2Vec();

            if (sentenceIterator != null) {
                if (tokenizerFactory == null)
                    tokenizerFactory = new DefaultTokenizerFactory();

                SentenceTransformer transformer = new SentenceTransformer.Builder().iterator(sentenceIterator)
                        .tokenizerFactory(tokenizerFactory).allowMultithreading(allowParallelTokenization)
                        .build();
                this.iterator = new AbstractSequenceIterator.Builder<>(transformer).build();
            }

            if (this.labelAwareIterator != null) {
                if (tokenizerFactory == null)
                    tokenizerFactory = new DefaultTokenizerFactory();

                SentenceTransformer transformer = new SentenceTransformer.Builder().iterator(labelAwareIterator)
                        .tokenizerFactory(tokenizerFactory).allowMultithreading(allowParallelTokenization)
                        .build();
                this.iterator = new AbstractSequenceIterator.Builder<>(transformer).build();
            }

            ret.numEpochs = this.numEpochs;
            ret.numIterations = this.iterations;
            ret.vocab = this.vocabCache;
            ret.minWordFrequency = this.minWordFrequency;
            ret.learningRate.set(this.learningRate);
            ret.minLearningRate = this.minLearningRate;
            ret.sampling = this.sampling;
            ret.negative = this.negative;
            ret.layerSize = this.layerSize;
            ret.batchSize = this.batchSize;
            ret.learningRateDecayWords = this.learningRateDecayWords;
            ret.window = this.window;
            ret.resetModel = this.resetModel;
            ret.useAdeGrad = this.useAdaGrad;
            ret.stopWords = this.stopWords;
            ret.workers = this.workers;
            ret.useUnknown = this.useUnknown;
            ret.unknownElement = this.unknownElement;
            ret.variableWindows = this.variableWindows;
            ret.seed = this.seed;
            ret.enableScavenger = this.enableScavenger;
            ret.vocabLimit = this.vocabLimit;


            ret.iterator = this.iterator;
            ret.lookupTable = this.lookupTable;
            ret.tokenizerFactory = this.tokenizerFactory;
            ret.modelUtils = this.modelUtils;

            ret.elementsLearningAlgorithm = this.elementsLearningAlgorithm;
            ret.sequenceLearningAlgorithm = this.sequenceLearningAlgorithm;

            this.configuration.setLearningRate(this.learningRate);
            this.configuration.setLayersSize(layerSize);
            this.configuration.setHugeModelExpected(hugeModelExpected);
            this.configuration.setWindow(window);
            this.configuration.setMinWordFrequency(minWordFrequency);
            this.configuration.setIterations(iterations);
            this.configuration.setSeed(seed);
            this.configuration.setBatchSize(batchSize);
            this.configuration.setLearningRateDecayWords(learningRateDecayWords);
            this.configuration.setMinLearningRate(minLearningRate);
            this.configuration.setSampling(this.sampling);
            this.configuration.setUseAdaGrad(useAdaGrad);
            this.configuration.setNegative(negative);
            this.configuration.setEpochs(this.numEpochs);
            this.configuration.setStopList(this.stopWords);
            this.configuration.setVariableWindows(variableWindows);
            this.configuration.setUseHierarchicSoftmax(this.useHierarchicSoftmax);
            this.configuration.setPreciseWeightInit(this.preciseWeightInit);
            this.configuration.setModelUtils(this.modelUtils.getClass().getCanonicalName());
            this.configuration.setAllowParallelTokenization(this.allowParallelTokenization);

            if (tokenizerFactory != null) {
                this.configuration.setTokenizerFactory(tokenizerFactory.getClass().getCanonicalName());
                if (tokenizerFactory.getTokenPreProcessor() != null)
                    this.configuration.setTokenPreProcessor(
                            tokenizerFactory.getTokenPreProcessor().getClass().getCanonicalName());
            }

            ret.configuration = this.configuration;

            // we hardcode
            ret.trainSequenceVectors = false;
            ret.trainElementsVectors = true;

            ret.eventListeners = this.vectorsListeners;

            return ret;
        }
    }

    public static class LinePreProcess implements TokenizerFactory {

        private TokenPreProcess tokenPreProcess;

        public LinePreProcess() {

        }

        public Tokenizer create(String toTokenize) {
            DefaultTokenizer t = new DefaultTokenizer(toTokenize.trim());
            t.setTokenPreProcessor(this.tokenPreProcess);
            return t;
        }

        public Tokenizer create(InputStream toTokenize) {
            DefaultStreamTokenizer t = new DefaultStreamTokenizer(toTokenize);
            t.setTokenPreProcessor(this.tokenPreProcess);
            return t;
        }

        public void setTokenPreProcessor(TokenPreProcess preProcessor) {
            this.tokenPreProcess = preProcessor;
        }

        @Override
        public TokenPreProcess getTokenPreProcessor() {
            return null;
        }

    }

    public static class TextPreProcessor implements TokenPreProcess {

        @Override
        public String preProcess(String token) {
            return token;
        }
    }

    public static void main(String[] args) throws Exception {
        FileInputStream fi =
                new FileInputStream(new File("data.txt"));

        TokenizerFactory tokenizer = new LinePreProcess();
        tokenizer.setTokenPreProcessor(new TextPreProcessor());

        int minFreq = 4;
        int dim = 25;

        long start = System.currentTimeMillis();
        SentenceIterator iter = new BasicLineIterator(fi);
        // Split on white spaces in the line to get words

        AbstractCache cache = new AbstractCache();

        WeightLookupTable table = new InMemoryLookupTable.Builder()
                .vectorLength(dim)
                .useAdaGrad(false)
                .cache(cache)
                .build();

        FastWord2Vec vec = new FastWord2Vec.Builder()
                .minWordFrequency(minFreq)
                .iterations(10)
                .workers(50)
                .layerSize(dim)
                .seed(42)
                .epochs(1)
                .batchSize(1024)
                .windowSize(5)
                .lookupTable(table)
                //                .stopWords(getStopWords())
                .vocabCache(cache)
                .iterate(iter)
                .tokenizerFactory(tokenizer)
                .build();

        vec.fit();

        long took = System.currentTimeMillis() - start;
        logger.info("Sim is " +  vec.wordsNearest("research", 10));
        System.out.println("Took: " + took);
    }
}
