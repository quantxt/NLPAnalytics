package com.quantxt.doc.helper;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.io.pdf.PDFManager;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.Dictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.DOUBLE;
import static org.junit.Assert.*;

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
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Expense" , dictItms);

        Dictionary dictionary = new Dictionary(entMap, "test", DOUBLE, 5,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<QTSearchable> searchableList = new ArrayList<>();
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
        String str = "Total selling, general and administrative expense(1) \n" +
                "$ \n" +
                "177.3 \n" +
                "                           $ \n" +
                "304.0 \n" +
                "(41.7 \n" +
                ")% ";

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Expense" , dictItms);

        Dictionary dictionary = new Dictionary(entMap, "test", DOUBLE, 5,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<QTSearchable> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, false, "");

        doc.convertValues2titleTable();
        // THEN
        assertNotNull(doc.getValues());
        assertEquals(doc.getTitle(),
                "<table width=\"100%\"><tr><td>Total selling, general and administrative expense</td><td>177.3</td></tr></table>")
        ;
    }

    @Test
    public void largeHorizentalSpaceValid() {
        String str = "Total selling, general and administrative expense(1) \n" +
                "                                   $ \n" +
                "177.3 \n" +
                "   $ \n" +
                "304.0 \n" +
                "  (41.7 \n" +
                ")% ";

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("Total selling, general and administrative expense" , "Total selling, general and administrative expense"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("Expense" , dictItms);

        Dictionary dictionary = new Dictionary(entMap, "test", DOUBLE, 5,
                Pattern.compile("^\\s*(\\([^\\)]+\\))?[\\s,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]+$"), null, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary);
        CommonQTDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(str, str, helper);
        List<QTSearchable> searchableList = new ArrayList<>();
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
    public void tableRow_1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("acord_27pages.pdf");
        PDDocument pdDocument = getPDFDocument(is);
        PDFManager pdfManager = new PDFManager(true);
        ArrayList<String> content = pdfManager.read(pdDocument, true, true);
        CommonQTDocumentHelper helper = new ENDocumentHelper();

        ArrayList<DictItm> dictItms = new ArrayList<>();

        dictItms.add(new DictItm("SPK" , "% SPRNK"));
        Map<String, List<DictItm>> entMap = new HashMap<>();
        entMap.put("SPK" , dictItms);

        Pattern skipBetweenValues = Pattern.compile("^([ ,;\"\\'\\:\\.\\?\\/\\/\\)\\(\\#\\@\\!\\-\\*\\%]*\n?){0,10}$");
        Dictionary dictionary = new Dictionary(entMap, "test", DOUBLE, 5,
                skipBetweenValues, skipBetweenValues, null, null);

  //      logger.info(content.get(25));
        QTSearchable qtSearchable = new QTSearchable(dictionary);
        ENDocumentInfo doc = new ENDocumentInfo("", content.get(25), helper);
        List<QTSearchable> searchableList = new ArrayList<>();
        searchableList.add(qtSearchable);
        helper.extract(doc, searchableList, true, "");
        assertTrue(doc.getValues() == null);
    }

    private PDDocument getPDFDocument(InputStream ins) throws IOException {
        PDFParser parser = new PDFParser(new RandomAccessBuffer(new BufferedInputStream(ins)));
        parser.parse();
        COSDocument cosDoc = parser.getDocument();
        PDDocument pdDoc = new PDDocument(cosDoc);
        return pdDoc;
    }
}
