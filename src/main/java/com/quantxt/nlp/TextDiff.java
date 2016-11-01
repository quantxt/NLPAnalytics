package com.quantxt.nlp;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.helpers.ValueComparator;
import com.quantxt.nlp.comp.TERalignment;
import com.quantxt.nlp.comp.TERcalc;
import com.quantxt.nlp.comp.TERcost;
import org.apache.log4j.Logger;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by matin on 10/7/16.
 */
public class TextDiff {

    private static Logger logger = Logger.getLogger(TERalignment.class);

    final private TERcost costfunc;

    public TextDiff(int delc, int insc, int subc, int shiftc) throws Exception {
        ENDocumentInfo.init();
        TERcalc.setCase(true);
        costfunc = new TERcost();
        costfunc._delete_cost = delc;
        costfunc._insert_cost = insc;
        costfunc._shift_cost = shiftc;
        costfunc._match_cost = 0;
        costfunc._substitute_cost = subc;
    }

    private static String file2str(String filename){
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        try {
            String line;

            br = new BufferedReader(new FileReader(filename));

            while ((line = br.readLine()) != null) {
                sb.append(line).append(" ");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public Map<String, List<String>> removeRepSentences(String text1, String text2){
        String sentences1[] = ENDocumentInfo.getSentences(text1);
        String sentences2[] = ENDocumentInfo.getSentences(text2);

        int shift = 10;

        int set1Idx = 0;
        int set2Idx = 0;
        List<String> text1Reduced = new ArrayList<>();
        List<String> text2Reduced = new ArrayList<>();
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        for (int i = 0; i<sentences1.length; i++){
            String sent1 = sentences1[i];
            logger.info("length1: " + sent1.split("\\s+").length);
            int startCmp = Math.max(set2Idx - shift, 0);
            int endCmp = Math.min(set2Idx + shift, sentences2.length);
            for (int j=startCmp; j <endCmp; j++){
                String sent2 = sentences2[j];
                logger.info("length2: " + sent2.split("\\s+").length);
                if (sent1.equals(sent2)){
//                    logger.info("addind " + set1Idx + " -> " + i + " / " + set2Idx + " -> " + j);
                    for (int k=set2Idx; k <j; k++){
                        sb2.append(sentences2[k]).append(" ");
                    }
                    for (int k=set1Idx; k <i; k++){
                        sb1.append(sentences1[k]).append(" ");
                    }
                    if (sb1.length() > 0 || sb2.length() > 0) {
                        text1Reduced.add(sb1.toString());
                        text2Reduced.add(sb2.toString());
                        sb2 = new StringBuilder();
                        sb1 = new StringBuilder();
                    }
                    set2Idx = j+1;
                    set1Idx = i+1;
                    break;
                }
            }
        }

        for (int k=set2Idx; k <sentences2.length; k++){
            sb2.append(sentences2[k]).append(" ");
        }

        for (int k=set1Idx; k <sentences1.length; k++){
            sb1.append(sentences1[k]).append(" ");
        }

        if (sb1.length() > 0 || sb2.length() > 0) {
            text1Reduced.add(sb1.toString());
            text2Reduced.add(sb2.toString());
        }

        logger.info("1: " + sentences1.length + " --> " + text1Reduced.size());
        logger.info("2: " + sentences2.length + " --> " + text2Reduced.size());

        Map<String, List<String>> res = new HashMap<>();
        res.put("text1" , text1Reduced);
        res.put("text2" , text2Reduced);
        return res;
    }


    public String getTextComp(String text1, String text2){

        Map<String, List<String>> shortend = removeRepSentences(text1, text2);
        List<String> text1List = shortend.get("text1");
        List<String> text2List = shortend.get("text2");
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>.insert {color: blue;}" +
                "</style></head><body>");
        for (int i=0; i < text1List.size(); i++) {
            TERalignment result = TERcalc.TER(text1List.get(i), text2List.get(i), costfunc);
            sb.append("<div>");
            char[] alignmentResult = result.alignment;

            int hypidx = 0;
            int refidx = 0;
            int idx = 0;
            while (idx < alignmentResult.length) {
                char d = alignmentResult[idx];
                if (d == 'S') {
                    String hs = "";
                    String rs = "";
                    while (d == 'S') {
                        hs += result.aftershift[hypidx++] + " ";
                        rs += result.ref[refidx++] + " ";
                        idx++;
                        if (idx >= alignmentResult.length) break;
                        d = alignmentResult[idx];
                    }
                    sb.append("<strike>").append(rs).append("</strike>")
                            .append("<span class=\"insert\">").append(hs).append("</span>");
                } else if (d == 'I') {
                    sb.append("<span class=\"insert\">");
                    while (d == 'I') {
                        sb.append(result.aftershift[hypidx++]).append(" ");
                        idx++;
                        if (idx >= alignmentResult.length) break;
                        d = alignmentResult[idx];
                    }
                    sb.append("</span>");
                } else if (d == 'D') {
                    sb.append("<strike>");
                    while (d == 'D') {
                        sb.append(result.ref[refidx++]).append(" ");
                        idx++;
                        if (idx >= alignmentResult.length) break;
                        d = alignmentResult[idx];
                    }
                    sb.append("</strike>");
                } else {
                    sb.append(result.ref[refidx++]).append(" ");
                    hypidx++;
                    idx++;
                }
            }
            sb.append("</div><br>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        int numTopics = 150;
        TopicModel tm = new TopicModel(numTopics, 500, "quotes");
     //   tm.loadInfererFromFile("myfile.txt");
        tm.loadInfererFromW2VFile("official_w2vec_100.txt");

        List<double []> allProbs = new ArrayList<>();
        List<String> allSents = new ArrayList<>();
        List<String> allTopicSents = new ArrayList<>();
        InstanceList instantList = new InstanceList(tm.getPipe());

//        TERcalc.setCase(true);
//        TERcost costfunc = new TERcost();
//        costfunc._delete_cost = 1;
//        costfunc._insert_cost = 1;
//        costfunc._shift_cost = 50;
//        costfunc._match_cost = 0;
//        costfunc._substitute_cost = 1;

        BufferedReader br = new BufferedReader(new FileReader("quotes.csv"));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                allSents.add(line);

//                String [] words = line.toLowerCase().split("\\s+");
//                StringBuilder sb = new StringBuilder();
//                for (String w : words){
//                    LDATopic ldaTopic = tm.getWLDATopic(w);
//                    if (ldaTopic == null){
               //         logger.info("Null " + w);
//                    } else {
//                        sb.append(ldaTopic.getBestTopic()).append(" ");
                 //       logger.info(w + " " + ldaTopic.getBestTopic());
//                    }
//                }

//                logger.info(sb.toString());
//                allTopicSents.add(sb.toString().trim());

                double[] p = tm.getSentenceVector(line);

//                double[] probs = tm.getProbs(instantList.get(instantList.size() - 1));
                allProbs.add(p);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null)br.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        double [] interestVec  = tm.getSentenceVector("interest rate rates hike cut");
        double [] inflation    = tm.getSentenceVector("inflation");
        double [] unemployment = tm.getSentenceVector("unemployment employment jobs job");
        double [] growth       = tm.getSentenceVector("economic growth");

        for (int i = 0; i <allSents.size(); i++){
            HashMap<String, Double> allprobs = new HashMap<>();
            double[] prob1 = allProbs.get(i);
            double fed = TopicModel.cosineSimilarity(prob1, interestVec);
            double ecb = TopicModel.cosineSimilarity(prob1, inflation);
            double boe = TopicModel.cosineSimilarity(prob1, unemployment);
            double boj = TopicModel.cosineSimilarity(prob1, growth);
            allprobs.put("rate", fed);
            allprobs.put("inflation", ecb);
            allprobs.put("unemployment", boe);
            allprobs.put("growth", boj);

/*
            for (int j = 0; j < instantList.size(); j++){
                double[] prob2 = allProbs.get(j);
                double d = TopicModel.cosineSimilarity(prob1, prob2);
                allprobs.put(j, d);
            }
*/
            ValueComparator bvc = new ValueComparator(allprobs);
            TreeMap<String, Double> sorted_map = new TreeMap<>(bvc);
            sorted_map.putAll(allprobs);
            StringBuilder sb = new StringBuilder();
            sb.append(allSents.get(i)).append("\t");
            ArrayList<String> keys = new ArrayList<>(sorted_map.keySet());

            for(int k=0; k < keys.size(); k++){
                String tag = keys.get(k);
                double s   = allprobs.get(tag);
                if (s < 0) break;
                sb.append("(").append(tag).append(":").append(s).append(")\t");
            }
            logger.info(sb.toString().trim());
        }
 //       TextDiff td = new TextDiff(1, 1, 1, 10);
 //       String res = td.getTextComp(file2str("hyp"), file2str("ref"));
 //       logger.info(res);
    }
}




