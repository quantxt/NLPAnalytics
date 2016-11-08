package com.quantxt.nlp;

import org.apache.log4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by u6014526 on 4/21/2016.
 */
public class LDATopic {

    final private static Logger logger = Logger.getLogger(LDATopic.class);

    private int numTopics;
    private double [] weights;
    private double totalWight = 0;

    public LDATopic(int n){
        numTopics = n;
        weights = new double[numTopics];
    }

    public void add(int topicN, double w){
        weights[topicN] +=w;
        totalWight +=w;
    }

    public double getProb(int N){
        return weights[N] / totalWight;
//        return weights[N];
    }

    public double [] getWeights(){
        return weights;
    }

    public String getBestTopic(){
        HashMap<String, Double> weightMap = new HashMap<>();
        for (int i=0; i<weights.length; i++){
            double weight = weights[i];
            if (weight == 0) continue;
            weightMap.put(String.valueOf(i), weight);
        }

        ValueComparator bvc = new ValueComparator(weightMap);
        TreeMap<String, Double> sorted_map = new TreeMap<>(bvc);
        sorted_map.putAll(weightMap);
        StringBuilder sb = new StringBuilder();

        double max = sorted_map.firstEntry().getValue();
        for (Map.Entry<String, Double> e : sorted_map.entrySet()){
            double v = e.getValue();
            if (v / max < .25) break;
            sb.append(e.getKey()).append(" ");
        }
        return sb.toString().trim();
    }
}

class ValueComparator implements Comparator<String> {
    Map<String, Double> base;

    public ValueComparator(Map<String, Double> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with
    // equals.
    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}

