package com.quantxt.QTDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.types.Entity;
import com.quantxt.util.StringUtil;

/**
 * Created by matin on 10/10/17.
 */

public class ENDocumentInfoTest {

    private QTExtract enx;

    @BeforeClass
    public void init() {
        try {
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
    }

    @Test
    public void testEntityExtract1(){
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. "
                + "is a research-based biopharmaceutical company that discovers, "
                + "develops and commercializes medicines in areas of unmet medical need .";

        ENDocumentHelper helper = new ENDocumentHelper();
        List<String> partlist = helper.tokenize(str);
        String [] parts = partlist.toArray(new String[partlist.size()]);
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts);

        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()), "biopharmaceutical company that discovers,");
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()), "develops and commercializes");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()), "medicines in areas of unmet medical need");
    }

    @Test
    public void testNounVerbPh1() {
        String str = "Gilead Sciences, Inc. told to reuters reporters.";
        QTDocument doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2() {
        String str = "Amazon Inc. reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3() {
        String str = "Amazon reported a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4() {
        String str = "Amazon Corp reported a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5() {
        String str = "Amazon LLC announced a gain on his earnings .";
        QTDocument doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh6() {
        String str = "He works as a high rank Senior Director in Amazon";
        QTDocument doc = new ENDocumentInfo(str, "");
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Title").iterator().next(), "Senior Director");
    }

    @Test
    public void findSpan1() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("a");
        tokens.add("research");
        ExtInterval spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "a research-based");
    }

    @Test
    public void findSpan2() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("Gilead");
        tokens.add("Science");
        ExtInterval spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "Gilead Sciences,");
    }

    @Test
    public void findSpan3() {
        String str = "Бывший мэр Даугавпилса рассказал о схемах местных депутатов, "
                + "чтобы успешнее осваивать деньги из городской казны и еврофондов в собственные карманы .";
        List<String> tokens = new ArrayList<>();
        tokens.add("Бывший");
        tokens.add("мэр");
        tokens.add("Даугавпилса");
        ExtInterval spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "Бывший мэр Даугавпилса");
    }
}