package com.quantxt.QTDocument;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.types.*;
import com.quantxt.nlp.entity.QTValueNumber;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.Dictionary;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.ENDocumentHelper;

import static com.quantxt.types.QTField.DataType.MONEY;
import static com.quantxt.types.DictSearch.AnalyzType.STEM;
import static com.quantxt.types.Dictionary.ExtractionType.*;
import static com.quantxt.util.NLPUtil.findSpan;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by matin on 10/10/17.
 */

public class ENDocumentInfoTest {

    private static QTSearchable qtSearchable;
    private static ENDocumentHelper helperSentenceDetect;
    private static boolean setUpIsDone = false;
    private static Dictionary global_dict;
    private static Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]*$");
    private static Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");


    @BeforeClass
    public static void init() {
        if (setUpIsDone) {
            return;
        }
        try {
            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("Gilead Sciences, Inc.", "Gilead Sciences, Inc.", "Gilead Sciences , Inc."));
            items.add(new DictItm("Amazon Inc.", "Amazon Inc.", "Amazon"));

            items.add(new DictItm("Director", "Director"));
            items.add(new DictItm("Senior Director", "Senior Director"));

            items.add(new DictItm("10 Year Exposure", "10 Year Exposure", "10 yr", "ten year"));

            global_dict = new Dictionary(null, "Company", items);
            qtSearchable = new QTSearchable(global_dict);
            helperSentenceDetect = new ENDocumentHelper().init();
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

            CommonQTDocumentHelper helper = new ENDocumentHelper();
            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("FEIN OR SOC SEC", "FEIN OR SOC SEC"));

            Pattern keyPaddingPattern = Pattern.compile("^.{20,130}$");

            Dictionary dictionary = new Dictionary(items, null,"FEIN", REGEX,
                    keyPaddingPattern, null, Pattern.compile("(\\d+\\-\\d+)"), new int []{1});
            QTSearchable qtSearchable = new QTSearchable(dictionary);
            QTDocument doc = new ENDocumentInfo("", result.toString("UTF-8"), helper);

            List<DictSearch> qtSearchableList = new ArrayList<>();
            qtSearchableList.add(qtSearchable);
            helper.extract(doc, qtSearchableList, false, "");

            doc.convertValues2titleTable();
            // THEN
            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>FEIN OR SOC SEC</td><td>59-2051726</td></tr></table>");


        } catch (Exception e){
            e.printStackTrace();
        }

    }

    @Test
    public void largeFilePatternDetectionWithPAGE() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/sparse_text.txt");
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("TOTAL AREA 1", "TOTAL AREA"));


            Pattern keyVerticalPaddingPattern = Pattern.compile("^\\s*$");

            Dictionary dictionary_1 = new Dictionary(items, null,"AREA", REGEX,
                    Pattern.compile("^[A-Za-z\\-\\n ]{20,600}$"), Pattern.compile("^ *$"), Pattern.compile("(\\d[,\\d]+\\d|\\d+)"), null);

            Dictionary dictionary_2 = new Dictionary(items, null,"AREA", REGEX,
                    keyVerticalPaddingPattern, null,Pattern.compile("(\\d[,\\d]+\\d|\\d+)"), null);

            QTSearchable qtSearchable = new QTSearchable(dictionary_1);
            QTSearchable qtSearchableVertical = new QTSearchable(dictionary_2);

            CommonQTDocumentHelper helper = new ENDocumentHelper();

            QTDocument doc = new ENDocumentInfo("", result.toString("UTF-8"), helper);

            List<DictSearch> qtSearchableList = new ArrayList<>();
            qtSearchableList.add(qtSearchable);
            helper.extract(doc, qtSearchableList, false, "");

            doc.convertValues2titleTable();

            // THEN
            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>TOTAL AREA 1</td><td>2</td><td>5</td><td>2000</td><td>1,590</td></tr></table>");
            assertEquals(doc.getValues().get(0).getExtIntervalSimples().get(0).getStart(), 354);

            //Check for vertical match: Total area and 1590

            doc = new ENDocumentInfo("", result.toString("UTF-8"), helper);
            List<DictSearch> searchableList = new ArrayList<>();
            searchableList.add(qtSearchableVertical);
            helper.extract(doc, searchableList, true, "");

            doc.convertValues2titleTable();
            // THEN
            assertNotNull(doc.getValues());
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>TOTAL AREA 1</td><td>1,590</td></tr></table>");
            assertEquals(doc.getValues().get(0).getExtIntervalSimples().get(0).getStart(), 491);

        } catch (Exception e){
            e.printStackTrace();
        }

    }

    @Test
    public void largeFilePatternDetectionWithPAGE_2() {
        try {
            InputStream inputStream = getClass().getResourceAsStream("/sparse_text.txt");
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("Area", "TOTAL AREA"));

            Pattern match = Pattern.compile("(^ *[\\d,]+)");

            Dictionary dictionary_1 = new Dictionary(items, null,"Area", REGEX,
                    null, null, match, new int[]{1});


            QTSearchable qtSearchable = new QTSearchable(dictionary_1);

            CommonQTDocumentHelper helper = new ENDocumentHelper();
            List<DictSearch> searchableList = new ArrayList<>();
            searchableList.add(qtSearchable);

            QTDocument doc = new ENDocumentInfo("", result.toString("UTF-8"), helper);
            helper.extract(doc, searchableList, true, "");

            doc.convertValues2titleTable();

            // THEN
            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>Area</td><td>1,590</td></tr></table>");
            assertEquals(doc.getValues().get(0).getExtIntervalSimples().get(0).getStart(), 491);


        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    public void testNounVerbPh1() {
        String str = "Gilead Sciences , Inc. told to reuters reporters.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        String id = qtSearchable.getDictionary().getId();
        Map<String, List<String>> entityMap = doc.getEntity();
        Assert.assertEquals(entityMap.get(id).get(0), "Gilead Sciences, Inc.");
    }

    @Test
    public void testNounVerbPh2() {
        String str = "Amazon Inc. reported a gain on his earnings .";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        Map<String, List<String>> entityMap = doc.getEntity();
        String id = qtSearchable.getDictionary().getId();

        Assert.assertEquals(entityMap.get(id).get(0), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh3() {
        String str = "Amazon reported a gain on his earnings .";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        String id = qtSearchable.getDictionary().getId();

        Map<String, List<String>> entityMap = doc.getEntity();
        Assert.assertEquals(entityMap.get(id).get(0), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh4() {
        String str = "Amazon Corp reported a gain on his earnings .";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        String id = qtSearchable.getDictionary().getId();

        Map<String, List<String>> entityMap = doc.getEntity();
        Assert.assertEquals(entityMap.get(id).get(0), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh5() {
        String str = "Amazon LLC announced a gain on his earnings .";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        String id = qtSearchable.getDictionary().getId();

        Map<String, List<String>> entityMap = doc.getEntity();
        Assert.assertEquals(entityMap.get(id).get(0), "Amazon Inc.");
    }

    @Test
    public void testNounVerbPh6() {
        String str = "He works as a high rank Senior Director in Amazon";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        String id = qtSearchable.getDictionary().getId();

        Map<String, List<String>> entityMap = doc.getEntity();
        Assert.assertTrue(entityMap.get(id).contains("Senior Director"));
    }

    @Test
    public void findSpan1() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("a");
        tokens.add("research");
        ExtIntervalSimple spans = findSpan(str, tokens);
        Assert.assertEquals(str.substring(spans.getStart(), spans.getEnd()), "a research");
    }

    @Test
    public void findSpan2() {
        String str = "Gilead Sciences Company Profile Gilead Sciences, Inc. is a research-based";
        List<String> tokens = new ArrayList<>();
        tokens.add("Gilead");
        tokens.add("Sciences");
        ExtIntervalSimple spans = findSpan(str, tokens);
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
        ExtIntervalSimple spans = findSpan(str, tokens);
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
        ENDocumentInfo doc = new ENDocumentInfo(str, "", helperSentenceDetect);
        doc.setLink("link");

        // WHEN
        List<QTDocument> childs = doc.getChunks(QTDocument.CHUNK.SENTENCE);

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

        Dictionary dictionary = new Dictionary(global_dict.getVocab(),  null,"test", REGEX,
                null, null, Pattern.compile("^[ \\:]+([\\d\\.]+)"), new int [] {1});

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td></tr></table>");
    }


    @Test
    public void findExppsureTable1() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6% 6.4% 9.8%";
        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                null, padding_bet_values, Pattern.compile("^[ \\:%]+([\\d\\.]+)%"), new int [] {1});

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);


        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();

        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable2() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr: 5.6% 6.4% 9.8%";
        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                null, padding_bet_values, Pattern.compile("^[ \\:%]+([\\d\\.]+)%"), new int [] {1});

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable3() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% 6.4% 9.8%";

        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                padding_bet_key_value, padding_bet_values, Pattern.compile("^(?:\\(\\d+\\))?[ \\:%]+([\\d\\.]+)%"), new int [] {1});

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable4() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% -6.4% 9.8%";

        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                padding_bet_key_value, padding_bet_values, Pattern.compile("^(?:\\(\\d+\\))?[ \\:%]+([\\d\\-\\.]+)%"), new int [] {1});


        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>-6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findExppsureTable5() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr(2) 5.6% -6.4% 9.8%";

        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                padding_bet_key_value, padding_bet_values, Pattern.compile("^(?:\\(\\d+\\))?[ \\:%]+([\\d\\-\\.]+)%"), new int [] {1});


        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td><td>-6.4</td><td>9.8</td></tr></table>");

    }

    @Test
    public void findSpane1() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Sovereign" ,"Sovereign"));
        dictItms.add(new DictItm("Quasi Sovereign" , "Quasi Sovereign"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", REGEX,
                null, padding_bet_values, Pattern.compile("^ +([\\d\\-\\.]+)%"), new int []{1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Sovereign</td><td>68</td></tr><tr><td>Sovereign</td><td>2</td></tr><tr><td>Quasi Sovereign</td><td>10</td></tr><tr><td>Quasi Sovereign</td><td>0</td></tr></table>");

    }

    @Test
    public void findSpan4() {
        String str =
                "The effects of this change are applied retrospectively and are provided in the Reconciliation of Non-GAAP Financial Measures to " +
                        "GAAP Financial Measures tables. 2 CUSTOMER METRICS Total Branded Postpaid Net Additions " +
                        "Branded Postpaid Customers (in thousands) Branded postpaid phone net customer additions " +
                        "were 774,000 in Q3 2018, compared to 686,000 in Q2 2018 and 595,000 in Q3 2017.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Branded postpaid net customer additions" , "Branded postpaid net customer additions", "Branded postpaid net customer additions were"));
        dictItms.add(new DictItm("Branded postpaid phone net customer additions" , "Branded postpaid phone net customer additions", "Branded postpaid phone net customer additions were"));
        dictItms.add(new DictItm("net customer additions" , "net customer additions", "net customer additions"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", REGEX,
                null, null, Pattern.compile("^ +([\\d\\,]+)"), new int [] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Branded postpaid phone net customer additions</td><td>774,000</td></tr></table>");

    }

    @Test
    public void findSpane3() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Net market value" , "Net market value", "Net market value, as of"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Net value", REGEX,
                null, null, Pattern.compile("^ +(\\d{2}\\/\\d{2}\\/\\d{2})"), new int [] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Net market value</td><td>10/31/18</td></tr></table>");

    }

    @Test
    public void findSpane4() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms_1 = new ArrayList<>();
        dictItms_1.add(new DictItm("Returns" , "Returns", "returned"));

        Dictionary dictionary_1 = new Dictionary(dictItms_1, null, "Fund Performance", REGEX,
                null, null, Pattern.compile("^ *([\\-+]?[\\d\\.]+)%"), new int[] {1});
        QTSearchable qtSearchable_1 = new QTSearchable(dictionary_1);

        ArrayList<DictItm> dictItms_2 = new ArrayList<>();
        dictItms_2.add(new DictItm("Net market value" , "Net market value", "Net market value, as of"));

        Dictionary dictionary_2 = new Dictionary(dictItms_2, null, "Net value", REGEX,
                null, null, Pattern.compile("^ +(\\d{2}\\/\\d{2}\\/\\d{2})"), new int [] {1});
        QTSearchable qtSearchable_2 = new QTSearchable(dictionary_2);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable_1);
        qtSearchableList.add(qtSearchable_2);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);

        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Net market value</td><td>10/31/18</td></tr><tr><td>Returns</td><td>-2.67</td></tr></table>");

    }

    @Test
    public void findSpane5() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Returns" , "Returns", "returned"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Fund Performance", REGEX,
                null, null, Pattern.compile("^ *([\\-+]?[\\d\\.]+)%"), new int[] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);

        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Returns</td><td>-2.67</td></tr></table>");

    }

    @Test
    public void STRING_mode() {
        String str = "InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms_1 = new ArrayList<>();
        dictItms_1.add(new DictItm("Returns" , "Returns", "returned"));

        Pattern keyPaddingPattern = Pattern.compile("\\s+(\\(\\d+\\)|[\\:\\,;]+)");
        Dictionary dictionary_1 = new Dictionary(dictItms_1, null, "Fund Performance", null,
                null, null, Pattern.compile("^ *([\\-+]?[\\d\\.]+)%"), new int[] {1});
        QTSearchable qtSearchable_1 = new QTSearchable(dictionary_1);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable_1);
        helper.extract(doc, qtSearchableList, false, "");

    //    doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getValues().get(0).getCategory(), "Returns");
        assertEquals(doc.getValues().get(0).getDict_name(), "Fund Performance");

 //       assertEquals(doc.getTitle(),
 //               "<table width=\"100%\"><tr><td>Returns</td><td>InceptionPortfolio Benchmark (Annualized) Asset Class Composition (Net market value, as of 10/31/18) Fund Performance External: Local: Sovereign 68% Sovereign 2% The Fund returned -2.67% (net I-shares) in October, underperforming the Quasi Sovereign 10% Quasi Sovereign 0% benchmark by 51 bps.</td></tr></table>");

    }

    @Test
    public void stringUnitTest2() {
        // GIVEN
        String str = "Bloomberg Barclays exposure to 10 yr : 5.6\n" +
        "market";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        Dictionary dictionary = new Dictionary(global_dict.getVocab(), null, "test", REGEX,
                null, null, Pattern.compile("[ \\:]+([\\d\\.]+)"), new int [] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>10 Year Exposure</td><td>5.6</td></tr></table>");

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

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Cash and cash equivalents" , "Cash and cash equivalents"));
        dictItms.add(new DictItm("Prepaid expenses and other current assets" ,"Prepaid expenses and other current assets"));
        dictItms.add(new DictItm("Warrant liability" , "Warrant liability"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", null,
                null, Pattern.compile("^[\\s\\$]*$"), Pattern.compile("^[\\$\\s]+([\\d\\,]+)"), new int [] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");
        doc.convertValues2titleTable();

        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Cash and cash equivalents</td><td>62,458</td><td>73,329</td></tr></table>");

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

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", null,
                null, Pattern.compile("^[\\s\\$\\)\\(]*$"), Pattern.compile("^[\\$\\s\\)\\(]+([\\d\\.]+)"), new int [] {1});
        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td><td>304.0</td><td>41.7</td></tr></table>")
        ;
    }

    @Test
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

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", REGEX,
                null, Pattern.compile("^[\\s\\$\\)\\(]*$"), Pattern.compile("^(?:\\(\\d\\))?[\\$\\s\\)\\(]+([\\d\\.]+)"), new int [] {1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td><td>304.0</td><td>41.7</td></tr></table>")
        ;
    }


    @Test
    public void verticalMatchFirstTokeninRow() {
        // GIVEN
        String str = "Date of Service     Provider Name       Provider Licence \n" +
                "6/7/2021 7:43       Katina Spadoni      22LP00013200";

        String str2 = "Date of Service     Provider Name       Provider Licence \n" +
                "  6/7/2021 7:43       Katina Spadoni      22LP00013200";

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);
        QTDocument doc2 = new ENDocumentInfo("", str2, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Date_Service" , "Date of Service"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Company", REGEX,
                null, null, Pattern.compile("(^\\S+)"), new int [] {1});

        QTSearchable qtSearchable = new QTSearchable(dictionary);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, true, "");
        helper.extract(doc2, qtSearchableList, true, "");

        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getValues().size(), 1);

        assertEquals(doc.getValues().get(0).getExtIntervalSimples().get(0).getStart(), 0);
        assertEquals(doc.getValues().get(0).getExtIntervalSimples().get(0).getStr(), "6/7/2021");

        assertNotNull(doc2.getValues());
        assertEquals(doc2.getValues().size(), 1);

        assertEquals(doc2.getValues().get(0).getExtIntervalSimples().get(0).getStart(), 2);
        assertEquals(doc2.getValues().get(0).getExtIntervalSimples().get(0).getStr(), "6/7/2021");

    }

    @Test
    @Ignore
    public void testSpan() throws IOException {

        InputStream is = new FileInputStream(new File("/Users/matin/ddd.txt"));

        String str = convertInputStreamToString(is);

        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument doc = new ENDocumentInfo("", str, helper);

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("State" , "st", "state code", "state"));

        Dictionary dictionary = new Dictionary(dictItms, null, "State", null,
                null, null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary,
                QTDocument.Language.ENGLISH,
                null, null,
                DictSearch.Mode.SPAN, STEM);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(doc, qtSearchableList, true, "");
        ArrayList<ExtInterval> extIntervalArrayList = doc.getValues();
        for (ExtInterval extInterval : extIntervalArrayList){
            System.out.println(extInterval.getCategory() + " "
            + extInterval.getLine() +" " +extInterval.getStart() + " "
            + extInterval.getEnd());
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
}
