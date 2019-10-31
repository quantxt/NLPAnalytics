package com.quantxt.nlp.search;

import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.trie.Emit;
import com.quantxt.types.DictItm;
import com.quantxt.types.Entity;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by matin on 30/10/2019.
 */

public class QTAdvancedSearchTest {

    private static QTAdvancedSearch qtAdvancedSearch;
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
            ArrayList<String> synonymMap = new ArrayList<>();
            synonymMap.add("Inc\tinciobi");
            synonymMap.add("Inc\tcorporate");
            qtAdvancedSearch = new QTAdvancedSearch(null, synonymMap);
            qtAdvancedSearch.init(entMap);
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
        Map<String, Collection<Emit>> result = qtAdvancedSearch.search(str);

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
        Map<String, Collection<Emit>> result = qtAdvancedSearch.search(str);

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
        Map<String, Collection<Emit>> result = qtAdvancedSearch.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.get("Company").size() == 1);
        assertEquals(result.get("Company").iterator().next().getKeyword(), "Amazon corporate");

    }
}
