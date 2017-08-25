package com.quantxt.nlp;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.topics.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.quantxt.doc.QTDocument;
import com.quantxt.trie.Trie;
import com.quantxt.types.MapSort;
import com.quantxt.types.StringDouble;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.datavec.api.util.ClassPathResource;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Created by matin on 7/8/16.
 */
public class TopicModel {

    final private static Logger logger = LoggerFactory.getLogger(TopicModel.class);

    //training//////
    final private static int RANDOM_SEED = 5;
    private int numTopics;
    private int numIterations;
    private String modelName;
    ///////////////

    private Pipe pipe;
    private HashMap<String, LDATopic> word2TopicW = new HashMap<>();
    private double[] topicW;
    private Map<String, double[]> TOPIC_MAP = new HashMap<>();

    public TopicModel(QTDocument doc){
//        tokenFactor = new LinePreProcess(doc);
//        tokenFactor.setTokenPreProcessor(new TextPreProcessor());
        pipe = getPipe(doc.getLanguage());
    }


    public TopicModel(int n, int iter, String m){
        numTopics = n;
        numIterations = iter;
        modelName = numTopics + "_" + m;
        topicW = new double[numTopics];
//        tokenFactor = new LinePreProcess();
//        tokenFactor.setTokenPreProcessor(new TextPreProcessor());
        pipe = getPipe();
    }

    public Pipe getPipe(){
        return getPipe(QTDocument.Language.ENGLISH);
    }

    public Pipe getPipe(QTDocument.Language lang){
        ArrayList<Pipe> pipeList = new ArrayList<>();

 //       ClassLoader classLoader = getClass().getClassLoader();
 //       File stopWordFile = new File(classLoader.getResource("en.stop.txt").getFile());

        // Pipes: lowercase, tokenize, remove stopwords, map to features
        pipeList.add( new CharSequenceLowercase() );
        pipeList.add(new LinePreProcess() );
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("[\\p{L}\\p{N}_]+")));
        //      pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );

        String langDir = "en";
        switch (lang){
            case SPANISH:
                langDir = "es";
                break;
        }
        try {
            pipeList.add( new TokenSequenceRemoveStopwords(new ClassPathResource(langDir + "/stoplist.txt").getFile(), "UTF-8", false, false, false) );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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

    public StringDouble getBestTag(String str) {
        double[] tvec = getSentenceVector(str);
        Map<String, Double> vals = new HashMap<>();
        double avg = 0;
        double numTopics = TOPIC_MAP.size();
        for (Map.Entry<String, double[]> r : TOPIC_MAP.entrySet()) {
            Double sim = TopicModel.cosineSimilarity(tvec, r.getValue());
            if (sim.isNaN()) {
                sim = 0d;
            }
            avg += sim / numTopics;
            vals.put(r.getKey(), sim);

        }
        //      if (avg < .1) return null;

        Map<String, Double> sorted_map = MapSort.sortByValue(vals);
        Map.Entry<String, Double> firstEntry = sorted_map.entrySet().iterator().next();
        double max = firstEntry.getValue();
        if (max < .1 || max / avg < 1.3){
            logger.warn("All tags are likely!.. model is not sharp enough");
        }
        return new StringDouble(firstEntry.getKey(), firstEntry.getValue());
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

    /*
    public List<String> getTokens(String str){
        Tokenizer s = tokenFactor.create(str);
        return s.getTokens();
    }
    */

    public double [] getSentenceVector(String line){

 //       Tokenizer s = tokenFactor.create(line);
 //       List<String> tokens = s.getTokens();

        String[] tokens = line.split("\\s+");

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

    /*
    public double [] getProbs(final String data){
        InstanceList instances = new InstanceList (this.getPipe());
        Instance ins = new Instance(data, "en", "training", "file");
        instances.addThruPipe(ins);
        return getProbs(instances.get(0));
    }
    */

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

    private void loadInfererFromW2VFile(BufferedReader br){
        try {
            //find number of topics/diemsions
            String line = br.readLine();
            String [] parts = line.split("\\s+");
            numTopics = parts.length - 1;
            topicW = new double[numTopics];
            do  {
                parts = line.split("\\s+");
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
            } while ((line = br.readLine()) != null);
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

    public void loadInfererFromW2VFile(InputStream is) throws IOException, ClassNotFoundException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        loadInfererFromW2VFile(br);
    }

    public void loadInfererFromW2VFile(String file) throws IOException, ClassNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        loadInfererFromW2VFile(br);

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

    public Trie getPhs(String phraseFileName) throws IOException {
        Trie.TrieBuilder phrase = Trie.builder().onlyWholeWords().ignoreCase().ignoreOverlaps();
        String line;

        int num = 0;
        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/Downloads/enwiki-20161201-all-titles-in-ns0"));

        int num2 = 0;
        try {
            while ((line = br.readLine()) != null) {
                if ((num++ % 100000) == 0) {
                    logger.info(num + " " + " " + num2 + " loaded");
                }
                //      if (num > 10500000) break;
                if (!line.matches("^([A-Z]).*")) continue;
                if (line.length() > 50) continue;

                StringDouble bestTag = getBestTag(line);

                if (bestTag == null || bestTag.getVal() < .5) continue;
                Files.write(Paths.get(phraseFileName), (line + "\n").getBytes(), StandardOpenOption.APPEND);
                line = line.replaceAll("[_\\-]+", " ");
                line = line.replaceAll("[^A-Za-z\\s]+", "").trim();
                String[] parts = line.split("\\s+");
                if (parts.length > 4) continue;
                //             logger.info(bb + " --> " + line + " --> " + tag);
                num2++;
                phrase.addKeyword(line);
            }
        } catch (IOException e) {
            logger.info("Error: " + e);
        }

        logger.info("Phrases loaded");
        return phrase.build();
    }

    public  Map<QTDocument, ArrayList<QTDocument>> getPCRels(final List<QTDocument> data,
                                                             final double thresh){
        logger.info("starting..");
        List<double[]> word2vecs = new ArrayList<>();
        for (int i = 0; i < data.size() - 1; i++) {
            QTDocument p = data.get(i);
            word2vecs.add(getSentenceVector(p.getTitle()));
        }

        logger.info("Computed w2v for " + data.size() + " instances");
        ListIterator<QTDocument> iterP1 = data.listIterator(0);
        ListIterator<double []> iterW2v1 = word2vecs.listIterator(0);

        Map<QTDocument, ArrayList<QTDocument>> parnet_child = new HashMap<>();
        while(iterP1.hasNext()){
            QTDocument p1 = iterP1.next();
            if (parnet_child.containsKey(p1)){
                logger.info(p1.getId());
            }
            parnet_child.put(p1, new ArrayList<>());
        }

        logger.info("parnet_child has: " + parnet_child.size());

        iterP1 = data.listIterator(0);
        while(iterP1.hasNext() && iterW2v1.hasNext()){
            QTDocument p1 = iterP1.next();
            double [] w2v1 = iterW2v1.next();
            DateTime d1 = p1.getDate();

            ListIterator<QTDocument> iterP2 = data.listIterator(iterP1.nextIndex());
            ListIterator<double []> iterW2v2 = word2vecs.listIterator(iterW2v1.nextIndex());
            boolean found = false;
            while(iterP2.hasNext() && iterW2v2.hasNext() && !found){
                QTDocument p2 = iterP2.next();
                DateTime d2 = p2.getDate();
                long diff = (d1.getMillis() - d2.getMillis() ) / 1000 / 60 / 60 / 24; //day
                if (diff > 5) break;
                double [] w2v2 = iterW2v2.next();
                double sim = TopicModel.cosineSimilarity(w2v1, w2v2);
                //       if (sim > .98) continue;
                if (sim > thresh){
                    found = true;
                    ArrayList<QTDocument> p2_childs = parnet_child.get(p2);
                    p2_childs.add(p1);
                    // check if p1 is a parent already
                    ArrayList<QTDocument> p1_childs = parnet_child.get(p1);
                    p2_childs.addAll(p1_childs);
                }
            }
            if (found){
                parnet_child.remove(p1);
            }
        }
        return parnet_child;
    }

    public ArrayList<QTDocument> removeDups(final List<QTDocument> docs, double thresh) throws ParseException {
        ArrayList<QTDocument> uniques = new ArrayList<>();
//        logger.info("POSTS HAS: " + POSTS.size());
//        ArrayList<WPpost> posts = new ArrayList<>();
/*        for (int i=0; i< docs.size(); i++){
            QTDocument doc = docs.get(i);
            WPpost p = new WPpost(doc.getTitle(), doc.getBody(), doc.getDate());
            p.setId(i);
            posts.add(p);
        }
*/      Map<QTDocument, ArrayList<QTDocument>> rels = getPCRels(docs, thresh);
        HashSet<String> dupids = new HashSet<>();
        for (Map.Entry<QTDocument, ArrayList<QTDocument>> e : rels.entrySet()) {
            if (e.getValue().size() > 0) {
                for (QTDocument p: e.getValue()){
                    dupids.add(p.getId());
                }
            }
        }
        for (int i=0; i< docs.size(); i++){
            if (dupids.contains(i)) continue;
            uniques.add(docs.get(i));
        }

        return uniques;
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
}

