package com.quantxt.nlp.comp.mkin;

import com.quantxt.nlp.comp.mkin.aligner.Word2vecMatcher;

import com.quantxt.nlp.comp.meteor.scorer.MeteorConfiguration;
import com.quantxt.nlp.comp.mkin.scorer.MeteorScorer;
import com.quantxt.nlp.comp.meteor.scorer.MeteorStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Created by matin on 4/15/18.
 */

public class TestRunner {

    final private static Logger logger = LoggerFactory.getLogger(TestRunner.class);

    public static double scorePlaintext(MeteorScorer scorer,
                                        List<String> lines1, List<String> lines2) throws IOException {

        double sum = 0;
        for (int i = 0; i < lines1.size(); i++) {
            MeteorStats stats = scorer.getMeteorStats(lines1.get(i),
                    lines2.get(i));
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

    public static void main(String[] args) throws Exception
    {
        MeteorConfiguration config = new MeteorConfiguration();
        ArrayList<Integer> modules = new ArrayList<>();
        ArrayList<Double> modulesWeights = new ArrayList<>();
        config.setNormalization(0);
        modules.add(4);
        modulesWeights.add(1.0);
        config.setModules(modules);
        config.setModuleWeights(modulesWeights);
        File w2vFile = new File("w2v_en.ser");
        File w2vCache = new File("w2vcache.en.ser");
        try {
            if (w2vCache.exists()){
                logger.info("Loading cache");
                FileInputStream fic = new FileInputStream(w2vCache);
        //        new BufferedInputStream(new FileInputStream(file))
                ObjectInputStream oic = new ObjectInputStream(new BufferedInputStream(fic));
                HashMap word2vcache = (HashMap) oic.readObject();
                config.setWord2vCache(word2vcache);
                oic.close();
                fic.close();
                logger.info("w2v cache warmed " + word2vcache.size());
            } else {
                FileInputStream fis = new FileInputStream(w2vFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                HashMap word2vMap = (HashMap) ois.readObject();
                config.setWord2vMap(word2vMap);
                ois.close();
                fis.close();
                logger.info("w2v loaded");
                Map<String, Double> cache = Word2vecMatcher.warmUpCache(word2vMap);
                logger.info("Serilizing w2v cache");
                FileOutputStream fos = new FileOutputStream(w2vCache);
                ObjectOutputStream out = new ObjectOutputStream(fos);
                out.writeObject(cache);
                out.close();
            }
        } catch (Exception e) {
            logger.error("W2v failed : " + e.getMessage());
        }

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
        l2.add("Apple’s four software platforms — iOS, macOS, watchOS and tvOS — provide seamless experiences across all Apple devices and empower people with breakthrough services including the App Store, Apple Music, Apple Pay and iCloud.");
        l2.add("“Websites and apps tell us they see twice as many people actually completing a purchase with Apple Pay than with other payment methods.");
        l2.add("Ever since Apple announced ARKit at its annual developers conference earlier this summer, the app-making community has enthusiastically shown off what it has been able to make with the new framework for augmented reality apps.");
        l2.add("In addition to getting better data more quickly to Apple Maps with drones, the company is also trying to improve its mapping service’s navigation and is eyeing ways to take images of the inside of buildings, according to Bloomberg.");

        for (int i=0; i < l1.size(); i++){
  //          logger.info(String.valueOf(i));
            for (int j=i+1; j < l1.size(); j++){
                MeteorStats stats = scorer.getMeteorStats(l1.get(j),
                        l1.get(i));
            }
        }

        long took = System.currentTimeMillis() - now;
        logger.info("Took: " + took);
        logger.info("Took: " + Word2vecMatcher.took);
        logger.info("Took: " + Word2vecMatcher.took1);
        logger.info("Took: " + Word2vecMatcher.took2);
    }
}
