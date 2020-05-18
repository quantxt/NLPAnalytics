package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.pattern.PatternCaptureGroupTokenFilter;
import org.apache.lucene.analysis.pattern.PatternReplaceFilter;
import org.apache.lucene.analysis.pattern.PatternTokenizer;
import org.apache.lucene.analysis.pattern.SimplePatternSplitTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.search.spans.SpanQuery;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class SearchUtilsTest {

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

            Map<String, List<DictItm>> entMap = new HashMap<>();
            entMap.put("Dict_Test", dictItms);

            // synonyms;
            ArrayList<String> synonym_pairs = new ArrayList<>();
            synonym_pairs.add("ert\tearning");
            synonym_pairs.add("gain\tprofit");
            Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
            qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, synonym_pairs, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STEM);

            qtSearchable_fuzzy = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.LETTER);
            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void parseTermsQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a profit on his earnings.";

        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Profit"));
        assertTrue(res.get(0).getKeyword().equals("profit"));
    }


    @Test
    public void parseTermsSynonymQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";

        // WHEN
        SpanQuery result = SearchUtils.getSpanQuery(qtSearchable.docSearchFldMap.get("Dict_Test").get(0).getSearch_analyzer(),
                "DUMMY_FIELD", "report gain", 1, false, true);

        assertEquals(result.toString(), "spanNear([DUMMY_FIELD:report, spanOr([DUMMY_FIELD:gain, DUMMY_FIELD:profit])], 1, true)");
        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Profit"));
        assertTrue(res.get(0).getKeyword().equals("gain"));
    }

    @Test
    @Ignore
    public void parseTermsFuzzy1EditQuery() throws IOException {
        // GIVEN
        String str = "Amazon Inc. reported a profti on his earnings.";
        List<QTMatch> res = qtSearchable_fuzzy.search(str);
        System.out.println(res.size() + " ");
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Profit"));
        assertTrue(res.get(0).getKeyword().equals("profti"));
    }

    @Ignore
    @Test
    public void parseTermsFuzzySynonymEditQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a gain on his earnings.";

        List<QTMatch> res = qtSearchable_fuzzy.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Profit"));
        assertTrue(res.get(0).getKeyword().equals("profti"));
    }


    @Test
    public void OOVCoverage() {
        // GIVEN
        String str = "I need tea";

        // WHEN
        Map<String, Map<String, Long>> stas = qtSearchable.getTermCoverage(str);

        assertTrue(stas.get("dicttest.stem").size()==3);
        assertTrue(stas.get("dicttest.stem").get("tea")==1L);
    }


    @Test
    public void parseMultiTermsQuery() {
        // GIVEN
        String query_Dsl = "DUMMY_FIELD.exact:report listed DUMMY_FIELD.exact:profit";

        // WHEN
        SpanQuery result = SearchUtils.parse(query_Dsl,
                "DUMMY_FIELD.exact",
         1,
        new AtomicInteger(0),
        false, false,true);

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

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Amazon_typo", dictItms);

        Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);


        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Amazon"));
    }


    @Test
    public void SimpleTokenizer_Unordered_v1() {
        // GIVEN
        String str = "Inc. Amazon reported a profit on his earnings. Inc";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc." ));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Amazon_unordered", dictItms);

        Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.SIMPLE);


        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Amazon"));
    }


    @Test
    public void Letter_Tokenizer_v1() {
        // GIVEN
        String str = "AmazonInc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc." ));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Amazon_typo", dictItms);

        Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);


        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Amazon"));
    }

    @Test
    public void Letter_Tokenizer_v2() {
        // GIVEN
        String str = "AmzaonInc. reported a profit on his earnings.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Amazon", "Amazon Inc" ));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Amazon_typo", dictItms);

        Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.FUZZY_SPAN, DictSearch.AnalyzType.LETTER);


        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Amazon"));
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

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Business", dictItms);

        Dictionary dictionary = new Dictionary("SearchUtilsTest", entMap);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STEM);

        List<QTMatch> res = qtSearchable.search(result.toString("UTF-8"));
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Business"));
    }
}
