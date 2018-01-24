package com.quantxt.nlp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.ENDocumentInfo;


/**
 * Created by matin on 5/18/2016.
 */

public class word2vec {
    final private static Logger logger = LoggerFactory.getLogger(word2vec.class);
    private WordVectors w2v;
    private TokenizerFactory tokenizer;
    private int minFreq = 4;

    public word2vec(int m)
    {
        minFreq = m;
        tokenizer = new LinePreProcess(new ENDocumentInfo("", ""));
        tokenizer.setTokenPreProcessor(new TextPreProcessor());
    }

    public void load(final File f) throws FileNotFoundException, UnsupportedEncodingException {
        w2v = WordVectorSerializer.loadTxtVectors(f);
    }
    public void set(WordVectors wv){
        w2v = wv;
    }

    public WordVectors getW2v(){
        return w2v;
    }

    public double[] getWordVector(String w){
        return w2v.getWordVector(w);
    }

    public Map<String, Double> getCloseWordRankMap(String w, int i){
        Collection<String> collection = w2v.wordsNearest(w, i);
        HashMap<String, Double> map = new HashMap<>();
        map.put(w, 1d);
        for (String s: collection){
            map.put(s, w2v.similarity(w,s));
        }
        return map;
    }

    public double getAverageDistance(String source, String target){
        String parts1[] = source.split("\\s+");
        String parts2[] = target.split("\\s+");
        double numWords = 0;

        double sum = 0;
        for (String w1 : parts1){
            for (String w2 : parts2){
                try {
                    Double d = w2v.similarity(w1, w2);
                    if (!d.isNaN() && Math.abs(d) > 0) {
                        sum += d;
                        numWords+=1;
                    }
                } catch (NullPointerException n){

                }
            }
        }
        if (numWords == 0) return 0;
        return sum / numWords;
    }

    public double getDistance(String w1, String w2){
        return w2v.similarity(w1, w2);
    }

    public Collection<String> getClosest(String w1, int d){
        return w2v.wordsNearest(w1, d);
    }

    public void train(final InputStream is,
                      final String w2vecOutputFilename,
                      final int dim,
                      boolean write) throws Exception {
        File serializedFile = new File(w2vecOutputFilename);
        if (serializedFile.exists()) {
            logger.info(w2vecOutputFilename + " already exists. Trainer is terminated.");
  //          wordVectors = WordVectorSerializer.loadTxtVectors(serializedFile);
  //          logger.info("loaded");
        } else {
            logger.info("Load & Vectorize Sentences....");

            SentenceIterator iter = new BasicLineIterator(is);
            // Split on white spaces in the line to get words

            AbstractCache cache = new AbstractCache();
            WeightLookupTable table = new InMemoryLookupTable.Builder()
                    .vectorLength(dim)
                    .useAdaGrad(false)
                    .cache(cache)
                    .build();

            Word2Vec vec = new Word2Vec.Builder()
                    .minWordFrequency(minFreq)
                    .iterations(1)
                    .layerSize(dim)
                    .seed(42)
                    .epochs(1)
                    .batchSize(1)
                    .windowSize(5)
                    .lookupTable(table)
    //                .stopWords(getStopWords())
                    .vocabCache(cache)
                    .iterate(iter)
                    .tokenizerFactory(tokenizer)
                    .build();

            logger.info("Fitting Word2Vec model....");
            vec.fit();
            if (write) {
                WordVectorSerializer.writeWordVectors(vec, w2vecOutputFilename);
            }
            w2v = WordVectorSerializer.fromTableAndVocab(table, cache);
    //        logger.info("sim: " + sim );
        }
        logger.info("Training word2vec finished.");

        /*
        double sim = wordVectors.similarity(TextNormalizer.normalize("mobile"), TextNormalizer.normalize("ios"));
        logger.info("Sim is " + sim);
        Gson gson = new Gson();
        Collection<String> nearestWords = wordVectors.wordsNearest(TextNormalizer.normalize("Computer") , 10);
        logger.info(gson.toJson(nearestWords));
        */
    }

    public static void main(String[] args) throws Exception {
        FileInputStream fi = new FileInputStream(new File("test.txtx"));
        String out = "w2v";
        word2vec w2v = new word2vec(2);
        w2v.train(fi, out, 10, true);
    }
}
