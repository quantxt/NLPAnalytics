package com.quantxt.nlp;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
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
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;


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
                    .minWordFrequency(25)
                    .iterations(1)
                    .layerSize(dim)
                    .seed(42)
                    .batchSize(1000000)
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

        //some denugs
        LinePreProcess lp = new LinePreProcess();
        double sim = wordVectors.similarity(lp.normalize("mobile"), lp.normalize("ios"));
        logger.info("Sim is " + sim);
        Gson gson = new Gson();
        Collection<String> nearestWords = wordVectors.wordsNearest(lp.normalize("Computer") , 10);
        logger.info(gson.toJson(nearestWords));
    }


    private static HashSet<String> getKeys() throws IOException {
        String datafile = "/Users/matin/git/quantxt/qtingestor/crunchbase_allUS_118k.txt";

        BufferedReader br = new BufferedReader(new FileReader(datafile));

        JsonParser parser = new JsonParser();
        HashSet<String> keys = new HashSet<>();
        try {
            String line;
            HashSet<String> uniqcomps = new HashSet<>();
            while ((line = br.readLine()) != null) {
                JsonObject json = (JsonObject) parser.parse(line);
                if (!json.has("website")) continue;
                String company = json.get("website").getAsString().replaceAll("^\"|\"$", "");
                if (company == null || company.isEmpty()) continue;
                if (uniqcomps.contains(company)) continue;
                uniqcomps.add(company);

                String cm = company.replaceAll("^(http|https)\\:\\/\\/", "");
                cm = cm.replace("www", "");
                cm = cm.replaceAll("[.\\/]", "").toLowerCase();
                keys.add(cm +".txt");
            }

        } catch (Exception e){
                logger.error("Error " + e);
        }
        return keys;

    }

    private static void getS3InputStream(String bucketName
    , HashSet<String> keys) throws IOException {
        final BasicAWSCredentials awsCreds = new BasicAWSCredentials(
                "AKIAITIPWJ26NCITRYVA", "JQeaZT8br/3dghGgMVYDlPXOEy+3cz1u703l/F/M");
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setProtocol(Protocol.HTTP);
        clientConfig.setConnectionTimeout(100000);
        AmazonS3 s3Client = new AmazonS3Client(awsCreds, clientConfig);

        final ListObjectsRequest req = new ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix("cleaned/");
        ObjectListing result = s3Client.listObjects(req);
        logger.info("There are: " + result.getObjectSummaries().size());
        ObjectListing objects = s3Client.listObjects(bucketName);
        List<S3ObjectSummary> keyList = new ArrayList<>();
//        keyList.addAll(objects.getObjectSummaries());

         do {
            objects = s3Client.listNextBatchOfObjects(objects);
            if (keys != null) {
                for (S3ObjectSummary obj : objects.getObjectSummaries()){
                    String key = obj.getKey().toLowerCase().replace("cleaned/", "");
                    if (keys.contains(key)){
                        keyList.add(obj);
                    }
                }
            } else {
                keyList.addAll(objects.getObjectSummaries());
            }
            logger.info("added " + keyList.size());
        } while (objects.isTruncated());

        for (S3ObjectSummary os : keyList){
            try {
                S3Object s3object = s3Client.getObject(new GetObjectRequest(
                        bucketName, os.getKey()));
                String cnt = new BufferedReader(new InputStreamReader(s3object.getObjectContent()))
                        .lines().collect(Collectors.joining("\n"));

                String normkey = os.getKey().toLowerCase().replace("cleaned/", "");
                logger.info("Added " + normkey);
                JsonObject json = new JsonObject();
                json.addProperty("key" , normkey);
                String content = cnt.replaceAll("\\s+" , " ").trim();
                json.addProperty("key" , os.getKey());
                json.addProperty("content" , content);
                Files.write(Paths.get("upsider_118k.matched.txt"),
                        (json.toString() + "\n").getBytes(), StandardOpenOption.APPEND);
 //               Files.write(Paths.get("upsider_118k.matched.txt"),
 //                       (cnt + "\n").getBytes(), StandardOpenOption.APPEND);
            } catch (Exception e){
                logger.error("Error: " + e);
            }
        }
        logger.info("Done with read");
    }

    public static void main(String[] args) throws Exception {
        int topics = 300;
        HashSet<String> keys = getKeys();
        getS3InputStream("upsider-scrape-data", keys);
/*        InputStream input = new FileInputStream("upsider_118k.txt");
        String output = "upsider_118k.w2v";
        trainWordVev(input, output, topics);
*/
    }
}
