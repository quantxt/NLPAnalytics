package com.quantxt.nlp;


import org.apache.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.Pattern;


/**
 * Created by matin on 8/16/16.
 */
public class CategoryDetection {
/*
    final private static String classifierFile = "mallet_document_classifier.maxent";
    private static Logger logger = Logger.getLogger(CategoryDetection.class);


    private Classifier classifier = null;
    private Pipe pipe;

    public CategoryDetection()  {
        ArrayList pipeList = new ArrayList();
        pipeList.add(new CharSequenceLowercase() );
        pipeList.add(new LinePreProcess() );
//        pipeList.add(new StandardTextNorm() );
//        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("[\\p{L}\\p{N}_]+")));
        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\S+")));
        pipeList.add(new TokenSequenceRemoveStopwords(new File("models/stoplist.txt"), "UTF-8", false, false, false) );
        pipeList.add(new TokenSequence2FeatureSequence());
        pipe = new SerialPipes(pipeList);
        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private InstanceList getInstanceFromFile(String file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        InstanceList instantList = new InstanceList(pipe);
        while ((line = br.readLine()) != null) {
            String [] parts = line.split("\\t");
            String body  = parts[0];
            String label = parts[1];
            Instance ins = new Instance(body, label, "events", "events");
            instantList.addThruPipe(ins);
        }
        return instantList;
    }

    private void train() throws IOException {
        logger.info("Training mallet document classifier");
        InstanceList trainingInstances = getInstanceFromFile("monop.labels");
        ClassifierTrainer trainer = new MaxEntTrainer();
        classifier = trainer.train(trainingInstances);
        ObjectOutputStream oos =
                new ObjectOutputStream(new FileOutputStream(classifierFile));
        oos.writeObject (classifier);
        oos.close();
    }

    public void init() throws IOException, ClassNotFoundException {
        logger.info("initiliziaing classifier");
        if (classifier == null){
            File f = new File(classifierFile);
            if(f.exists()) {
                ObjectInputStream ois =
                        new ObjectInputStream(new FileInputStream(classifierFile));
                classifier = (Classifier) ois.readObject();
                ois.close();
            } else {
                train();
            }
        }
    }
    */
}
