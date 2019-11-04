package com.quantxt.nlp.search;

import com.quantxt.trie.Emit;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class QTSearchableTest {

    private static QTSearchable qtSearchable;
    private static boolean setUpIsDone = false;


    @BeforeClass
    public static void init() {
        if (setUpIsDone) {
            return;
        }
        try {
            ArrayList<DictItm> dictItms_1 = new ArrayList<>();
            dictItms_1.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc."));
            dictItms_1.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon" ));
            dictItms_1.add(new DictItm("Amazon Inc.", "Amazon Inc."));

            ArrayList<DictItm> dictItms_2 = new ArrayList<>();
            dictItms_2.add(new DictItm("Director","Director" ));
            dictItms_2.add(new DictItm("Senior Director", "Senior Director"));

            Map<String, List<DictItm>> entMap = new HashMap<>();
            entMap.put("Company", dictItms_1);
            entMap.put("Title", dictItms_2);

            // synonyms;
            ArrayList<String> synonym_pairs = new ArrayList<>();
            synonym_pairs.add("Inc\tinciobi");
            synonym_pairs.add("Inc\tcorporate");
            Dictionary dictionary = new Dictionary(entMap);
            qtSearchable = new QTSearchable(dictionary, null, synonym_pairs,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STANDARD);
            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Test
    public void testParseNames() {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";

        // WHEN
        Map<String, Collection<Emit>> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon Inc");

    }

    @Test
    public void testSynonymNotStemable()  {
        // GIVEN
        String str = "Amazon inciobi reported a gain on his earnings.";

        // WHEN
        Map<String, Collection<Emit>> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon inciobi");

    }

    @Test
    public void testSynonymStemable() {
        // GIVEN
        String str = "Amazon corporate reported a gain on his earnings.";

        // WHEN
        Map<String, Collection<Emit>> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon corporate");

    }
}
