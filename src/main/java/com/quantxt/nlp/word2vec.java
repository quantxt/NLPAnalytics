package com.quantxt.nlp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quantxt.nlp.types.TextNormalizer;
import org.apache.log4j.Logger;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.*;
import java.util.*;


/**
 * Created by matin on 5/18/2016.
 */

public class word2vec {
    final private static Logger logger = Logger.getLogger(word2vec.class);

    public static void trainWordVev(final InputStream is,
                                    final String w2vecOutputFilename,
                                    final int dim) throws IOException {
        WordVectors wordVectors;
        File serializedFile = new File(w2vecOutputFilename);
        if (serializedFile.exists()) {
            logger.info("reading " + w2vecOutputFilename + " from disk... ");
            wordVectors = WordVectorSerializer.loadTxtVectors(serializedFile);
            logger.info("loaded");
        } else {
            logger.info("Load & Vectorize Sentences....");

            SentenceIterator iter = new BasicLineIterator(is);
            // Split on white spaces in the line to get words
            TokenizerFactory t = new LinePreProcess();
            t.setTokenPreProcessor(new TextPreProcessor());

            AbstractCache cache = new AbstractCache();
            WeightLookupTable table = new InMemoryLookupTable.Builder()
                    .vectorLength(dim)
                    .useAdaGrad(false)
                    .cache(cache)
                    .lr(0.025f)
                    .build();

            Word2Vec vec = new Word2Vec.Builder()
                    .minWordFrequency(2)
                    .iterations(1)
                    .layerSize(dim)
                    .seed(42)
                    .batchSize(100)
                    .windowSize(5)
                    .lookupTable(table)
    //                .stopWords(getStopWords())
                    .vocabCache(cache)
                    .iterate(iter)
                    .tokenizerFactory(t)
                    .build();

            logger.info("Fitting Word2Vec model....");
            vec.fit();
            WordVectorSerializer.writeWordVectors(vec, w2vecOutputFilename);
            wordVectors = WordVectorSerializer.fromTableAndVocab(table, cache);
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
        int topics = 20;
        InputStream input = new FileInputStream("in.txt");
        String output = "out.txt";
        trainWordVev(input, output, topics);
    }
}
