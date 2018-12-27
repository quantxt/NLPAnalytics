package com.quantxt.QTDocument;

import com.quantxt.nlp.ExtractLc;
import com.quantxt.trie.Emit;
import com.quantxt.types.Entity;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by matin on 12/9/18.
 */
public class ExtractLcTest {

    @Test
    public void testParseNames() throws IOException {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";
        ExtractLc speaker = getSpeaker();

        // WHEN
        Map<String, Collection<Emit>> result = speaker.parseNames(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon Inc");

    }

    @Test
    public void testSynonymNotStemable() throws IOException {
        // GIVEN
        String str = "Amazon inciobi reported a gain on his earnings.";
        ExtractLc speaker = getSpeaker();

        // WHEN
        Map<String, Collection<Emit>> result = speaker.parseNames(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon inciobi");

    }

    @Test
    public void testSynonymStemable() throws IOException {
        // GIVEN
        // corporate will become corpor
        String str = "Amazon corporate reported a gain on his earnings.";
        ExtractLc speaker = getSpeaker();

        // WHEN
        Map<String, Collection<Emit>> result = speaker.parseNames(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon corporate");

    }

    public static ExtractLc getSpeaker() {
        ExtractLc speaker = null;
        try {
            ArrayList<Entity> entityArray1 = new ArrayList<>();
            entityArray1.add(new Entity("Gilead Sciences, Inc.", null, true));
            entityArray1.add(new Entity("Amazon Inc.", new String[] { "Amazon" }, true));
            entityArray1.add(new Entity("Amazon Inc.", null, true));
            ArrayList<Entity> entityArray2 = new ArrayList<>();
            entityArray2.add(new Entity("Director", new String[] { "Director" }, true));
            entityArray2.add(new Entity("Senior Director", new String[] { "Senior Director" }, true));
            Map<String, Entity[]> entMap = new HashMap<>();
            entMap.put("Company", entityArray1.toArray(new Entity[entityArray1.size()]));
            entMap.put("Title", entityArray2.toArray(new Entity[entityArray2.size()]));
            speaker = new ExtractLc(entMap, null, null);

            // synonyms;
            ArrayList<String> synonymMap = new ArrayList<>();
            synonymMap.add("inciobi\tInc.");
            synonymMap.add("corporate\tInc");
            speaker.setSynonyms(synonymMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return speaker;
    }

}
