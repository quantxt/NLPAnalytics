package com.quantxt.nlp.types;

import com.quantxt.nlp.word2vec;
import com.quantxt.types.MapSort;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Created by matin on 2/5/17.
 */


public class Tagger {
    final private static Logger logger = LoggerFactory.getLogger(Tagger.class);

    private INDArray columnMeans;
    private INDArray columnStds;
    private MultiLayerNetwork model;
    private word2vec w2v;
    private HashMap<String, Integer> dictionary;

    public Tagger(){

    }

    public static Tagger load(String dirname, String lang) {
        try {
            File mean_file = new File(dirname + "/" + "mean.ser");
            File std_file = new File(dirname + "/" + "std.ser");
            File model_file = new File(dirname + "/" + "model_dp.ser");
            File w2v_file = new File(dirname + "/" + "w2v.txt");
            File dict_file = new File(dirname + "/" + "dict.ser");
            FileInputStream mean_stream = mean_file.exists() ? new FileInputStream(mean_file) : null;
            FileInputStream std_stream = std_file.exists() ? new FileInputStream(std_file) : null;
            FileInputStream model_stream = model_file.exists() ? new FileInputStream(model_file) : null;
            FileInputStream w2v_stream = w2v_file.exists() ? new FileInputStream(w2v_file) : null;
            FileInputStream dic_stream = dict_file.exists() ? new FileInputStream(dict_file) : null;

            Tagger tagger = new Tagger(lang, mean_stream, std_stream, model_stream, w2v_stream, dic_stream);
            return tagger;

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return null;
    }

    public static Tagger load(String dirname) {
        try {
            File mean_file = new File(dirname + "/" + "mean.ser");
            File std_file = new File(dirname + "/" + "std.ser");
            File model_file = new File(dirname + "/" + "model_dp.ser");
            File w2v_file = new File(dirname + "/" + "w2v.txt");
            File dict_file = new File(dirname + "/" + "dict.ser");
            FileInputStream mean_stream = mean_file.exists() ? new FileInputStream(mean_file) : null;
            FileInputStream std_stream = std_file.exists() ? new FileInputStream(std_file) : null;
            FileInputStream model_stream = model_file.exists() ? new FileInputStream(model_file) : null;
            FileInputStream w2v_stream = w2v_file.exists() ? new FileInputStream(w2v_file) : null;
            FileInputStream dic_stream = dict_file.exists() ? new FileInputStream(dict_file) : null;

            Tagger tagger = new Tagger(null, mean_stream, std_stream, model_stream, w2v_stream, dic_stream);
            tagger.setW2v(w2v_file);
            return tagger;

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return null;
    }

    public void setW2v(word2vec w2v){
        this.w2v = w2v;
    }

    public word2vec getW2v() {
        return w2v;
    }


    private void setW2v(File w2vFile) throws FileNotFoundException, UnsupportedEncodingException {
        w2v = new word2vec(2);
        w2v.load(w2vFile);
    }


    public Tagger(String language,
                  InputStream means,
                  InputStream vars,
                  InputStream model_ann,
                  InputStream w2vstream,
                  File w2vFile,
                  InputStream dict) {

        w2v = new word2vec(2);
        try {
            w2v.load(w2vFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }

    public double[] getTextVec(String content) {
        String[] tokens = content.split("\\s+");

        List<double[]> vectors = new ArrayList<>();
        for (String w : tokens) {
            double[] vec = w2v.getWordVector(w);
            if (vec == null) continue;
            ;
            vectors.add(vec);
        }
        if (vectors.size() == 0) return null;
        int length = vectors.get(0).length;
        double[] prob = new double[length];
        for (double[] v : vectors) {
            for (int i = 0; i < length; i++) {
                prob[i] += v[i];
            }
        }
        return prob;
        //      return topic_model.getSentenceVector(content);
    }

    public Tagger(String language,
                  InputStream means,
                  InputStream vars,
                  InputStream model_ann,
                  InputStream w2v,
                  InputStream dict) {

        if (language == null) {
            language = "en";
        }

        logger.info("loading the model: " + language);
        try {

            logger.info("Mean loaded");
            columnMeans = means == null ? null :
                    (INDArray) (new ObjectInputStream(means)).readObject();

            logger.info("Var loaded");
            columnStds = vars == null ? null :
                    (INDArray) (new ObjectInputStream(vars)).readObject();

            dictionary = new HashMap<>();
            ArrayList<String> str2intArr = dict == null ? null :
                    (ArrayList<String>) (new ObjectInputStream(dict)).readObject();
            if (str2intArr != null) {
                for (int i = 0; i < str2intArr.size(); i++) {
                    dictionary.put(str2intArr.get(i), i);
                }
            }

            logger.info("Model loaded");
            model = model_ann == null ? null :
                    ModelSerializer.restoreMultiLayerNetwork(model_ann);

            logger.info("W2v loading");
            //    switch (language) {
                /*
                case "es" :
                    topic_model = new TopicModel(new ESDocumentInfo("", "")) ;
                    break;
                default:
                    topic_model = new TopicModel(new ENDocumentInfo("", "")) ;
                }
                */

            //            topic_model = new TopicModel();
            //        topic_model.loadInfererFromW2VFile(w2v);

            logger.info("Model  is loaded");

        } catch (Exception e) {
            logger.error("Model  failed to load. " + e.getMessage());
        }

    }

    public Map<String, Double> tag(String content) {
        double[] vec = getTextVec(content);
        int numfeatures = vec.length;
        INDArray features = Nd4j.create(1, numfeatures);
        for (int j = 0; j < numfeatures; j++) {
            features.putScalar(new int[]{0, j}, vec[j]);
        }

        features = features.subiRowVector(columnMeans);
        features = features.diviRowVector(columnStds);
        INDArray predicted = model.output(features);

        HashMap<String, Double> bestlabels = new HashMap<>();
        for (Map.Entry<String, Integer> e : dictionary.entrySet()) {
            int idx = e.getValue();
            double val = predicted.getDouble(idx);
            bestlabels.put(e.getKey(), val);
        }
        Map<String, Double> sorted_map = MapSort.sortdescByValue(bestlabels);
        return sorted_map;
    }

    public double getAverageDistance(String source, String target) {
        return w2v.getAverageDistance(source, target);
    }

    public Map<String, Double> getCloseWordRankMap(String w, int i) {
        return w2v.getCloseWordRankMap(w, i);
    }
}
