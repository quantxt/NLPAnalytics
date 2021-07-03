package com.quantxt.doc.helper;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.types.ExtInterval;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static com.quantxt.types.Dictionary.ExtractionType.REGEX;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class CommonQTDocumentHelperTest {

    @Test
    public void simpleExtraction() {
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

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));
        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", null,
                null, Pattern.compile("^[\\$\\s\\(\\)]*$"), Pattern.compile("^(?:\\(\\d\\))?[\\$\\s\\(\\)]+([\\d\\.]+)"), new int [] {1});

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td><td>304.0</td><td>41.7</td></tr></table>")
        ;
    }

    @Test
    public void largeHorizentalSpace() {
        String str =
                "Total selling, general and administrative expense(1) \n" +
                "$ \n" +
                "177.3 \n" +
                "                           $ \n" +
                "304.0 \n" +
                "(41.7 \n" +
                ")% ";

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", null,
                null, Pattern.compile("^[\\$\\s\\(\\)]*$"), Pattern.compile("^(?:\\(\\d\\))?[\\$\\s\\(\\)]+([\\d\\.]+)"), new int [] {1});

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td><td>304.0</td><td>41.7</td></tr></table>")
        ;
    }

    @Test
    public void largeHorizentalSpaceValid() {
        String str =
                "Total selling, general and administrative expense(1) \n" +
                "                                   $ \n" +
                "177.3 \n" +
                "   $ \n" +
                "304.0 \n" +
                "  (41.7 \n" +
                ")% ";

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", REGEX,
                null, Pattern.compile("^[\\$\\s\\(\\)]*$"), Pattern.compile("^(?:\\(\\d\\))?[\\$\\s\\(\\)]+([\\d\\.]+)"), new int [] {1});

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td><td>304.0</td><td>41.7</td></tr></table>")
        ;
    }

    @Test
    public void tableLineTest1() {
        String str = "               Total selling, general and administrative expense(1) \n";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        boolean isTableRow = helper.lineIsTableRow(str);
        assertFalse(isTableRow);
    }

    @Test
    public void tableLineTest2() {
        String str = "               Total     hetffd      hkef \n";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        boolean isTableRow = helper.lineIsTableRow(str);
        assertTrue(isTableRow);
    }

    @Test
    public void startOfNextToken() {
        String str = "This is a very long sentence  $%  about $#  processing";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        int startNextToken = helper.startNextToken(str, 28);
        assertTrue(startNextToken == 34);
    }

    @Test
    public void endOfPreviousToken() {
        String str = "This is a very long sentence  $%  about $#  processing";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        int endPreviousToken = helper.endPrevToken(str, 34);
        assertTrue(endPreviousToken == 28);
    }

    @Test
    public void endOfPreviousToken_2() {
        String str = "This is a even muuuuuuuuuuuuuuuuuuuuch                      longerrrrrrrrrrrrrrrrrrrrrr sentence  $%  about $#  processing";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        int endPreviousToken = helper.endPrevToken(str, 102);
        assertTrue(endPreviousToken == 96);
    }

    @Test
    public void tableRow_TSV() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tsv_w_space.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Orig Year Built", "Orig Year Built"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Orig Year Built", REGEX,
                null, Pattern.compile("^[\\n ]*$"), Pattern.compile("^[ \\n]*(?:[A-Za-z\\(\\) \\n]{0,35})?(\\d{4})"), new int []{1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");

        doc.convertValues2titleTable();

        assertTrue(doc.getTitle().equals(
                "<table width=\"100%\"><tr><td>Orig Year Built</td><td>1982</td><td>1982</td><td>1982</td><td>1982</td></tr></table>"));
    }

    @Test
    public void extractAuto_1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("accord140.txt");
        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("POLICY_NUMBER", "POLICY NUMBER"));
        dictItms.add(new DictItm("EFFECTIVE_DATE", "EFFECTIVE DATE"));
        dictItms.add(new DictItm("STREET_ADDRESS", "STREET ADDRESS"));
        dictItms.add(new DictItm("PLUMBING","PLUMBING, YR"));
        dictItms.add(new DictItm("CONSTRUCTION_TYPE","CONSTRUCTION TYPE"));
        dictItms.add(new DictItm("PROT_CL","PROT CL"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Orig Year Built", REGEX,
                null, null, Pattern.compile("__auto__"), new int []{1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, null);

        ArrayList<ExtInterval> v = doc.getValues();
        Collections.sort(v, Comparator.comparingInt(ExtInterval::getLine));

        assertTrue(v.get(0).getExtIntervalSimples().get(0).getStr().equals("CPS3227404"));
        assertTrue(v.get(1).getExtIntervalSimples().get(0).getStr().equals("07/14/20"));
        assertTrue(v.get(2).getExtIntervalSimples().get(0).getStr().equals("13261 McGregor Blvd. Fort Myers FL 33919"));
        assertTrue(v.get(3).getExtIntervalSimples().get(0).getStr().equals("JM"));
        assertTrue(v.get(4).getExtIntervalSimples().get(0).getStr().equals("2"));
        assertTrue(v.get(5).getExtIntervalSimples().get(0).getStr().equals("18"));

    }

    @Test
    public void extractAuto_3() throws IOException {

        String content = "Member Name:                        Member DOB:\n" +
                "  JOHN L DOE                          10/04/1945\n" +
                "  Member ID:                          Group #:\n" +
                "  ZZZ123456789                        9876543210\n" +
                "\n" +
                "  RXBIN:     123456                    Deductible:  $500\n" +
                "  RXPCN:     ADV                       CÐ¾Ð ay:  $20 Ð Ð¡Ð ";
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("DOB", "Member DOB"));
        dictItms.add(new DictItm("Group", "Group #"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Insurance_Id", REGEX,
                null, null, Pattern.compile("__auto__"), new int []{1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, null);

        ArrayList<ExtInterval> v = doc.getValues();
        Collections.sort(v, Comparator.comparingInt(ExtInterval::getLine));

        assertTrue(v.get(0).getExtIntervalSimples().get(0).getStr().equals("10/04/1945"));
        assertTrue(v.get(1).getExtIntervalSimples().get(0).getStr().equals("9876543210"));

    }

    @Test
    public void extractAuto_2() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("table-v1.txt");
        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Status", "Status"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Test", REGEX,
                null, null, Pattern.compile("__auto__"), new int []{1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, null);

        ArrayList<ExtInterval> v = doc.getValues();
        Collections.sort(v, Comparator.comparingInt(ExtInterval::getLine));

        assertTrue(v.get(0).getExtIntervalSimples().get(0).getStr().equals("APPROVED"));

    }

    @Test
    public void tableRow_TSV_noFound() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tsv_w_space.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        //YR Built is a valid phrase but an invalid header (label)
        dictItms.add(new DictItm("Year Built", "YR Built"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Year Built", REGEX,
                null, null, Pattern.compile("^[^\n]{0,10}\n{0,3}(\\d{4})$"), new int [] {1});


        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");

        assertTrue(doc.getValues() == null);
    }

    @Test
    public void exceprt_v1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tsv_w_space.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        //YR Built is a valid phrase but an invalid header (label)
        dictItms.add(new DictItm("Year Built", "Orig Year Built"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Year Built", REGEX,
                null, null, Pattern.compile("^[^\n]{0,20}\n{0,3}(\\d{4})"), new int []{1});

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");
        String excerpt = helper.extractHtmlExcerpt(content, doc.getValues().get(0));
        int ii = excerpt.indexOf("<b>1982</b>");
        assertEquals(excerpt.indexOf("<b>1982</b>"), 178);
    }

    @Test
    public void regex_extraction_v1() {

        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        //YR Built is a valid phrase but an invalid header (label)
        dictItms.add(new DictItm("SSN", "Social security"));

        Dictionary dictionary = new Dictionary(dictItms, null, "SSN", REGEX,
                null, null, Pattern.compile("(is|was)\\s+(\\d{3}\\-\\d{2}\\-\\d{4})"), new int []{2});

        String content = "may social security  is 123-23-1234";
        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        assertFalse(doc.getValues() == null);
        assertTrue(doc.getValues().size() == 1);
        assertTrue(doc.getValues().get(0).getExtIntervalSimples().get(0).getStr().equals("123-23-1234"));
    }

    @Test
    public void tableRowDetect_1() {
        String row = "This is not a table row";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        boolean isTableRow = helper.lineIsTableRow(row);
        assertTrue(!isTableRow);
    }

    @Test
    public void tableRowDetect_2() {
        String row = "This             can             be             a         table            row";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        boolean isTableRow = helper.lineIsTableRow(row);
        assertTrue(isTableRow);
    }

    @Test
    public void tableRowDetect_3() {
        String row = "small row";
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        boolean isTableRow = helper.lineIsTableRow(row);
        assertTrue(isTableRow);
    }

    @Test
    @Ignore
    public void apart_keywords_v1() {
        // GIVEN
        String content = "There is Item 1.                       Business here.";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Business", "Item 1. Business" ));

        Dictionary dictionary = new Dictionary(null, "Business", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                DictSearch.Mode.SPAN, DictSearch.AnalyzType.STEM);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");

        assertTrue(doc.getValues().size() == 0);
    }


    @Test
    @Ignore
    public void multi_analyze_match() {
        // GIVEN
        String query = "gas and rockes";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Item1", "Search 1 has gas and rockes" ));
        dictItms.add(new DictItm("Item2", "Search 2 has rock and ga" ));

        Dictionary dictionary = new Dictionary(null, "Test", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                new DictSearch.Mode[] {DictSearch.Mode.PARTIAL_SPAN}, new DictSearch.AnalyzType[] {DictSearch.AnalyzType.STANDARD, DictSearch.AnalyzType.STEM});

        List<ExtInterval> matches = qtSearchable.search(query);

        assertTrue(matches.size() == 1);
    }

    @Test
    public void multiLineSearch() {
        // GIVEN
        String content = "         COMPANYMAILING                     ADDRESS                             PROPERTY             LOCATION\n" +
                "\n" +
                "         Selective         Ins.Co       ofNew                                    65     CANAL         ST\n" +
                "\n" +
                "         PO     BOX782747                                                         MILLBURY,MA01527-3266\n" +
                "                                                                             dumm y iline";

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Address", "PROPERTY LOCATION" ));

        Dictionary dictionary = new Dictionary(null, "Address", dictItms);
        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH, null, null,
                new DictSearch.Mode[] {DictSearch.Mode.SPAN}, new DictSearch.AnalyzType[] {DictSearch.AnalyzType.STEM});

        dictionary.setValType(REGEX);
        dictionary.setPattern(Pattern.compile("(?:\\S+ +){0,4}(?:(?:(?:\\d+|\\d+[ \\-]+\\d+) +[A-Za-z \\-\\n,]+)([A-Z]{2}) {0,20}(\\d{5})(?:\\-\\d{4})?)[ \\n]+"));
        dictionary.setGroups(new int []{1});
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument qtDocument = new ENDocumentInfo("", content, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(qtDocument, qtSearchableList, true, null);

        assertTrue(qtDocument.getValues().size() == 1);
        assertTrue(qtDocument.getValues().get(0).getExtIntervalSimples().get(0).getStr().equals("MA"));

    }

    @Test
    @Ignore
    public void flood_long_phrase() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("flood2.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        //YR Built is a valid phrase but an invalid header (label)
        dictItms.add(new DictItm("Condo", "BuildingIndicator:Non-Elevated"));

        Dictionary dictionary = new Dictionary(dictItms, null, "Zone", null,
                null, null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary, QTDocument.Language.ENGLISH,
                null,
                null,
                new DictSearch.Mode[] {DictSearch.Mode.ORDERED_SPAN},
        new DictSearch.AnalyzType[]{DictSearch.AnalyzType.SIMPLE,
                DictSearch.AnalyzType.STANDARD});

        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);

        helper.extract(doc, searchableList, true, "");
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
