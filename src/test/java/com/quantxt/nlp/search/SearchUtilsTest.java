package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.types.ExtInterval;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.search.spans.SpanQuery;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quantxt.nlp.search.SearchUtils.getSpanQuery;
import static org.junit.Assert.*;

public class SearchUtilsTest {

    final private static Logger logger = LoggerFactory.getLogger(SearchUtilsTest.class);


    private static QTSearchable qtSearchable;
    private static QTSearchable qtSearchable_fuzzy;
    private static boolean setUpIsDone = false;

    @BeforeClass
    public static void init() {
        if (setUpIsDone) {
            return;
        }
        try {
            ArrayList<DictItm> dictItms = new ArrayList<>();
            dictItms.add(new DictItm("Profit", "profit" ));
            dictItms.add(new DictItm("Hot Drink", "coffee" ));
            dictItms.add(new DictItm("Hot Drink", "tea" ));
            dictItms.add(new DictItm("Cold Drink", "water" ));
            dictItms.add(new DictItm("Amazon Inc", "Amazon Inc"));

            // synonyms;
            ArrayList<String> synonym_pairs = new ArrayList<>();
            synonym_pairs.add("ert\tearning");
            synonym_pairs.add("gain\tprofit");
            Dictionary dictionary = new Dictionary(null, "Dict_Test", dictItms);
            qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, synonym_pairs, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STEM);

            qtSearchable_fuzzy = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                    DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);

            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void parseTermsQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a profit on his earnings.";

        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 2);
        assertTrue(res.get(1).getCategory().equals("Profit"));
        assertTrue(res.get(1).getStr().equals("profit"));
    }


    @Test
    public void parseTermsSynonymQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";

        // WHEN
        SpanQuery result = getSpanQuery(qtSearchable.docSearchFldList.get(0).getSearch_analyzer(),
                "DUMMY_FIELD", "report gain", 1, false, true, true);

        assertEquals(result.toString(), "spanNear([DUMMY_FIELD:report, spanOr([DUMMY_FIELD:gain, DUMMY_FIELD:profit])], 1, true)");
        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 2);
        assertTrue(res.get(1).getCategory().equals("Profit"));
        String matchedStr = str.substring(res.get(1).getStart(), res.get(1).getEnd());
        assertTrue(matchedStr.equals("gain"));
    }

    @Test
    public void parseTermsFuzzy1EditQuery() {
        // GIVEN
        String str = "AmazonInc. reported a profit on his earnings.";
        List<ExtInterval> res = qtSearchable_fuzzy.search(str);
        assertTrue(res.size() == 2);
        assertTrue(res.get(0).getCategory().equals("Amazon Inc"));
        assertTrue(res.get(0).getStr().equals("AmazonInc"));
    }

    @Test
    public void parseTermsFuzzyMiddlePharse() {
        // GIVEN
        // we should NOT match on profit that is in middle of `wordstartproftiwordend`
        String str = "Amazon Inc. reported a wordstartproftiwordend on his earnings.";
        List<ExtInterval> res = qtSearchable_fuzzy.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon Inc"));
        assertTrue(res.get(0).getStr().equals("Amazon Inc"));
    }

    @Ignore
    @Test
    public void parseTermsFuzzySynonymEditQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";

        List<ExtInterval> res = qtSearchable_fuzzy.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Profit"));
        assertTrue(res.get(0).getStr().equals("profti"));
    }


    @Test
    public void OOVCoverage() {
        // GIVEN
        String str = "I need tea";

        // WHEN
        Map<String, Map<String, Long>> stas = qtSearchable.getTermCoverage(str);

        assertTrue(stas.get("dict_test.stem").size()==3);
        assertTrue(stas.get("dict_test.stem").get("tea")==1L);
    }


    @Test
    public void parseMultiTermsQuery() {
        // GIVEN
        String query_Dsl = "DUMMY_FIELD.exact:report listed DUMMY_FIELD.exact:profit";

        // WHEN
        SpanQuery result = SearchUtils.parse(query_Dsl,
                "DUMMY_FIELD.exact", 1, new AtomicInteger(0),
        true, false,true);

        assertEquals(result.toString(), "spanNear([DUMMY_FIELD.exact:report listed, DUMMY_FIELD.exact:profit], 1, true)");
    }

    @Test
    public void parseMultiTermsEndofStrQuery() {
        // GIVEN
        String query_Dsl = "DUMMY_FIELD.exact:report listed";

        // WHEN
        SpanQuery result = SearchUtils.parse(query_Dsl,
                "DUMMY_FIELD.exact",
                1,
                new AtomicInteger(0),
                false, false, true);

        assertEquals(result.toString(), "DUMMY_FIELD.exact:report listed");
    }

    @Test
    public void SimpleTokenizer_v1() {
        // GIVEN
        String str = "Amazon Inc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc." ));

        Dictionary dictionary = new Dictionary(null, "Amazon_typo", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon"));
    }


    @Test
    public void SimpleTokenizer_Unordered_v1() {
        // GIVEN
        String str = "Inc. Amazon reported a profit on his earnings. Inc";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc." ));

        Dictionary dictionary = new Dictionary(null, "Amazon_unordered", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.SIMPLE);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon"));
    }


    @Test
    public void Letter_Tokenizer_v1() {
        // GIVEN
        String str = "AmazonInc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc." ));

        Dictionary dictionary = new Dictionary(null, "Amazon_typo", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon"));
    }

    @Test
    public void Letter_Tokenizer_v2() {
        // GIVEN
        String str = "AmzaonInc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc" ));

        Dictionary dictionary = new Dictionary(null, "Amazon_typo", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon"));
    }

    @Test
    public void NewLine_v1() throws IOException {
        // GIVEN
        InputStream inputStream = getClass().getResourceAsStream("/long_text_v2.txt");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }


        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Business", "Item 1. Business" ));

        Dictionary dictionary = new Dictionary(null, "Business", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STEM);

        List<ExtInterval> res = qtSearchable.search(result.toString("UTF-8"));
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Business"));
    }
}
