package com.quantxt.nlp.topic;

import com.quantxt.types.MapSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by matin on 4/21/2016.
 */
public class LDATopic {

    final private static Logger logger = LoggerFactory.getLogger(LDATopic.class);

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

        Map<String, Double> sorted_map = MapSort.sortByValue(weightMap);
        Map.Entry<String, Double> firstEntry = sorted_map.entrySet().iterator().next();

        StringBuilder sb = new StringBuilder();

        double max = firstEntry.getValue();
        for (Map.Entry<String, Double> e : sorted_map.entrySet()){
            double v = e.getValue();
            if (v / max < .25) break;
            sb.append(e.getKey()).append(" ");
        }
        return sb.toString().trim();
    }
}

