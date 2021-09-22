package com.quantxt.doc.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.BeforeClass;
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

        assertTrue(tokens.contains("electron"));
        assertTrue(tokens.contains("behavior"));

        // Stopwords
        assertFalse(tokens.contains("they"));

        // Fails
        // assertFalse(tokens.contains("off"));
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
}