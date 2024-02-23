package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.DictItm;
import com.quantxt.model.DictSearch;
import com.quantxt.model.Dictionary;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.search.Query;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.quantxt.types.QSpan;

import static com.quantxt.nlp.search.SearchUtils.getMatchAllQuery;
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
            qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, synonym_pairs, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STEM);

            qtSearchable_fuzzy = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                    DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);

            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @Ignore
    public void testtest() throws IOException {
        // GIVEN
   //     String str = "CID132 After any administrative expenses are paid, no fu CID132 $0 - $50,000  CID134 $1,000,001 -";

        InputStream inputStream = new FileInputStream(new File("/Users/matin/fff.txt"));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        String str = result.toString("UTF-8");

        result = new ByteArrayOutputStream();
        buffer = new byte[1024];
        inputStream = new FileInputStream(new File("/Users/matin/Downloads/2cbdee00-34c8-43c9-a08f-f1bb48bedd2f"));
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        String syn_str = result.toString("UTF-8");
        ArrayList<DictItm> dictItms = new ArrayList<>();
        String [] syn_lines = syn_str.split("\n");
        for (String s : syn_lines){
            String [] p = s.split("\t");
            dictItms.add(new DictItm(p[0], p[1] ));
        }
   //     ArrayList<DictItm> dictItms = new ArrayList<>();
   //     dictItms.add(new DictItm("Check", "☒ $0-$50,000" ));

        // synonyms;
        ArrayList<String> synonym_pairs = new ArrayList<>();
        synonym_pairs.add("$0 - $50,000\t$0-$50,000");
        synonym_pairs.add("CID132\t☒");
        Dictionary dictionary = new Dictionary(null, "Dict_Test", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, synonym_pairs, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.WHITESPACE);

        Query q = getMatchAllQuery(qtSearchable.docSearchFldList.get(0).getMirror_synonym_search_analyzer(), "Dict_Test.whitespace", "☒ $0-$50,000");
        List<ExtInterval> res = qtSearchable.search(str, 0);
        assertTrue(res.size() == 2);
        assertTrue(res.get(1).getCategory().equals("Profit"));
        assertTrue(res.get(1).getStr().equals("profit"));
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
        String str = "Amazon Inc. reported a profit on his earnings.";
        List<ExtInterval> res = qtSearchable_fuzzy.search(str);
        assertTrue(res.size() == 2);
        assertTrue(res.get(0).getCategory().equals("Amazon Inc"));
        assertTrue(res.get(0).getStr().equals("Amazon Inc"));
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
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
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
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.SIMPLE);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon"));
    }


    @Test
    public void Letter_Tokenizer_v1() {
        // GIVEN
        String str = "Amazon1 reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon1", "Amazon" ));

        Dictionary dictionary = new Dictionary(null, "Amazon1", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);


        List<ExtInterval> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Amazon1"));
    }

    @Test
    @Ignore
    public void Letter_Tokenizer_v2() {
        // GIVEN
        String str = "AmzaonInc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazoninc" ));

        Dictionary dictionary = new Dictionary(null, "Amazon_typo", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
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
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STEM);

        List<ExtInterval> res = qtSearchable.search(result.toString("UTF-8"));
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Business"));

    }

    @Test
    public void stopword_position_gap() {
        // GIVEN
        String str = "Amzaon inc reported a very hight profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Profit", "reported hight profit" ));
        List<String> stopwords = new ArrayList<>();
        stopwords.add("a");
        stopwords.add("very");

        Dictionary dictionary = new Dictionary(null, "Amazon_profit", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, stopwords,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);


        List<ExtInterval> res = qtSearchable.search(str, 5);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Profit"));
    }

    @Test
    public void exactMatch_especialChar_v1() {
        // GIVEN
        String str = ": WMG 5152   Total Excl.   GST Amt @              Total Incl.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("GST", "GST amt @" ));

        Dictionary dictionary = new Dictionary(null, "GST", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);


        List<QSpan> res = qtSearchable.search(str, null,0, false);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getExtInterval(false).getCategory().equals("GST"));
    }

    @Test
    public void stem_search_1() {
        // GIVEN
        String str = "Amzaon inc report high profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Profit", "reported high profit" ));


        Dictionary dictionary = new Dictionary(null, "Amazon_profit", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocumentHelper.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STEM);


        List<ExtInterval> res = qtSearchable.search(str, 0);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCategory().equals("Profit"));
    }
}
