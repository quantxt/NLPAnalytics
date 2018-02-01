package com.quantxt.QTDocument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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

    private static QTExtract enx;
    private static ENDocumentHelper helper;
    private static boolean setUpIsDone = false;

    @BeforeClass
    public static void init() {
        if (setUpIsDone) {
            return;
        }
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
            helper = new ENDocumentHelper();
            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNounVerbPh1() {
        String str = "Gilead Sciences, Inc. told to reuters reporters.";
        QTDocument doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2() {
        String str = "Amazon Inc. reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3() {
        String str = "Amazon reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4() {
        String str = "Amazon Corp reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5() {
        String str = "Amazon LLC announced a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh6() {
        String str = "He works as a high rank Senior Director in Amazon";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
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

    @Test
    public void testVectorizedTitleNotNullArray() {
        // GIVEN
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);

        // WHEN
        double[] vectorizedTitle = doc.getVectorizedTitle(enx);

        // THEN
        assertNotNull(vectorizedTitle);
    }

    @Test
    public void testDocumentChildsNotEmptyAndHasEqualProperties() {
        // GIVEN
        String str = "Light behaves in some respects like particles and in "
                + "other respects like waves. Matter—the \"stuff\" of the "
                + "universe consisting of particles such as electrons and they "
                + "wavelike behavior too. Some light sources, such as neon "
                + "lights, give off only certain frequencies of light.";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        doc.setLink("link");
        doc.setLogo("logo");

        // WHEN
        List<QTDocument> childs = doc.getChilds();

        // THEN
        assertNotNull(childs);
        assertFalse(childs.isEmpty());
        assertEquals(childs.size(), 3);
        assertEquals(childs.get(0).getTitle(), "Light behaves in some respects like particles and in other respects like waves.");
        assertEquals(childs.get(0).getLink(), doc.getLink());
        assertEquals(childs.get(0).getLogo(), doc.getLogo());
        assertEquals(childs.get(0).getLanguage(), doc.getLanguage());
    }
}
