package com.quantxt.QTDocument;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import com.quantxt.helper.types.ExtIntervalSimple;
import com.quantxt.nlp.entity.QTValueNumber;
import com.quantxt.nlp.search.QTAdvancedSearch;
import com.quantxt.types.DictItm;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.types.Entity;
import com.quantxt.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.quantxt.helper.types.QTField.QTFieldType.*;
import static org.junit.Assert.*;

/**
 * Created by matin on 10/10/17.
 */

public class ENDocumentInfoTest {

    private static Logger logger = LoggerFactory.getLogger(ENDocumentInfoTest.class);

    private static QTAdvancedSearch qtAdvancedSearch;
    private static ENDocumentHelper helper;
    private static boolean setUpIsDone = false;
    private static Map<String, List<DictItm>> global_dicts = new HashMap<>();

    @BeforeClass
    public static void init() {
        if (setUpIsDone) {
            return;
        }
        try {
            ArrayList<DictItm> d1_items = new ArrayList<>();
            d1_items.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc.", "Gilead Sciences , Inc."));
            d1_items.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon"));

            ArrayList<DictItm> d2_items = new ArrayList<>();
            d2_items.add(new DictItm("Director", "Director"));
            d2_items.add(new DictItm("Senior Director", "Senior Director"));

            ArrayList<DictItm> d3_items = new ArrayList<>();
            d3_items.add(new DictItm("10 Year Exposure", "10 Year Exposure", "10 yr", "ten year"));


            global_dicts.put("Company" , d1_items);
            global_dicts.put("Title" , d2_items);
            global_dicts.put("Exposure" , d3_items);
            helper = new ENDocumentHelper();
            qtAdvancedSearch = new QTAdvancedSearch();
            qtAdvancedSearch.init(global_dicts);
            setUpIsDone = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void largeFilePatternDetection() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/long_text.txt");
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("FEIN OR SOC SEC", "FEIN OR SOC SEC"));

            Map<String, List<DictItm>> dicts = new HashMap<>();
            dicts.put("FEIN" , items);

            Pattern keyPaddingPattern = Pattern.compile("[\\s\\#]+");

            QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
            qtAdvancedSearch.init(dicts);
            qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
            qtAdvancedSearch.setSearch_distance(130);
            qtAdvancedSearch.setValType(STRING);
            qtAdvancedSearch.setPattern(Pattern.compile("(\\d+\\-\\d+)"));
            qtAdvancedSearch.setGroups(new int []{1});
            QTDocument doc = new ENDocumentInfo("", result.toString("UTF-8"), helper);
            doc.extractKeyValues(qtAdvancedSearch, "");

            doc.convertValues2titleTable();
            // THEN
            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>FEIN OR SOC SEC</td><td>59-2051726</td></tr></table>");


        } catch (Exception e){
            e.printStackTrace();
        }

    }


    @Test
    public void testNounVerbPh1() {
        String str = "Gilead Sciences , Inc. told to reuters reporters.";
        QTDocument doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2() {
        String str = "Amazon Inc. reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3() {
        String str = "Amazon reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4() {
        String str = "Amazon Corp reported a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5() {
        String str = "Amazon LLC announced a gain on his earnings .";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertEquals(entityMap.get("Company").iterator().next(), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh6() {
        String str = "He works as a high rank Senior Director in Amazon";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        ArrayList<QTDocument> docs = doc.extractEntityMentions(qtAdvancedSearch, true, false, false);
        Map<String, LinkedHashSet<String>> entityMap = docs.get(0).getEntity();
        Assert.assertTrue(entityMap.get("Title").contains("Senior Director"));
    }

    @Test
    public void findSpan1() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("a");
        tokens.add("research");
        ExtIntervalSimple spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "a research");
    }

    @Test
    public void findSpan2() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("Gilead");
        tokens.add("Sciences");
        ExtIntervalSimple spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "Gilead Sciences");
    }

    @Test
    public void findSpan3() {
        String str = "Бывший мэр Даугавпилса рассказал о схемах местных депутатов, "
                + "чтобы успешнее осваивать деньги из городской казны и еврофондов в собственные карманы .";
        List<String> tokens = new ArrayList<>();
        tokens.add("Бывший");
        tokens.add("мэр");
        tokens.add("Даугавпилса");
        ExtIntervalSimple spans = StringUtil.findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "Бывший мэр Даугавпилса");
    }

    @Test
    public void testDocumentChildsNotEmptyAndHasEqualProperties() {
        // GIVEN
        String str = "Light behaves in some respects like particles and in "
                + "other respects like waves. Matter—the \"stuff\" of the "
                + "universe consisting of particles such as electrons and they "
                + "wavelike behavior too. Some light sources, such as neon "
                + "lights, give off only certain frequencies of light.";
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helper);
        doc.setLink("link");

        // WHEN
        List<QTDocument> childs = doc.getChilds(false);

        // THEN
        assertNotNull(childs);
        assertFalse(childs.isEmpty());
        assertEquals(childs.size(), 3);
        assertEquals(childs.get(0).getTitle(), "Light behaves in some respects like particles and in other respects like waves.");
        assertEquals(childs.get(0).getLink(), doc.getLink());
        assertEquals(childs.get(0).getLanguage(), doc.getLanguage());
    }

    @Test
    public void findExppsure1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6%";
        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)|[\\:\\,;]+");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, str);
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td></tr></table>");
    }


    @Test
    public void findExppsureTable1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6% 6.4% 9.8%";
        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)|[\\:\\,;]+");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, str);
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable2() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr: 5.6% 6.4% 9.8%";

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)|[\\:\\,;]+");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable3() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% 6.4% 9.8%";

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable4() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% -6.4% 9.8%";

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>-6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable5() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% -6.4% 9.8%";

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.init(global_dicts);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>-6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findDate1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% -6.4% 9.8%";

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(global_dicts);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>-6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findSpane1() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Sovereign" ,"Sovereign"));
        dictItms.add(new DictItm("Quasi Sovereign" , "Quasi Sovereign"));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Company" , dictItms);

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Sovereign</td><td>68.0</td></tr><tr><td>Sovereign</td><td>2.0</td></tr><tr><td>Quasi Sovereign</td><td>10.0</td></tr><tr><td>Quasi Sovereign</td><td>0.0</td></tr></table>");

    }

    @Test
    public void findSpane2() {
        String str =
                "The effects of this change are applied retrospectively and are provided in the Reconciliation of Non-GAAP Financial Measures to " +
                        "GAAP Financial Measures tables. 2 CUSTOMER METRICS Total Branded Postpaid Net Additions " +
                        "Branded Postpaid Customers (in thousands) Branded postpaid phone net customer additions " +
                        "were 774,000 in Q3 2018, compared to 686,000 in Q2 2018 and 595,000 in Q3 2017.";
        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Branded postpaid net customer additions" , "Branded postpaid net customer additions", "Branded postpaid net customer additions were"));
        dictItms.add(new DictItm("Branded postpaid phone net customer additions" , "Branded postpaid phone net customer additions", "Branded postpaid phone net customer additions were"));
        dictItms.add(new DictItm("net customer additions" , "net customer additions", "net customer additions"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Company" , dictItms);

        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Branded postpaid phone net customer additions</td><td>7.74E8</td></tr></table>");

    }

    @Test
    public void findSpane3() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Net market value" , "Net market value", "Net market value, as of"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Net value" , dictItms);
        Pattern keyPaddingPattern = Pattern.compile("\\s{0,3}\\(\\d+\\)");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(15);
        qtAdvancedSearch.setValType(DATETIME);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);

        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Net market value</td><td>10/31/18</td></tr></table>");

    }

    @Test
    public void findSpane4() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        ArrayList<DictItm> dictItms_1 = new ArrayList<>();
        dictItms_1.add(new DictItm("Returns" , "Returns", "returned"));
        Map<String, List<DictItm>> entMap1 = new HashMap<>();
        entMap1.put("Fund Performance" , dictItms_1);
        QTAdvancedSearch qtAdvancedSearch_1 = new QTAdvancedSearch();
        qtAdvancedSearch_1.init(entMap1);

        ArrayList<DictItm> dictItms_2 = new ArrayList<>();
        dictItms_2.add(new DictItm("Net market value" , "Net market value", "Net market value, as of"));
        Map<String, List<DictItm>> entMap2 = new HashMap<>();
        entMap2.put("Net value" , dictItms_2);
        QTAdvancedSearch qtAdvancedSearch_2 = new QTAdvancedSearch();
        qtAdvancedSearch_2.init(entMap2);

        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        Pattern regex = Pattern.compile("returned ([\\-+]?[\\d\\.]+)%");

        Pattern regexPad = Pattern.compile("\\s+(\\(\\d+\\)|[\\:\\,;]+)");

        qtAdvancedSearch_1.setPattern(regex);
        qtAdvancedSearch_1.setGroups(new int[] {1});

        qtAdvancedSearch_1.setValType(STRING);
        qtAdvancedSearch_1.setKeyPadding(regexPad);
        qtAdvancedSearch_1.setSearch_distance(25);

        doc.extractKeyValues(qtAdvancedSearch_1, "");

        qtAdvancedSearch_2.setKeyPadding(regexPad);
        qtAdvancedSearch_2.setSearch_distance(15);
        qtAdvancedSearch_2.setValType(DATETIME);
        doc.extractKeyValues(qtAdvancedSearch_2, "");
        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);

        logger.error(doc.getTitle());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Net market value</td><td>10/31/18</td></tr><tr><td>Returns</td><td>-2.67</td></tr></table>");

    }

    @Test
    public void findSpane5() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Returns" , "Returns", "returned"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Fund Performance" , dictItms);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);

        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setValType(STRING);

        Pattern regex = Pattern.compile("returned ([\\-+]?[\\d\\.]+)%");
        Pattern regexPad = Pattern.compile("\\s+(\\(\\d+\\)|[\\:\\,;]+)");

        qtAdvancedSearch.setPattern(regex);
        qtAdvancedSearch.setGroups(new int[] {1});
        qtAdvancedSearch.setKeyPadding(regexPad);
        qtAdvancedSearch.setSearch_distance(25);

        doc.extractKeyValues(qtAdvancedSearch, "");
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Returns</td><td>-2.67</td></tr></table>");

    }

    @Test
    public void stringUnitTest1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6 million";

        Pattern keyPaddingPattern = Pattern.compile("\\s+\\(\\d+\\)|[\\:\\,;]+");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(global_dicts);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, "");
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5600000.0</td></tr></table>");

    }

    @Test
    public void stringUnitTest2() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6\n" +
        "market";
        Pattern keyPaddingPattern = Pattern.compile("\\s+\\(\\d+\\)|[\\:\\,;]+");

        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(global_dicts);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);

        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td></tr></table>");

    }

    @Test
    public void currencyTest1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : $ 5.6 million";
        List<ExtIntervalSimple> intervals= new ArrayList<>();
        QTValueNumber.detect(str, str, intervals);
        // THEN
        assertTrue(intervals.size() == 2);
        assertTrue(intervals.get(1).getType() == MONEY);
    }

    @Test
    public void thousandsInTableHeader() {
        // GIVEN
        String str = "Condensed Consolidated Balance Sheets \n" +
                "(In thousands) \n" +
                "March 31,2009 \n" +
                "December 31,2008 \n" +
                "(Unaudited) \n" +
                "(Note) \n" +
                "Assets \n" +
                "Current assets: \n" +
                "Cash and cash equivalents \n" +
                "$ \n" +
                "62,458 \n" +
                "$ \n" +
                "73,329 \n";


        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Cash and cash equivalents" , "Cash and cash equivalents"));
        dictItms.add(new DictItm("Prepaid expenses and other current assets" ,"Prepaid expenses and other current assets"));
        dictItms.add(new DictItm("Warrant liability" , "Warrant liability"));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Company" , dictItms);
        Pattern keyPaddingPattern = Pattern.compile("\\s+(\\(\\d+\\)|[\\:\\,;\\$]+)");

        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);

        doc.extractKeyValues(qtAdvancedSearch, "");
        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Cash and cash equivalents</td><td>6.2458E7</td><td>7.3329E7</td></tr></table>");

    }

    @Test
    public void percentInNumber() {
        // GIVEN
        String str = "Share-based compensation (benefit) expense (in thousands)\n" +
                "(54.6 \n" +
                ") \n" +
                "102.7 \n" +
                "(153.2 \n" +
                ")% \n" +
                "Total selling, general and administrative expense \n" +
                "$ \n" +
                "177.3 \n" +
                "$ \n" +
                "304.0 \n" +
                "(41.7 \n" +
                ")% ";


        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));

        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Company" , dictItms);
        Pattern keyPaddingPattern = Pattern.compile("\\s+\\(\\d+\\)|[\\:\\,;]+");
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(entMap);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);
        qtAdvancedSearch.setKeyPadding(keyPaddingPattern);

        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);

        doc.extractKeyValues(qtAdvancedSearch, "");

        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177300.0</td><td>304000.0</td><td>41.7</td></tr></table>")
        ;
    }

    public void numberParantesis() {
        // GIVEN
        String str = "Share-based compensation (benefit) expense (in thousands)\n" +
                "(54.6 \n" +
                ") \n" +
                "102.7 \n" +
                "(153.2 \n" +
                ")% \n" +
                "Total selling, general and administrative expense(1) \n" +
                "$ \n" +
                "177.3 \n" +
                "$ \n" +
                "304.0 \n" +
                "(41.7 \n" +
                ")% ";


        ArrayList<Entity> entityArray1 = new ArrayList<>();

        entityArray1.add(new Entity("Total selling, general and administrative expense" , new String[]{"Total selling, general and administrative expense"} , true));

        Map<String, Entity[]> entMap = new HashMap<>();
        entMap.put("Company" , entityArray1.toArray(new Entity[entityArray1.size()]));
        QTAdvancedSearch qtAdvancedSearch = new QTAdvancedSearch();
        qtAdvancedSearch.init(global_dicts);
        qtAdvancedSearch.setSearch_distance(5);
        qtAdvancedSearch.setValType(DOUBLE);

        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        doc.extractKeyValues(qtAdvancedSearch, str);
        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177300.0</td><td>304000.0</td><td>41.7</td></tr></table>")
        ;
    }
}
