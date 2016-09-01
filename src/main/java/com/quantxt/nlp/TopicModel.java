package com.quantxt.nlp;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.topics.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * Created by matin on 7/8/16.
 */
public class TopicModel {


    final private int numTopics;
    public TopicModel(int n){
        numTopics = n;

    }

    private SerialPipes getPipe(){
        ArrayList<Pipe> pipeList = new ArrayList<>();

        // Pipes: lowercase, tokenize, remove stopwords, map to features
        pipeList.add( new CharSequenceLowercase() );
        pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequenceRemoveStopwords(new File("models/en.stop.txt"), "UTF-8", false, false, false) );
        pipeList.add( new TokenSequence2FeatureSequence() );

        return new SerialPipes(pipeList);
    }

    public void train(String inFile) throws IOException {

        InstanceList instances = new InstanceList (this.getPipe());

        BufferedReader br = new BufferedReader(new FileReader(inFile));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                JsonArray arr = new JsonParser().parse(line).getAsJsonArray();
                for (JsonElement je : arr) {
                    String data = je.getAsJsonObject().get("post_content").getAsString();
                    Instance ins = new Instance(data, "en", "training", "file");
                    instances.addThruPipe(ins);
                }
            }

        } finally {
            br.close();
        }

        //  the second is the parameter for a single dimension of the Dirichlet prior.
        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);
        model.addInstances(instances);
        model.setBurninPeriod(10);
        model.setNumThreads(4);
        model.setNumIterations(500);
        model.estimate();
        model.printTopWords(new File("model.mallet"), 50, false);
    }

    public static void main(String[] args) throws Exception {

        TopicModel tp = new TopicModel(15);
        tp.train("dump.json");

    }
}
