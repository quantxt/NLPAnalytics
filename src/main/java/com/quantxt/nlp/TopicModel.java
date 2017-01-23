package com.quantxt.nlp;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.topics.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.log4j.Logger;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Created by matin on 7/8/16.
 */
public class TopicModel {

    final private static Logger logger = Logger.getLogger(TopicModel.class);

    final private static int RANDOM_SEED = 5;
    final private int numTopics;
    final private int numIterations;
    final private String modelName;
    private Pipe pipe;
    private HashMap<String, LDATopic> word2TopicW = new HashMap<>();
    private double[] topicW;
    private TokenizerFactory tokenFactor;

    public TopicModel(int n, int iter, String m){
        numTopics = n;
        numIterations = iter;
        modelName = numTopics + "_" + m;
        topicW = new double[numTopics];
        tokenFactor = new LinePreProcess();
        tokenFactor.setTokenPreProcessor(new TextPreProcessor());
        pipe = getPipe();
    }

    public Pipe getPipe(){
        ArrayList<Pipe> pipeList = new ArrayList<>();

 //       ClassLoader classLoader = getClass().getClassLoader();
 //       File stopWordFile = new File(classLoader.getResource("en.stop.txt").getFile());

        // Pipes: lowercase, tokenize, remove stopwords, map to features
        pipeList.add( new CharSequenceLowercase() );
        pipeList.add(new LinePreProcess() );
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("[\\p{L}\\p{N}_]+")));
        //      pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File("models/stoplist.txt"), "UTF-8", false, false, false) );
        pipeList.add( new TokenSequence2FeatureSequence() );

        return new SerialPipes(pipeList);
    }

    private ParallelTopicModel getModel(final InstanceList instantList) throws IOException
    {
        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);
        model.setRandomSeed(RANDOM_SEED);
        model.addInstances(instantList);
        model.setNumThreads(6);
        model.setNumIterations(numIterations);
        model.setOptimizeInterval(10);
        model.estimate();
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(modelName + ".ser"));
        out.writeObject(model);
        out.close();
        return model;
    }

    public void train(String inFile,
                      String text_field,
                      int maxLine) throws IOException {

        InstanceList instances = new InstanceList (this.getPipe());

        BufferedReader br = new BufferedReader(new FileReader(inFile));
        try {
            String line;
            int numline = maxLine;
            while ((line = br.readLine()) != null) {
                JsonElement elem = new JsonParser().parse(line);
                if (numline-- <0) break;
                if (elem.isJsonArray()) {
                    for (JsonElement je : elem.getAsJsonArray()) {
                        String data = je.getAsJsonObject().get(text_field).getAsString();
                        Instance ins = new Instance(data, "en", "training", "file");
                        instances.addThruPipe(ins);
                    }
                } else {
                    String data = elem.getAsJsonObject().get(text_field).getAsString();
                    Instance ins = new Instance(data, "en", "training", "file");
                    instances.addThruPipe(ins);
                }
            }

        } finally {
            br.close();
        }
        getModel(instances);
    }

    public ParallelTopicModel loadModel() throws IOException, ClassNotFoundException {
        File serializedFile = new File(modelName+".ser");
        if (serializedFile.exists()) {
            logger.info("Reading model from disk");
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelName + ".ser"));
            ParallelTopicModel model = (ParallelTopicModel) ois.readObject();
            ois.close();
            return model;
        } else {
            logger.error("Model file is missing");
        }
        return null;
    }

    public InstanceList getInstanceFromFile(String file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        InstanceList instantList = new InstanceList(pipe);
        InputStream sentenceModellIn = new FileInputStream("models/en-sent.bin");
        SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);

        while ((line = br.readLine()) != null) {
            for (String l : sentenceDetector.sentDetect(line)){
                Files.write(Paths.get("sent.txt"), (l +"\n").getBytes(), StandardOpenOption.APPEND);
                Instance ins = new Instance(l, "no", "events", "events");
                instantList.addThruPipe(ins);

            }
        }
        return instantList;
    }

    public double [] getAvgVector(){

        double sum = 0;
        for (double d : topicW){
            sum +=d;
        }

        double [] probs = new double[numTopics];
        for (int i =0; i <numTopics; i++){
            probs[i] = topicW[i] / sum;
        }
        return probs;
    }

    public List<String> getTokens(String str){
        Tokenizer s = tokenFactor.create(str);
        return s.getTokens();
    }

    public double [] getSentenceVector(String line){

        Tokenizer s = tokenFactor.create(line);
        List<String> tokens = s.getTokens();

        double [] probs = new double[numTopics];
        for (String w : tokens){
            LDATopic ldatopic = getWLDATopic(w);
            if (ldatopic == null) {
                logger.debug("oov: " + w);
                continue;
            }
            for (int i =0; i <numTopics; i++){
                double d = ldatopic.getWeights()[i];
//                if (d < .01) continue;
                probs[i] += d;
            }
        }

        return probs;
    }

    public void loadInfererFromFile(String file) throws IOException, ClassNotFoundException {
//        String modelName = "MostReadNews_"+numTopics;
        ParallelTopicModel model = loadModel();
        if (model == null){
            InstanceList instantList = getInstanceFromFile(file);
            model = getModel(instantList);
        }
        model.printTopWords(new File(modelName + ".topwords"), 20, false);
        populateWord2ProbMap(model);
    }

    public double [] getProbs(final String data){
        InstanceList instances = new InstanceList (this.getPipe());
        Instance ins = new Instance(data, "en", "training", "file");
        instances.addThruPipe(ins);
        return getProbs(instances.get(0));
    }

    public double [] getProbs(final Instance ins){
        double [] probs = new double[numTopics];
        FeatureSequence tokens = (FeatureSequence) ins.getData();
        double oov = 0;
        double sum = 0;
        for (int position = 0; position < tokens.size(); position++) {
            int code= tokens.getIndexAtPosition(position);
            String w = tokens.getAlphabet().lookupObject(code).toString();
            LDATopic ldaTopic = word2TopicW.get(w);
            if (ldaTopic == null){
//                logger.info(w + " is oov");
                oov += 1;
                continue;
            }
            for (int i=0; i < numTopics; i++){
                double score = ldaTopic.getProb(i) / topicW[i];
                probs[i] +=  score;// * topicW[i];
                sum += score;
            }
        }
//        double ivPercentage = 1 - (oov / tokens.size());
        double ivPercentage = 1;
        if (sum != 0) {
            for (int i = 0; i < numTopics; i++) {
                if (Math.abs(probs[i]) < .005 ||  Double.isNaN(probs[i])) {
                    probs[i] = 0;
                } else {
                    probs[i] = probs[i] * ivPercentage / sum;
                }
            }
        }
        return probs;
    }

    public double getTopicWeight(int t){
        return topicW[t];

    }

    public double cosineSimilarity(String s1, String s2) {
        double [] p1 = getSentenceVector(s1);
        double [] p2 = getSentenceVector(s2);
        return cosineSimilarity(p1, p2);
    }

    public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }


    public void loadInfererFromW2VFile(String file) throws IOException, ClassNotFoundException {
//        String modelName = "MostReadNews_"+numTopics;

        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                String [] parts = line.split("\\s+");
                String w = parts[0];
                LDATopic ldaTopic = word2TopicW.get(w);
                if (ldaTopic == null){
                    ldaTopic = new LDATopic(numTopics);
                    word2TopicW.put(w, ldaTopic);
                }
                for (int topic = 0; topic < numTopics; topic++) {
                    double weight = Double.parseDouble(parts[topic+1]);
//                    if (weight > 0) {
                        ldaTopic.add(topic, weight);
                        topicW[topic] += weight;
//                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void populateWord2ProbMap(ParallelTopicModel model) throws IOException {
        logger.info("Populating topic-weight map for: " + modelName + " " + numTopics + " topics");
        for (int topic = 0; topic < numTopics; topic++) {
//            if (!validtopics.contains(topic)){
//                continue;
//            }
            for (int type = 0; type < model.numTypes; type++) {
                int[] topicCounts = model.typeTopicCounts[type];

                double weight = 0;
                int index = 0;
                while (index < topicCounts.length &&
                        topicCounts[index] > 0) {
                    int currentTopic = topicCounts[index] & model.topicMask;
                    if (currentTopic == topic) {
                        weight += topicCounts[index] >> model.topicBits;
                        break;
                    }
                    index++;
                }
                if (weight == 0){
                    continue;
                }

                String w = model.alphabet.lookupObject(type).toString();
                LDATopic ldaTopic = word2TopicW.get(w);
                if (ldaTopic == null){
                    ldaTopic = new LDATopic(numTopics);
                    word2TopicW.put(w, ldaTopic);
                }

                ldaTopic.add(topic, weight);
                topicW[topic] += weight;
            }
        }
    }

    public LDATopic getWLDATopic(String w){
        return word2TopicW.get(w);
    }

    private double cmpSentence(String s1, String s2){
        double [] p1 = getSentenceVector(s1);
        double [] p2 = getSentenceVector(s2);
        double simp = cosineSimilarity(p1, p2);
        return simp;
    }

    public static void main(String[] args) throws Exception {

        int numTopics = 50;
        TopicModel tm = new TopicModel(numTopics, 500, "cb_official");
        tm.loadInfererFromW2VFile("/Users/matin/git/quantxt/NLPAnalytics/crunchbase_50k_50.w2v");
        HashMap<String, double[]> all = new HashMap<>();

        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/Downloads/all_clean.csv"));
        try {

            String line;

            while ((line =br.readLine()) != null) {
                String [] parts = line.split(",");
                String p = "";
                List<String> partPro = new ArrayList<>();
                for (String part : parts){
                    if (part.endsWith("\"")){
                        p += "," + part;
                        partPro.add(p);
                        p = "";
                        continue;
                    }
                    if (! part.startsWith("\"")) {
                        if (p.isEmpty()) {
                            partPro.add(part);
                        } else {
                            p += "," + part;
                        }
                    } else {
                        p = part;
                    }
                }

                if (partPro.size() < 5) continue;
                String content = partPro.get(4).replaceAll("^\"|\"$", "");
                double [] vec1 = tm.getSentenceVector(content);
                all.put(partPro.get(0).replaceAll("^\"|\"$", ""), vec1);
            }
        } finally {
            br.close();
        }

        for (Map.Entry<String, double[]> e : all.entrySet()){
            String cmp1 = e.getKey();
            double [] vec1 = e.getValue();
            HashMap<String, Double> sims = new HashMap<>();
            for (Map.Entry<String, double[]> ee : all.entrySet()){
                String cmp2 = ee.getKey();
                if (cmp1.equals(cmp2))continue;
                double [] vec2 = ee.getValue();
                double sim = TopicModel.cosineSimilarity(vec1, vec2);
                if (!Double.isFinite(sim))continue;
                sims.put(cmp2, sim);
  //              all.put(cmp1 + "_" + cmp2, sim);
            }

            ValueComparator bvc = new ValueComparator(sims);
            TreeMap<String, Double> sorted_map = new TreeMap<>(bvc);
            sorted_map.putAll(sims);

            int top = 4;
            StringBuilder sb = new StringBuilder();
            sb.append(cmp1+ ": ");
            for (Map.Entry<String, Double> s1 : sorted_map.entrySet()){
                if (top-- < 0) break;
                sb.append("(").append(s1.getKey()).append(":").append(s1.getValue()).append(") ");
            }
            int bot = 4;
            for (Map.Entry<String, Double> s2 : sorted_map.descendingMap().entrySet()){
                if (bot-- < 0) break;
                sb.append("(").append(s2.getKey()).append(":").append(s2.getValue()).append(")");
            }

            logger.info(sb.toString());
        }



//        TopicModel tp = new TopicModel(200, 500, "yelp");
  //      tp.loadModel();
   //     tp.train("/Users/matin/git/quantxt/QTReviewxtModel/yelp_academic_dataset_review.json", "text", 50000);
    }
}

