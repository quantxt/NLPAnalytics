package com.quantxt.nlp.types;

import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.QTDocument.ESDocumentInfo;
import com.quantxt.nlp.TopicModel;
import com.quantxt.types.MapSort;
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

    private static Properties prop = null;

    private INDArray columnMeans;
    private INDArray columnStds;
    private MultiLayerNetwork model;
    private TopicModel topic_model;
    private HashMap<String, Integer> dictionary;


    public static Tagger load(String dirname)
    {
        try {
            File mean_file  = new File(dirname + "/" + "mean.ser");
            File std_file   = new File(dirname + "/" + "std.ser");
            File model_file = new File(dirname + "/" + "model_dp.ser");
            File w2v_file   = new File(dirname + "/" + "w2v.txt");
            File dict_file  = new File(dirname + "/" + "dict.ser");
            FileInputStream mean_stream  = mean_file.exists() ? new FileInputStream(mean_file): null;
            FileInputStream std_stream   = std_file.exists() ? new FileInputStream(std_file): null;
            FileInputStream model_stream = model_file.exists() ? new FileInputStream(model_file): null;
            FileInputStream w2v_stream   = w2v_file.exists() ? new FileInputStream(w2v_file): null;
            FileInputStream dic_stream   = dict_file.exists() ? new FileInputStream(dict_file): null;

            Tagger tagger = new Tagger(dirname, mean_stream, std_stream, model_stream, w2v_stream, dic_stream);
            return tagger;

        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        return null;
    }


    public Tagger(INDArray cM,
                  INDArray cS,
                  MultiLayerNetwork mn,
                  TopicModel tm,
                  HashMap<String, Integer> dict) {
        columnMeans = cM;
        columnStds = cS;
        model = mn;
        topic_model = tm;
        dictionary = dict;
    }

    public double[] getTextVec(String content) {
        return topic_model.getSentenceVector(content);
    }

    public Tagger(String language,
                    InputStream means,
                    InputStream vars,
                    InputStream model_ann,
                    InputStream w2v,
                    InputStream dict)
    {
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
            switch (language) {
                case "es" :  topic_model = new TopicModel(new ESDocumentInfo("", "")) ;
                    break;
                default:
                    topic_model = new TopicModel(new ENDocumentInfo("", "")) ;
                }
            topic_model.loadInfererFromW2VFile(w2v);

            logger.info("Model " + language + " is loaded");

        } catch (Exception e) {
            logger.error("Model " + language + " failed to load. " + e.getMessage());
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

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner( new File("test") );
        String res = scanner.useDelimiter("\\A").next();
        scanner.close();
        res = res.replaceAll("^[\\S\\s]+Total instances" , "Total instances");
        logger.info("Res is: " + res);
    }
}
