package com.quantxt.doc.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.quantxt.types.ExtIntervalSimple;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dejani on 1/25/18.
 */

public class ENDocumentHelperTest {

    private static ENDocumentHelper helper;

    private static Logger logger = LoggerFactory.getLogger(ENDocumentHelperTest.class);

    @BeforeClass
    public static void setup() {
        if (helper != null) return;
        helper = new ENDocumentHelper();
        helper.init();
        helper.loadNERModel();
    }

    @Test
    public void testTokenize() {
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

        assertTrue(tokens.contains("electrons"));
        assertTrue(tokens.contains("behavior"));

        // Stopwords
        assertTrue(tokens.contains("they"));

        // Fails
        // assertFalse(tokens.contains("off"));
    }

    @Test
    public void EntestEntityExtract1() {
        // GIVEN
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. "
                + "is a research-based biopharmaceutical company that discovers, "
                + "develops and commercializes medicines in areas of unmet medical need .";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);

        // THEN
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "research-based biopharmaceutical company");
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "develops");
        Assert.assertEquals(str.substring(tagged.get(9).getStart(), tagged.get(9).getEnd()),
                "unmet medical need");
    }

    @Test
    public void EntestEntityExtract2() {
        // GIVEN
        String str = "Personal luxury goods growth stalls in ME even as global market rebounds " +
                "Personal luxury goods growth stalls in ME even as global market " +
                "rebounds Global personal luxury goods market growth is on its way for a rebound " +
                "on the back of a strong demand from Chinese customers, a recent research reveals.";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Personal luxury goods growth stalls");
        Assert.assertEquals(str.substring(tagged.get(12).getStart(), tagged.get(12).getEnd()),
                "Chinese customers");
    }

    @Test
    public void EntestEntityExtract3() {
        // GIVEN
        String str = "“The “Creation of an Enabling Regulatory Environment for Blockchain " +
                "Projects is currently crucial” Artem Tolkachev, Director of Legal Services " +
                "for Technology Projects at Deloitte CIS, described the development of permissive " +
                "regulatory frameworks for blockchain and cryptocurrency as necessary in order to " +
                "empower innovation within the industry.";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);
        // THEN
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "Director");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "Legal Services");
        Assert.assertEquals(str.substring(tagged.get(11).getStart(), tagged.get(11).getEnd()),
                "permissive regulatory frameworks");
    }


    @Test
    public void EntestEntityExtract4() {
        // GIVEN
        String str = "Many venture capital firms and investment groups have recently keyed in " +
                "on investing and acquiring innovative and out-of-the-box blockchain startups, "+
                "that seek to push the boundaries of the technology and where it can reach.";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);

        // THEN
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "have recently keyed");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "acquiring");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "boundaries");
    }


    @Test
    public void EntestEntityExtract5() {
        // GIVEN
        String str = "Oil imports help feed US export powerhouse Shale revolution and " +
                "end of curbs contribute to increased flow both ways The US oil industry is " +
                "rapidly turning the country into an energy export powerhouse, tipped last " +
                "week by one prominent consultancy to start shipping more oil overseas than " +
                "the majority of Opec countries by 2020.";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Oil imports");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "powerhouse Shale revolution");
        Assert.assertEquals(str.substring(tagged.get(10).getStart(), tagged.get(10).getEnd()),
                "US oil industry");
    }

    @Test
    public void testBulletPointSplit_1() {
        // GIVEN
        String str = "damage to physician and patient confidence in any of our MS products. •\n" +
                "difficulty in penetrating this market if our therapies are not regarded as offering significant benefits over current treatments;";

        // WHEN
        String [] sentences = helper.getSentences(str);
        assertNotNull(sentences);
        assertFalse(sentences.length == 3);
        assertFalse(sentences[1].startsWith("\ndifficulty"));
    }


    @Test
    public void testNormalize1() {
        // GIVEN
        String str = "Some light sources, such as neon lights, "
                + "give off only certain frequencies of light.";

        // WHEN
        String normalized = helper.normalize(str);

        // THEN
        assertNotNull(normalized);
        assertFalse(normalized.isEmpty());
        assertFalse(normalized.contains(","));
        assertFalse(normalized.contains("."));
        assertFalse(normalized.contains("Some"));
    //    assertFalse(normalized.contains("off"));
    }

    @Test
    public void testNormalize2() {
        // GIVEN
        String str = "AT&T and Johsnson & Johnson are biggest U.S. companaies";

        // WHEN
        String normalized = helper.normalize(str);

        // THEN
        assertNotNull(normalized);
        assertFalse(normalized.isEmpty());
        assertTrue(normalized.contains("at&t"));
        assertTrue(normalized.contains("u.s."));
        //    assertFalse(normalized.contains("off"));
    }

    @Test
    public void testIsTag() {
        // GIVEN
        String tag1 = "IN";
        String tag2 = "TO";
        String tag3 = "CC";
        String tag4 = "DT";

        String tag5 = "DD";
        String tag6 = "CS";
        String tag7 = "S";

        // WHEN
        boolean isTag1 = helper.isTagDC(tag1);
        boolean isTag2 = helper.isTagDC(tag2);
        boolean isTag3 = helper.isTagDC(tag3);
        boolean isTag4 = helper.isTagDC(tag4);

        boolean isTag5 = helper.isTagDC(tag5);
        boolean isTag6 = helper.isTagDC(tag6);
        boolean isTag7 = helper.isTagDC(tag7);

        // THEN
        assertTrue(isTag1);
        assertTrue(isTag2);
        assertTrue(isTag3);
        assertTrue(isTag4);

        assertFalse(isTag5);
        assertFalse(isTag6);
        assertFalse(isTag7);
    }

    @Test
    public void testNounAndVerbPhrases1() {
        // GIVEN
        String str = "Oil imports help feed US export powerhouse Shale revolution and " +
                "end of curbs contribute to increased flow both ways The US oil industry is " +
                "rapidly turning the country into an energy export powerhouse, tipped last " +
                "week by one prominent consultancy to start shipping more oil overseas than " +
                "the majority of Opec countries by 2020.";

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str);

        // THEN
        assertNotNull(tagged);
    }
}