package com.quantxt.QTDocument;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.quantxt.nlp.comp.meteor.MeteorComp;
import com.quantxt.nlp.comp.meteor.scorer.MeteorScorer;
import com.quantxt.nlp.comp.meteor.scorer.MeteorStats;

/**
 * Created by dejani on 1/28/18.
 */
public class MeteorCompTest {

    @Test
    public void testEmptyScorePlaintext() throws IOException {
        // GIVEN
        MeteorScorer scorer = mock(MeteorScorer.class);
        MeteorStats meteorStats = new MeteorStats();
        doReturn(meteorStats).when(scorer)
                .getMeteorStats(anyString(), anyString());

        List<String> lines1 = Arrays.asList("test", "123");
        List<String> lines2 = Arrays.asList("abc", "555");

        // WHEN
        MeteorStats stats = MeteorComp.scorePlaintext(scorer, lines1, lines2);

        // THEN
        assertNotNull(stats);
        assertNull(stats.alignment);
        assertTrue(stats.chunks == 0);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testDiffernetSizeArraysScorePlaintext() throws IOException {
        // GIVEN
        MeteorScorer scorer = mock(MeteorScorer.class);
        MeteorStats meteorStats = new MeteorStats();
        doReturn(meteorStats).when(scorer)
        .getMeteorStats(anyString(), anyString());

        List<String> lines1 = Arrays.asList("test", "123");
        List<String> lines2 = Arrays.asList("abc");

        // WHEN
        MeteorComp.scorePlaintext(scorer, lines1, lines2);

        // THEN
    }

    @Test
    public void testNonEmptyScorePlaintext() throws IOException {
        // GIVEN
        MeteorScorer scorer = mock(MeteorScorer.class);
        MeteorStats meteorStats = new MeteorStats("15 14 4 3 6 6 2 2 1 1 0 0 1 1 0 0 2 2 1 1 3 15 14");
        doReturn(meteorStats).when(scorer).getMeteorStats(anyString(), anyString());

        List<String> lines1 = Arrays.asList("test", "123");
        List<String> lines2 = Arrays.asList("abc", "555");

        // WHEN
        MeteorStats stats = MeteorComp.scorePlaintext(scorer, lines1, lines2);

        // THEN
        assertNotNull(stats);
        assertNull(stats.alignment);
        assertTrue(stats.chunks == 6.0);
    }

}
