package com.quantxt.QTDocument;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.quantxt.nlp.Speaker;
import com.quantxt.trie.Emit;
import com.quantxt.types.Entity;

/**
 * Created by dejani on 1/25/18.
 */
public class SpeakerTest {

    @Test
    public void testParseNames() throws IOException {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";
        Speaker speaker = getSpeaker();

        // WHEN
        Map<String, Collection<Emit>> result = speaker.parseNames(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon Inc.");

    }

    @Test
    public void testEntitiesDefaultLoad() throws IOException {
        // GIVEN
        Map<String, Entity[]> entities = null;
        String taggerDir = null;
        InputStream phraseFile = null;

        // WHEN
        Speaker speaker = new Speaker(entities, taggerDir, phraseFile);

        // THEN
        assertNotNull(speaker.getNameTree());

        assertFalse(speaker.getNameTree().isEmpty());
    }

    @Test(expected = IOException.class)
    public void testWhenNotExistsPhraseFileThenIOException() throws IOException {
        // GIVEN
        Map<String, Entity[]> entities = null;
        String taggerDir = null;
        InputStream phraseFile = new FileInputStream(new File("dummy.txt"));

        // WHEN
        new Speaker(entities, taggerDir, phraseFile);

        // THEN
    }

    @Test
    public void testWhenNotExistsTeggerDirThenTaggerIsNull() throws IOException {
        // GIVEN
        Map<String, Entity[]> entities = null;
        String taggerDir = "test_dir";
        InputStream phraseFile = null;

        // WHEN
        Speaker speaker = new Speaker(entities, taggerDir, phraseFile);

        // THEN
        assertNull(speaker.getTagger());
    }

    public static Speaker getSpeaker() {
        Speaker speaker = null;
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
            speaker = new Speaker(entMap, (String) null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return speaker;
    }
}