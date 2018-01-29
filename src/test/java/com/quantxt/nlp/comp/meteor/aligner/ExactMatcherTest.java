package com.quantxt.nlp.comp.meteor.aligner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

/**
 * Created by dejani on 1/28/18.
 */
public class ExactMatcherTest {

    @Test
    public void testMatchAlignmentSameWords() {
        // GIVEN
        ArrayList<String> words1 = new ArrayList<>(Arrays.asList("java", "c++", "php"));
        ArrayList<String> words2 = new ArrayList<>(Arrays.asList("java", "c", "js"));
        Stage s = new Stage(words1, words2);

        // WHEN
        ExactMatcher.match(0, s);

        // THEN
        assertNotNull(s.matches);
        assertFalse(s.matches.isEmpty());
        assertFalse(s.matches.get(0).isEmpty());
        assertTrue(s.line1Coverage[0] == 1);
        assertTrue(s.line2Coverage[0] == 1);

        assertTrue(s.line1Coverage[1] == 0);
        assertTrue(s.line2Coverage[1] == 0);
    }

    @Test
    public void testNonMatchAlignmentSameWords() {
        // GIVEN
        ArrayList<String> words1 = new ArrayList<>(Arrays.asList("kotlin", "c++", "php"));
        ArrayList<String> words2 = new ArrayList<>(Arrays.asList("java", "c", "js"));
        Stage s = new Stage(words1, words2);

        // WHEN
        ExactMatcher.match(0, s);

        // THEN
        assertNotNull(s.matches);
        assertFalse(s.matches.isEmpty());
        assertTrue(s.matches.get(0).isEmpty());
        assertTrue(s.line1Coverage[0] == 0);
        assertTrue(s.line2Coverage[0] == 0);
    }

}
