package com.quantxt.nlp.comp.meteor.aligner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

/**
 * Created by dejani on 1/28/18.
 */
public class AlignerTest {

    @Test
    public void testDefaultConstructForExistingLanguage() {
        // GIVEN
        String language = "en";
        ArrayList<Integer> modules = new ArrayList<>(Arrays.asList(0,1,2));

        // WHEN
        Aligner aligner = new Aligner(language, modules);

        // THEN
        assertNotNull(aligner);
        assertNotNull(aligner.getModules());
        assertNotNull(aligner.getLanguage());
        assertNotNull(aligner.getPartialComparator());

        assertEquals(aligner.getLanguage(), "english");
        assertFalse(aligner.getModules().isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void testDefaultConstructForNonExistingLanguage() {
        // GIVEN
        String language = "mm";
        ArrayList<Integer> modules = new ArrayList<>(Arrays.asList(0,1,2));

        // WHEN
        new Aligner(language, modules);

        // THEN
    }
    @Test(expected = RuntimeException.class)
    public void testNonExistingMatcher() {
        // GIVEN
        String language = "en";
        ArrayList<Integer> modules = new ArrayList<>(Arrays.asList(5));

        // WHEN
        new Aligner(language, modules).align("123", "abc");

        // THEN
    }

    @Test
    public void testAlign() {
        // GIVEN
        String language = "en";
        ArrayList<Integer> modules = new ArrayList<>(Arrays.asList(0));
        Aligner aligner = new Aligner(language, modules);

        Alignment a = new Alignment("words", "words");

        // WHEN
        aligner.align(a);

        // THEN
        assertNotNull(a.matches);
        assertTrue(a.matches.length == 1);
        assertTrue(a.avgChunkLength == 1);
        assertTrue(a.line1Matches == 1);
        assertTrue(a.line2Matches == 1);
    }
}
