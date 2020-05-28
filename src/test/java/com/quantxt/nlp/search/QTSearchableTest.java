package com.quantxt.nlp.search;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.junit.BeforeClass;
import org.junit.Ignore;
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
            Dictionary dictionary = new Dictionary("QTSearchableTest", entMap);
            qtSearchable = new QTSearchable(dictionary, null, synonym_pairs, null,
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
        List<QTMatch> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get(0).getGroup(), "Company");
        assertEquals(result.get(0).getKeyword(), "Amazon Inc");

    }

    @Test
    public void testSynonymNotStemable()  {
        // GIVEN
        String str = "Amazon inciobi reported a gain on his earnings.";

        // WHEN
        List<QTMatch> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        assertEquals(result.get(0).getKeyword(), "Amazon inciobi");

    }

    @Test
    public void testSynonymStemable() {
        // GIVEN
        String str = "Amazon corporate reported a gain on his earnings.";

        // WHEN
        List<QTMatch> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        assertEquals(result.get(0).getKeyword(), "Amazon corporate");

    }

    @Test
    public void testStopWord() {
        try {
            ArrayList<DictItm> dictItms_1 = new ArrayList<>();
            dictItms_1.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc."));
            dictItms_1.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon"));
            dictItms_1.add(new DictItm("Amazon Inc.", "Amazon Inc."));

            Map<String, List<DictItm>> entMap = new HashMap<>();
            entMap.put("Company", dictItms_1);

            // synonyms;
            ArrayList<String> stopword = new ArrayList<>();
            stopword.add("inc");
            Dictionary dictionary = new Dictionary("QTSearchableTest", entMap);
            QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, stopword,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STANDARD);
            // GIVEN
            String str = "Gilead Sciences, Inc. reported a gain on his earnings.";

            // WHEN
            List<QTMatch> result = qtSearchable.search(str);

            // THEN
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.size() == 1);
           assertEquals(result.get(0).getKeyword(), "Gilead Sciences");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    @Ignore
    public void testStopWordList() {
        try {
            ArrayList<DictItm> dictItms_1 = new ArrayList<>();
            dictItms_1.add(new DictItm("CONSTRUCTION TYPE Fire Resistive", "CONSTRUCTION TYPE Fire Resistive"));

            Map<String, List<DictItm>> entMap = new HashMap<>();
            entMap.put("Construction", dictItms_1);

            String [] stopword = new String [] {"distance", "to", "hydrant", "district", "code", "stat",
                    "number", "prot" ,"cl" , "#", "stories", "basm'ts", "yr", "built", "total", "area"};

            Dictionary dictionary = new Dictionary("QTSearchableTest", entMap);
            QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, Arrays.asList(stopword),
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STANDARD);
            // GIVEN
            String str = "CONSTRUCTION TYPE DISTANCE TO HYDRANT STAT DISTRICT CODE NUMBER PROT CL # STORIES # BASM'TS YR BUILT TOTAL AREA Fire Resistive";

            // WHEN
            List<QTMatch> result = qtSearchable.search(str);

            // THEN
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.size() == 1);
            assertEquals(result.get(0).getGroup(), "Construction");
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    @Test
    public void test_weather()  {
        // GIVEN
        String str = "Accordingly, we are subject to risks, including labor disputes, inclement weather, natural disasters, cybersecurity attacks, possible acts of terrorism, availability of shipping containers, and increased security restrictions associated with such carriers’ ability to provide delivery services to meet our shipping needs.";

        ArrayList<DictItm> dictItms_1 = new ArrayList<>();
        dictItms_1.add(new DictItm("weather conditions","Weather"));
        dictItms_1.add(new DictItm("climate change","Weather"));
        dictItms_1.add(new DictItm("global warming","Weather"));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Weather", dictItms_1);

        Dictionary dictionary = new Dictionary("QTSearchableTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STANDARD);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<QTSearchable> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        // THEN
        assertNotNull(doc.getValues());

    }

}
