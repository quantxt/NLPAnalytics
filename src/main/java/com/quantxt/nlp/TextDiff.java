package com.quantxt.nlp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.nlp.comp.TERalignment;
import com.quantxt.nlp.comp.TERcalc;
import com.quantxt.nlp.comp.TERcost;

/**
 * Created by matin on 10/7/16.
 */
public class TextDiff {

    private static Logger logger = LoggerFactory.getLogger(TextDiff.class);

    final private TERcost costfunc;
    final private TERcalc tcalc;

    public TextDiff(int delc, int insc, int subc, int shiftc) throws Exception {
        tcalc = new TERcalc();
        tcalc.setCase(true);
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
        ENDocumentHelper helper = new ENDocumentHelper();
        String sentences1[] = helper.getSentences(text1);
        String sentences2[] = helper.getSentences(text2);

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

    public static TERalignment getAlignment(String sent1, String sent2, TERcost cf){
        TERcalc tcalc = new TERcalc();
        TERalignment result = tcalc.TER(sent1, sent2, cf);
        return result;
    }

    public String getTextComp(String text1, String text2){

        Map<String, List<String>> shortend = removeRepSentences(text1, text2);
        List<String> text1List = shortend.get("text1");
        List<String> text2List = shortend.get("text2");
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><style>.insert {color: blue;}" +
                "</style></head><body>");
        for (int i=0; i < text1List.size(); i++) {
            TERalignment result = tcalc.TER(text1List.get(i), text2List.get(i), costfunc);
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

//        TERcalc.setCase(true);
//        TERcost costfunc = new TERcost();
//        costfunc._delete_cost = 1;
//        costfunc._insert_cost = 1;
//        costfunc._shift_cost = 50;
//        costfunc._match_cost = 0;
//        costfunc._substitute_cost = 1;

 //       TextDiff td = new TextDiff(1, 1, 1, 10);
 //       String res = td.getTextComp(file2str("hyp"), file2str("ref"));
 //       logger.info(res);
    }
}




