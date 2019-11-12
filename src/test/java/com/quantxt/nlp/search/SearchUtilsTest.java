package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.search.spans.SpanQuery;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchUtilsTest {

    private static QTSearchable qtSearchable;
    private static QTSearchable qtSearchable_exact;
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
            Dictionary dictionary = new Dictionary(entMap);
            qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, synonym_pairs, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.STEM);
            qtSearchable_exact = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, synonym_pairs, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.EXACT_CI);
            qtSearchable_fuzzy = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                    DictSearch.Mode.ORDERED_SPAN, DictSearch.AnalyzType.SIMPLE);
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
                "DUMMY_FIELD", "report gain", 1, 5, false, true);

        assertEquals(result.toString(), "spanNear([DUMMY_FIELD:report, spanOr([DUMMY_FIELD:gain, DUMMY_FIELD:profit])], 1, true)");
        List<QTMatch> res = qtSearchable.search(str);
        assertTrue(res.size() == 1);
        assertTrue(res.get(0).getCustomData().equals("Profit"));
        assertTrue(res.get(0).getKeyword().equals("gain"));
    }

    @Test
    public void parseTermsFuzzy1EditQuery() {
        // GIVEN
        String str = "Amazon Inc. reported a profti on his earnings.";

        List<QTMatch> res = qtSearchable_fuzzy.search(str);
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

}
