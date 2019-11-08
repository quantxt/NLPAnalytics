package com.quantxt.nlp.comp.mkin.aligner;

import com.quantxt.nlp.topic.TopicModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by matin on 4/15/18.
 */

public class Word2vecMatcher {

    final private static Logger logger = LoggerFactory.getLogger(Word2vecMatcher.class);
    private static HashMap<String, double[]> word2vMap;

    private static HashMap<String, Double> w2vCache;

    final private static int MaxMatchPertoken = 3;
    final private static double w2vThresh = .5;

    public static long took = 0;
    public static long took1 = 0;
    public static long took2 = 0;

    public static void match(int stage,
                             Alignment a,
                             Stage s) {

        // Get keys for word stems
        long start = System.currentTimeMillis();

        took += (System.currentTimeMillis() - start);
        ArrayList<List<IdxVal>> allwordmatrix = new ArrayList<>();

        for (int i = 0; i < a.words1.length; i++) {
            String w1 = a.words1[i];
            List<IdxVal> vals = new ArrayList<>();
            for (int j = 0; j < a.words2.length; j++) {
                // Match for DIFFERENT words with SAME stems
                Double w = null;
                String w2 = a.words2[j];
                if (w1.equals(w2)){
                    w = 1d;
                } else {
                    if (w2vCache != null) {
                        String catchKey = getKey(w1, w2);
                        w = w2vCache.get(catchKey);
                    } else {
                        double[] vec1 = word2vMap.get(w1);
                        double[] vec2 = word2vMap.get(w2);
                        if (vec1 != null && vec2 != null) {
                            w = TopicModel.cosineSimilarity(vec1, vec2);
                        }
                    }
                }
                if (w == null || w < w2vThresh) continue;
                vals.add(new IdxVal(j, w));
            }

            if (vals.size() > 1) {
                Collections.sort(vals, (c1, c2) -> Double.compare(c2.val, c1.val));
            }
            allwordmatrix.add(vals);
        }

        took1 += (System.currentTimeMillis() - start);

        for (int i = 0; i < a.words1.length; i++) {
            List<IdxVal> data = allwordmatrix.get(i);
  //          if (data.size() >= MaxMatchPertoken){
  //              Collections.sort(data, (c1, c2) -> Double.compare(c2.val, c1.val));
  //              data = data.subList(0, MaxMatchPertoken);
  //          }
            int topS = MaxMatchPertoken;
            for (IdxVal iv : data){
                if (topS-- <0) break;
                int j = iv.idx;
                double w = iv.val;
                Match m = new Match(j, 1, i, 1, w, stage);
                // Add this match to the list of matches and mark coverage
                s.matches[j].add(m);
                s.line1Coverage[i]++;
                s.line2Coverage[j]++;
            }
        }

        took2 += (System.currentTimeMillis() - start);
    }

    private static String getKey(String str1 , String str2){
        if (str1.compareTo(str2) > 0) return (str1+ "_" + str2);
        return (str2+ "_" + str1);
    }

    public static void setW2vCache(HashMap<String, Double> map) {
        w2vCache = map;
    }

    public static void setW2v(HashMap<String, double[]> map) {
        word2vMap = map;
    }

    public static HashMap<String, Double> warmUpCache(Map<String, double[]> map) throws InterruptedException {
        if (map == null) return null;

        ConcurrentHashMap tmpCache = new ConcurrentHashMap<>();
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<double[]> vecs = new ArrayList<>();
        for (Map.Entry<String, double[]> e1 : map.entrySet()){
            String key = e1.getKey();
            keys.add(key);
            vecs.add(e1.getValue());
        }

        AtomicInteger latch = new AtomicInteger(0);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(15000);
        for (int i =0; i < keys.size(); i++) {
          //     if (i > 15) break;
            double[] vec1 = vecs.get(i);
            String key1 = keys.get(i);
            final int statIdx = i;
            latch.incrementAndGet();
            Thread t = new Thread() {
                @Override
                public void run() {
                    for (int j = statIdx + 1; j < keys.size(); j++) {
                        double[] vec2 = vecs.get(j);
                        String key2 = keys.get(j);
                        Double w = TopicModel.cosineSimilarity(vec1, vec2);
                        if (Double.isFinite(w) && w >= w2vThresh) {
                            String key = getKey(key1, key2);
                            tmpCache.put(key, w);
                        }
                    }
                    latch.decrementAndGet();
                }
            };
            executor.execute(t);
        }
        executor.shutdown();

        while (latch.get() > 0){
            logger.info("Running " + latch.get());
            Thread.sleep(2000);
        }

        w2vCache = new HashMap<>(tmpCache);
        logger.info("Size of w2v catch: " + w2vCache.size());
        return w2vCache;
    }

    private static class IdxVal{
        public int idx;
        public double val;

        public IdxVal(int i, double v){
            idx = i;
            val = v;
        }

    }
    public static void main(String[] args) throws Exception {

    }
}
