package com.quantxt.nlp;

import org.apache.log4j.Logger;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.InMemoryLookupCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by u6014526 on 5/18/2016.
 */
public class word2vec {
    final private static Logger logger = Logger.getLogger(word2vec.class);

    public static void trainWordVev(final String inputFilename,
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
            // Strip white space before and after for each line
//            getSentenceFile(45);
            SentenceIterator iter = new BasicLineIterator(new File(inputFilename));
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

        double sim = wordVectors.similarity("tesla", "solar");
        logger.info("Similarity between tesla and solar: " + sim);
        sim = wordVectors.similarity("uber", "solar");
        logger.info("Similarity between uber and solar: " + sim);
    }

    public static void main(String[] args) throws Exception {
        int topics = 150;
//        String input  = "SNPNewsBodies.list";
//        Utilities.createSentencesfromDir(75, input, false);  //false for using the body, true for headline
 //       String output = "models" + File.separator + "word2vecSNPNewsBody2_" + topics + ".txt";
        String input = "/Users/matin/git/quantxt/qtingestor/tech.txt";
        String output = "/Users/matin/git/quantxt/qtingestor/snp500_w2vec_150.txt";
        trainWordVev(input, output, topics);
    }
}
