package com.quantxt.nlp;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;


/**
 * Created by matin on 5/18/2016.
 */

public class word2vec {
    final private static Logger logger = LoggerFactory.getLogger(word2vec.class);

    public word2vec(){

    }
    public void train(final InputStream is,
                      final String w2vecOutputFilename,
                      final int dim) throws IOException {
  //      WordVectors wordVectors;
        File serializedFile = new File(w2vecOutputFilename);
        if (serializedFile.exists()) {
            logger.info(w2vecOutputFilename + " already exists. Trainer is terminated.");
  //          wordVectors = WordVectorSerializer.loadTxtVectors(serializedFile);
  //          logger.info("loaded");
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
                    .minWordFrequency(3)
                    .iterations(1)
                    .layerSize(dim)
                    .seed(42)
                    .batchSize(1)
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
  //          wordVectors = WordVectorSerializer.fromTableAndVocab(table, cache);
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
        /*
        String in = "/Users/matin/git/QTdatacollect/text.tr.corpus";
        ENDocumentInfo.init();
        JsonParser parser = new JsonParser();
        try {
            BufferedReader br = new BufferedReader(new FileReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    JsonObject json = parser.parse(line).getAsJsonObject();
                    String ttle = json.get("title").getAsString();
                    String body = json.get("body").getAsString();
                    ENDocumentInfo doc = new ENDocumentInfo(body, ttle);
                    doc.processDoc();
                    for (String s : doc.getSentences()) {
                        Files.write(Paths.get("text.tr.corpus.lines"), (s + "\n").getBytes(), StandardOpenOption.APPEND);
                    }
                } catch (Exception ee){
                    logger.error(line);
                }
            }
        } catch (Exception e){
            logger.error(e.getMessage());
        }
*/
        int topics = 150;
        InputStream input = new FileInputStream("text.tr.corpus.lines.10000");
        String output = "/Users/matin/git/QTdatacollect/foursquare/reuters_150_w2v.10000.txt";
        word2vec w2v = new word2vec();
        w2v.train(input, output, topics);
    }
}
