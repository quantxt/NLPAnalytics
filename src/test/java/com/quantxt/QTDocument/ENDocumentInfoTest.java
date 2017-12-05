package com.quantxt.QTDocument;

import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.ExtInterval;
import com.quantxt.types.Entity;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by matin on 10/10/17.
 */
public class ENDocumentInfoTest {

    private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfoTest.class);
    private static QTExtract enx;
    private static boolean initizlied = false;

    private static void init() {
        if (initizlied) return;

        try {
            ENDocumentInfo.init(null);
            ArrayList<Entity> entityArray1 = new ArrayList<>();
            entityArray1.add(new Entity("Gilead Sciences, Inc." , null , true));
            entityArray1.add(new Entity("Amazon Inc." , new String[]{"Amazon"} , true));
            ArrayList<Entity> entityArray2 = new ArrayList<>();
            entityArray2.add(new Entity("Director" , new String[]{"Director"} , true));
            entityArray2.add(new Entity("Senior Director" , new String[]{"Senior Director"} , true));
            Map<String, Entity[]> entMap = new HashMap<>();
            entMap.put("Company" , entityArray1.toArray(new Entity[entityArray1.size()]));
            entMap.put("Title" , entityArray2.toArray(new Entity[entityArray2.size()]));
            enx = new Speaker(entMap, (String)null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        initizlied = true;
    }

    @Test
    public void testEntityExtract1(){
        init();
        String str =
                "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based biopharmaceutical company that discovers, develops and commercializes medicines in areas of unmet medical need .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        String [] parts = str.split("\\s+");
        List<ExtInterval> tagged = doc.getNounAndVerbPhrases(str, parts);
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()), "a research-based biopharmaceutical company that discovers,");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()), "develops and commercializes");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()), "medicines in areas of unmet medical need");
    }

    @Test
    public void testNounVerbPh1(){
        init();
        String str = "Gilead Sciences, Inc. told to reuters.";
        QTDocument doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next() , "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2(){
        init();
        String str = "Amazon Inc. reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3(){
        init();
        String str = "Amazon reported a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4(){
        init();
        String str = "Amazon Corp reported a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5(){
        init();
        String str = "Amazon LLC announced a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh6(){
        init();
        String str = "He works as a high rank Senior Director in Amazon";
        QTDocument doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Title").iterator().next() , "Senior Director");
    }
}
