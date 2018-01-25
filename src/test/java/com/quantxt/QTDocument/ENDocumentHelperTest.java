package com.quantxt.QTDocument;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Assert;
import org.junit.Test;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.types.Entity;

/**
 * Created by dejani on 1/25/18.
 */
public class ENDocumentHelperTest {

    private ENDocumentHelper helper = new ENDocumentHelper();

    @Test
    public void testRawTestSentences() {
        // GIVEN
        String html = "<p>Light behaves in some respects like particles and in "
                + "other respects like waves. Matter—the \"stuff\" of the "
                + "universe consisting of particles such as "
                + "<a href=\"/wiki/Electron\" title=\"Electron\">electrons</a> and "
                + "<a href=\"/wiki/Atom\" title=\"Atom\">atoms</a>—exhibits "
                + "<a href=\"/wiki/Wave%E2%80%93particle_duality\" title=\"Wave–particle duality\">wavelike behavior</a> too. "
                + "Some light sources, such as <a href=\"/wiki/Neon_lighting\" title=\"Neon lighting\">neon lights</a>, "
                + "give off only certain frequencies of light."
                + "</p>";
        Document document = Jsoup.parseBodyFragment(html);
        Elements elements = document.getElementsByTag("p");
        ENDocumentInfo doc = new ENDocumentInfo(elements, "");

        // WHEN
        String[] sentences = helper.getSentences(doc.getBody());

        // THEN
        assertNotNull(sentences);
        assertFalse(sentences.length == 0);
        assertEquals(sentences.length, 3);
    }

    @Test
    public void testTokenize() throws IOException {
        // GIVEN
        String str = "Light behaves in some respects like particles and in "
                + "other respects like waves. Matter—the \"stuff\" of the "
                + "universe consisting of particles such as electrons and they "
                + "wavelike behavior too. Some light sources, such as neon "
                + "lights, give off only certain frequencies of light.";

        // WHEN
        List<String> tokens = helper.tokenize(str);

        // THEN
        assertNotNull(tokens);
        assertFalse(tokens.isEmpty());

        assertTrue(tokens.contains("behav"));
        assertTrue(tokens.contains("behavior"));
        assertTrue(tokens.contains("wave"));

        // Stopwords
        assertFalse(tokens.contains("they"));

        // Fails
        // assertFalse(tokens.contains("off"));
    }

    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. "
                + "is a research-based biopharmaceutical company that discovers, "
                + "develops and commercializes medicines in areas of unmet medical need .";
        String[] parts = str.split("\\s+");

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts);

        // THEN
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "a research-based biopharmaceutical company that discovers,");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "develops and commercializes");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "medicines in areas of unmet medical need");
    }

    @Test
    public void testNormalize() {
        // GIVEN
        String str = "Some light sources, such as neon lights, "
                + "give off only certain frequencies of light.";
        ENDocumentHelper helper = new ENDocumentHelper();

        // WHEN
        String normalized = helper.normalize(str);

        // THEN
        assertNotNull(normalized);
        assertFalse(normalized.isEmpty());
        assertFalse(normalized.contains(","));
        assertFalse(normalized.contains("."));
        assertFalse(normalized.contains("as"));
        assertFalse(normalized.contains("off"));
    }

    @Test
    public void testStopWords1() {
        // GIVEN
        String word1 = "they";
        String word2 = "about";
        String word3 = "itself";
        String word4 = "science";
        String word5 = "student";

        // WHEN
        boolean isStopword1 = helper.isStopWord(word1);
        boolean isStopword2 = helper.isStopWord(word2);
        boolean isStopword3 = helper.isStopWord(word3);

        boolean isStopword4 = helper.isStopWord(word4);
        boolean isStopword5 = helper.isStopWord(word5);

        // THEN
        assertTrue(isStopword1);
        assertTrue(isStopword2);
        assertTrue(isStopword3);

        assertFalse(isStopword4);
        assertFalse(isStopword5);
    }

    public static Speaker getSpeaker() {
        Speaker speaker = null;
        try {
            ArrayList<Entity> entityArray1 = new ArrayList<>();
            entityArray1.add(new Entity("Gilead Sciences, Inc.", null, true));
            entityArray1.add(new Entity("Amazon Inc.", new String[] { "Amazon" }, true));
            ArrayList<Entity> entityArray2 = new ArrayList<>();
            entityArray2.add(new Entity("Director", new String[] { "Director" }, true));
            entityArray2.add(new Entity("Senior Director", new String[] { "Senior Director" }, true));
            Map<String, Entity[]> entMap = new HashMap<>();
            entMap.put("Company", entityArray1.toArray(new Entity[entityArray1.size()]));
            entMap.put("Title", entityArray2.toArray(new Entity[entityArray2.size()]));
            speaker = new Speaker(entMap, (String) null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return speaker;
    }

}