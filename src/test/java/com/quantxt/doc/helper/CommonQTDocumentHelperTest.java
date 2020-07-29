package com.quantxt.doc.helper;

import com.quantxt.commons.model.SearchDocument;
import com.quantxt.commons.service.reader.PdfDocumentReader;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.types.ExtInterval;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.quantxt.types.Dictionary.ExtractionType.NUMBER;
import static com.quantxt.types.Dictionary.ExtractionType.REGEX;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class CommonQTDocumentHelperTest {
    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelperTest.class);


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
        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", NUMBER,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

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

        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", NUMBER,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

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

        Dictionary dictionary = new Dictionary(dictItms, null, "Expense", NUMBER,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

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
    public void tableRow_1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("acord_27pages.pdf");
        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> content = pdfDocumentReader.readByPage(searchDocument, true);

        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("SPK", "% SPRNK"));

        Pattern skipBetweenValues = Pattern.compile("^(?:[ ,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]*\n){0,10}$");
        Dictionary dictionary = new Dictionary(dictItms, null, "SPK", NUMBER,
                skipBetweenValues, skipBetweenValues, null, null);

        //      logger.info(content.get(25));
        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content.get(25), helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");
        assertTrue(doc.getValues() == null);

    }

    @Test
    public void tableRow_TSV() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tsv_w_space.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        dictItms.add(new DictItm("Orig Year Built", "Orig Year Built"));

        Pattern skipBetweenKeyValues = Pattern.compile("^([^\n]{0,30}\n){0,3}$");
        Dictionary dictionary = new Dictionary(dictItms, null, "Orig Year Built", NUMBER,
                skipBetweenKeyValues, null, null, null);


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
    public void tableRow_TSV_noFound() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("tsv_w_space.txt");

        String content = convertInputStreamToString(is);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();
        //YR Built is a valid phrase but an invalid header (label)
        dictItms.add(new DictItm("Year Built", "YR Built"));

        Pattern skipBetweenKeyValues = Pattern.compile("^([^\n]{0,10}\n){0,3}$");
        Dictionary dictionary = new Dictionary(dictItms, null, "Year Built", NUMBER,
                skipBetweenKeyValues, null, null, null);


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

        Pattern skipBetweenKeyValues = Pattern.compile("^([^\n]{0,20}\n){0,3}$");
        Dictionary dictionary = new Dictionary(dictItms, null, "Year Built", NUMBER,
                skipBetweenKeyValues, null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content, helper);
        List<DictSearch> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");
        String excerpt = helper.extractHtmlExcerpt(content, doc.getValues().get(0));
        int ii = excerpt.indexOf("<b>1982</b>");
        assertEquals(excerpt.indexOf("<b>1982</b>"), 182);
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
        dictionary.setGroups(new int [1]);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        QTDocument qtDocument = new ENDocumentInfo("", content, helper);

        List<DictSearch> qtSearchableList = new ArrayList<>();
        qtSearchableList.add(qtSearchable);
        helper.extract(qtDocument, qtSearchableList, true, null);

        assertTrue(qtDocument.getValues().size() == 1);
        assertTrue(qtDocument.getValues().get(0).getExtIntervalSimples().get(0).getStr().equals("MA"));

    }

    private PDDocument getPDFDocument(InputStream ins) throws IOException {
        PDFParser parser = new PDFParser(new RandomAccessBuffer(new BufferedInputStream(ins)));
        parser.parse();
        COSDocument cosDoc = parser.getDocument();
        PDDocument pdDoc = new PDDocument(cosDoc);
        return pdDoc;
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
        String excerpt = helper.extractHtmlExcerpt(content, doc.getValues().get(0));
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
