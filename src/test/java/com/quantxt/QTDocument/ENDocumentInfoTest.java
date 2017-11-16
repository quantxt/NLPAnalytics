package com.quantxt.QTDocument;

import com.quantxt.SearchConcepts.Entity;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.ExtInterval;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
            ArrayList<Entity> entityArray = new ArrayList<>();
            entityArray.add(new Entity("Gilead Sciences, Inc." , null , true));
            entityArray.add(new Entity("Amazon Inc." , new String[]{"Amazon"} , true));
            Entity[] entities = entityArray.toArray(new Entity[entityArray.size()]);
            enx = new Speaker(entities, null, null);
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
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Assert.assertEquals(docs.get(0).getEntity() , "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2(){
        init();
        String str = "Amazon Inc. reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Assert.assertEquals(docs.get(0).getEntity() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3(){
        init();
        String str = "Amazon reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Assert.assertEquals(docs.get(0).getEntity() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4(){
        init();
        String str = "Amazon Corp reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Assert.assertEquals(docs.get(0).getEntity() , "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5(){
        init();
        String str = "Amazon LLC announced a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        doc.processDoc();
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Assert.assertEquals(docs.get(0).getEntity() , "Amazon Inc.");
    }
}
