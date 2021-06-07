package com.quantxt.nlp.search;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.nlp.tokenizer.QLetterTokenizer;
import com.quantxt.types.ExtInterval;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
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
            ArrayList<DictItm> dictItms = new ArrayList<>();
            dictItms.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc."));
            dictItms.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon" ));
            dictItms.add(new DictItm("Amazon Inc.", "Amazon Inc."));

            dictItms.add(new DictItm("Director","Director" ));
            dictItms.add(new DictItm("Senior Director", "Senior Director"));

            // synonyms;
            ArrayList<String> synonym_pairs = new ArrayList<>();
            synonym_pairs.add("Inc\tinciobi");
            synonym_pairs.add("Inc\tcorporate");
            Dictionary dictionary = new Dictionary(null, "Company", dictItms);
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
        List<ExtInterval> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        //TODO: this should be Amazon Inc.  with the dot
        assertEquals(result.get(0).getDict_name(), "Company");
        assertEquals(result.get(0).getCategory(), "Amazon Inc.");
        String matchedStr = str.substring(result.get(0).getStart(), result.get(0).getEnd());
        assertEquals(matchedStr, "Amazon Inc");

    }

    @Test
    public void testSynonymNotStemable()  {
        // GIVEN
        String str = "Amazon inciobi reported a gain on his earnings.";

        // WHEN
        List<ExtInterval> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        assertEquals(result.get(0).getStr(), "Amazon inciobi");

    }

    @Test
    public void testSynonymStemable() {
        // GIVEN
        String str = "Amazon corporate reported a gain on his earnings.";

        // WHEN
        List<ExtInterval> result = qtSearchable.search(str);

        // THEN
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.size() == 1);
        assertEquals(result.get(0).getStr(), "Amazon corporate");

    }

    @Test
    public void testStopWord() {
        try {
            ArrayList<DictItm> dictItms = new ArrayList<>();
            dictItms.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc."));
            dictItms.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon"));
            dictItms.add(new DictItm("Amazon Inc.", "Amazon Inc."));

            // synonyms;
            ArrayList<String> stopword = new ArrayList<>();
            stopword.add("inc");
            Dictionary dictionary = new Dictionary(null, "Company", dictItms);
            QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, stopword,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STANDARD);
            // GIVEN
            String str = "Gilead Sciences, Inc. reported a gain on his earnings.";

            // WHEN
            List<ExtInterval> result = qtSearchable.search(str);

            // THEN
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.size() == 1);
           assertEquals(result.get(0).getStr(), "Gilead Sciences");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    @Ignore
    public void testStopWordList() {
        try {
            ArrayList<DictItm> dictItms = new ArrayList<>();
            dictItms.add(new DictItm("CONSTRUCTION TYPE Fire Resistive", "CONSTRUCTION TYPE Fire Resistive"));

            String [] stopword = new String [] {"distance", "to", "hydrant", "district", "code", "stat",
                    "number", "prot" ,"cl" , "#", "stories", "basm'ts", "yr", "built", "total", "area"};

            Dictionary dictionary = new Dictionary(null, "Construction", dictItms);
            QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, Arrays.asList(stopword),
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STANDARD);
            // GIVEN
            String str = "CONSTRUCTION TYPE DISTANCE TO HYDRANT STAT DISTRICT CODE NUMBER PROT CL # STORIES # BASM'TS YR BUILT TOTAL AREA Fire Resistive";

            // WHEN
            List<ExtInterval> result = qtSearchable.search(str);

            // THEN
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertTrue(result.size() == 1);
            assertEquals(result.get(0).getCategory(), "Construction");
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    @Test
    public void test_weather()  {
        // GIVEN
        String str = "Accordingly, we are subject to risks, including labor disputes, inclement weather, natural disasters, cybersecurity attacks, possible acts of terrorism, availability of shipping containers, and increased security restrictions associated with such carriers’ ability to provide delivery services to meet our shipping needs.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("weather conditions","Weather"));
        dictItms.add(new DictItm("climate change","Weather"));
        dictItms.add(new DictItm("global warming","Weather"));

        Dictionary dictionary = new Dictionary(null, "Weather", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, null, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STANDARD);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        // THEN
        assertNotNull(doc.getValues());

    }

    @Test
    public void test_str_special_chars() {
        String srch_str1 = "This is a super \"deal [good deal] ~amzing~ very:special very!very";
        String srch_str2 = "I need ||rire and || rire  +hrlpe";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("V1", "very:special"));
        dictItms.add(new DictItm("V2", "super \"deal"));
        dictItms.add(new DictItm("V3", "||rire"));
        dictItms.add(new DictItm("V4", "|| rire"));
        dictItms.add(new DictItm("V5", "[good deal]"));
        dictItms.add(new DictItm("V6", "~amzing~"));
        dictItms.add(new DictItm("V7", "very!very"));
        dictItms.add(new DictItm("V8", "+hrlpe"));


        Dictionary dictionary = new Dictionary(null, "SPCH", dictItms);
        List<DictSearch> searchableList = new ArrayList<>();
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.WHITESPACE);
        searchableList.add(qtSearchable);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc1 = new ENDocumentInfo("", srch_str1, helper);
        helper.extract(doc1, searchableList, false, "");

        ENDocumentInfo doc2 = new ENDocumentInfo("", srch_str2, helper);
        helper.extract(doc2, searchableList, false, "");

        // THEN
        assertNotNull(doc1.getValues());
        assertNotNull(doc2.getValues());
        assertTrue(doc1.getValues().get(0).getCategory().equals("V2"));
        assertTrue(doc1.getValues().get(1).getCategory().equals("V5"));
        assertTrue(doc1.getValues().get(2).getCategory().equals("V6"));
        assertTrue(doc1.getValues().get(3).getCategory().equals("V1"));
        assertTrue(doc1.getValues().get(4).getCategory().equals("V7"));


        assertTrue(doc2.getValues().get(0).getCategory().equals("V3"));
        assertTrue(doc2.getValues().get(1).getCategory().equals("V4"));
        assertTrue(doc2.getValues().get(2).getCategory().equals("V8"));

    }

    @Test
    public void test_str_unicode() {
        String srch_str1 = "This is a good \uF06E $100,000,001 - $500 million ";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("V1", "\uF06E $100,000,001"));


        Dictionary dictionary = new Dictionary(null, "SPCH", dictItms);
        List<DictSearch> searchableList = new ArrayList<>();
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.WHITESPACE);
        searchableList.add(qtSearchable);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc1 = new ENDocumentInfo("", srch_str1, helper);
        helper.extract(doc1, searchableList, false, "");


        // THEN
        assertNotNull(doc1.getValues());
        assertTrue(doc1.getValues().get(0).getCategory().equals("V1"));

    }

    @Test
    public void test_str_checkbox_simple() {
        String srch_str1 = "This is a good \u2612 $100,000,001 - $500 Million";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("V1", "\u2612 $100,000,001 $500 million"));

        Dictionary dictionary = new Dictionary(null, "SPCH", dictItms);
        List<DictSearch> searchableList = new ArrayList<>();
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);
        searchableList.add(qtSearchable);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc1 = new ENDocumentInfo("", srch_str1, helper);
        helper.extract(doc1, searchableList, false, "");

        // THEN
        assertNotNull(doc1.getValues());
        assertTrue(doc1.getValues().get(0).getCategory().equals("V1"));

    }

    @Test
    public void test_unicode_str() {
        String srch_str1 = "☒ $10,000,000,001-$50 billion";
        String srch_str2 = "we found \uF06E $100,000,001 - $500 million and it `is a lot of money";
        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("500M", "\uF06E $100,000,001 - $500 million"));
        dictItms.add(new DictItm("100M", "\uF06E $50,000,001 - $100 million"));
        dictItms.add(new DictItm("50K", "\uDBFF\uDC00$0 - $50,000"));
        dictItms.add(new DictItm("50B", "☒ $10,000,000,001-$50 billion"));

        Dictionary dictionary = new Dictionary(null, "Occupancy", dictItms);
        List<DictSearch> searchableList = new ArrayList<>();
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.WHITESPACE);
        searchableList.add(qtSearchable);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc1 = new ENDocumentInfo("", srch_str1, helper);
        helper.extract(doc1, searchableList, false, "");

        ENDocumentInfo doc2 = new ENDocumentInfo("", srch_str2, helper);
        helper.extract(doc2, searchableList, false, "");

        // THEN
        assertNotNull(doc1.getValues());
        assertNotNull(doc2.getValues());

    }
}
