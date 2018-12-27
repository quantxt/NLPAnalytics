package com.quantxt.nlp.comp.meteor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.quantxt.nlp.comp.meteor.scorer.MeteorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.nlp.comp.meteor.scorer.MeteorScorer;
import com.quantxt.nlp.comp.meteor.scorer.MeteorStats;

/**
 * Created by matin on 8/30/17.
 */

public class MeteorComp {

    final private static Logger logger = LoggerFactory
            .getLogger(MeteorComp.class);

    public static double scorePlaintext(MeteorScorer scorer,
            List<String> lines1, List<String> lines2) throws IOException {

    //    MeteorStats aggStats = new MeteorStats();
        double sum = 0;
        for (int i = 0; i < lines1.size(); i++) {
            MeteorStats stats = scorer.getMeteorStats(lines1.get(i), lines2.get(i));
            sum += stats.score;
    //        logger.info("acc: " + stats.score);
    //        logger.info("P:" + stats.precision);
    //        logger.info("R:" + stats.recall);
    //        logger.info("F1:" + stats.f1);
    //        logger.info("FP:" + stats.fragPenalty);
  //          aggStats.addStats(stats);
        }
        return sum;
    }

    public static void main(String[] args) throws Exception {

        MeteorConfiguration config = new MeteorConfiguration();
   //     ArrayList<Integer> modules = new ArrayList<>();
   //     ArrayList<Double> modulesWeights = new ArrayList<>();
   //     config.setNormalization(1);
   //     modules.add(2);
   //     modules.add(0);
    //    modules.add(1);
    //    modules.add(3);
    //    modulesWeights.add(1.0);
    //    config.setModules(modules);
    //    config.setModuleWeights(modulesWeights);
        MeteorScorer scorer = new MeteorScorer(config);


        File file = new File("test_data.txt");

        BufferedReader br = new BufferedReader(new FileReader(file));

        String st;
        ArrayList<String> l1 = new ArrayList<>();
        int topn = 1500;
        while ((st = br.readLine()) != null){
            if (topn-- <0) break;
            l1.add(st);
        }

        long now = System.currentTimeMillis();

        ArrayList<String> l2 = new ArrayList<>();
        l2.add("Stock market rises broadly as attention turns to corporate results");
        l2.add("A Philippine domestic worker has been hospitalised in Saudi Arabia after her employer allegedly forced her to drink household bleach, Manila's foreign ministry said Monday.");
        l2.add("Amidst the continued fervor for cryptocurrencies and blockchain technology, evangelists have claimed it can help replace everything from money itself, to the foundation of many of our digital tools.");

        for (int i=0; i < l1.size(); i++){
            logger.info(String.valueOf(i));
            for (int j=i+1; j < l1.size(); j++){
                MeteorStats stats = scorer.getMeteorStats(l1.get(j),
                        l1.get(i));
            }
        }

        /*    for (int i=0; i < l2.size(); i++){
            //    logger.info(String.valueOf(i));
            Map<Integer, Double> scores = new HashMap<>();
            for (int j=0; j < l1.size(); j++){
                MeteorStats stats = scorer.getMeteorStats(l1.get(j),
                        l2.get(i));
                scores.put(j, stats.score);
            }
            Map<Integer, Double> sorted = MapSort.sortdescByValue(scores);
            int topN = 5;
            for (Map.Entry<Integer, Double> e : sorted.entrySet()){
                if (topN-- <0) break;
                logger.info(l1.get(e.getKey()) + " : " + e.getValue());
            }
            logger.info("\n\n");
        }
    */
        long took = System.currentTimeMillis() - now;
        logger.info("Took: " + took);
    }
}
