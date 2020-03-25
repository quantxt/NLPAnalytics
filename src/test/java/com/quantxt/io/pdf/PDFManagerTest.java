package com.quantxt.io.pdf;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.Dictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.DOUBLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class PDFManagerTest {

    private PDDocument getPDFDocument(InputStream ins) throws IOException {
        PDFParser parser = new PDFParser(new RandomAccessBuffer(new BufferedInputStream(ins)));
        parser.parse();
        COSDocument cosDoc = parser.getDocument();
        PDDocument pdDoc = new PDDocument(cosDoc);
        return pdDoc;
    }

    @Test
    public void PDFOnePageReadNoSort() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("803673_p1-compressed.pdf");
        PDDocument pdDocument = getPDFDocument(is);
        PDFManager pdfManager = new PDFManager(false);
        ArrayList<String> content = pdfManager.read(pdDocument, true, true);
        pdDocument.close();
        assertFalse(content == null);
        assertFalse(content.isEmpty());
        String page = String.join("\n", content);

        assertThat(page.indexOf("Years in Business:")).isEqualTo(2088);
        assertThat(page.indexOf("9/12/2019")).isEqualTo(994);
        assertThat(page.indexOf("Sq Ft of Entire Bldg")).isEqualTo(9951);
    }

    @Test
    public void PDFOnePageReadSort() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("803673_p1-compressed.pdf");
        PDDocument pdDocument = getPDFDocument(is);
        PDFManager pdfManager = new PDFManager(true);
        ArrayList<String> content = pdfManager.read(pdDocument, true, true);
        pdDocument.close();
        assertFalse(content == null);
        assertFalse(content.isEmpty());
        String page = String.join("\n", content);
        /*
        System.out.println(page.indexOf("Years in Business"));
        System.out.println(page.indexOf("9/12/2019"));
        System.out.println(page.indexOf("Sq Ft of Entire Bldg"));

         */

        assertThat(page.indexOf("Years in Business:")).isEqualTo(2088);
        assertThat(page.indexOf("9/12/2019")).isEqualTo(994);
        assertThat(page.indexOf("Sq Ft of Entire Bldg")).isEqualTo(9951);
    }

    @Test
    public void PDFOneBadPageReadSort() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("acord_p8.pdf");
        PDDocument pdDocument = getPDFDocument(is);
        PDFManager pdfManager = new PDFManager(true);
        ArrayList<String> content = pdfManager.read(pdDocument, true, true);
        pdDocument.close();
        assertFalse(content == null);
        assertFalse(content.isEmpty());
        String page = String.join("\n", content);

        assertThat(page.indexOf("BUS PERS PROP")).isEqualTo(2207);
        assertThat(page.indexOf("10,196")).isEqualTo(2505);
        assertThat(page.indexOf("CONSTRUCTION")).isEqualTo(4258);
        assertThat(page.indexOf("TOTAL AREA")).isEqualTo(4621);
    //    assertThat(page.indexOf("YR BUILT")).isEqualTo(4096);
    }

    @Test
    public void PDFDeck_v1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");
        PDDocument pdDocument = getPDFDocument(is);
        PDFManager pdfManager = new PDFManager(true);
        ArrayList<String> content = pdfManager.read(pdDocument, true, true);
        pdDocument.close();
        assertFalse(content == null);
        assertFalse(content.isEmpty());

        Assert.assertEquals(content.size(), 17);

        assertThat(content.get(6).indexOf("Adjusted EBITDA Margin")).isEqualTo(7715);
        assertThat(content.get(6).indexOf("Adjusted EBITDA*")).isEqualTo(6920);
        assertThat(content.get(6).indexOf("CapEx")).isEqualTo(8517);
    }

    @Test
    public void PDFNumberDetectionWithPAGE() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");
            PDDocument pdDocument = getPDFDocument(is);
            PDFManager pdfManager = new PDFManager(true);
            ArrayList<String> content = pdfManager.read(pdDocument, true, true);
            pdDocument.close();
            assertFalse(content == null);


            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("Adjusted EBITDA", "Adjusted EBITDA"));

            Map<String, List<DictItm>> dicts = new HashMap<>();
            dicts.put("EBITDA", items);

            CommonQTDocumentHelper helper = new ENDocumentHelper();

            Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
            Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

            Dictionary dictionary_1 = new Dictionary(dicts, "test", DOUBLE, 130,
                    padding_bet_key_value, padding_bet_values, null, null);

            QTSearchable qtSearchable = new QTSearchable(dictionary_1);
            List<QTSearchable> dictionaries = new ArrayList<>();
            dictionaries.add(qtSearchable);
            QTDocument doc = new ENDocumentInfo("", content.get(6), helper);
            helper.extract(doc, dictionaries, true, "");

            doc.convertValues2titleTable();
            // THEN
            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>Adjusted EBITDA</td><td>908.0</td><td>884.0</td><td>878.0</td><td>895.0</td><td>873.0</td><td>882.0</td><td>804.0</td></tr></table>");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void PDFNumberDetectionWithPAGE_Label() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");
            PDDocument pdDocument = getPDFDocument(is);
            PDFManager pdfManager = new PDFManager(false);
            ArrayList<String> content = pdfManager.read(pdDocument, true, true);
            pdDocument.close();
            assertFalse(content == null);


            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("Q1", "Q1"));
            items.add(new DictItm("Q2", "Q2"));
            items.add(new DictItm("Q3", "Q3"));
            items.add(new DictItm("Q4", "Q4"));

            Map<String, List<DictItm>> dicts = new HashMap<>();
            dicts.put("Quarters", items);

            CommonQTDocumentHelper helper = new ENDocumentHelper();

            Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
            Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

            Dictionary dictionary_1 = new Dictionary(dicts, "test", DOUBLE, 130,
                    padding_bet_key_value, padding_bet_values, null, null);

            QTSearchable qtSearchable = new QTSearchable(dictionary_1);
            List<QTSearchable> dictionaries = new ArrayList<>();
            dictionaries.add(qtSearchable);
            QTDocument doc = new ENDocumentInfo("", content.get(6), helper);
            helper.extract(doc, dictionaries, true, "");

            doc.convertValues2titleTable();
            // THEN
            assertFalse(doc.getValues() == null);
            assertTrue(doc.getTitle().startsWith(
                    "<table width=\"100%\"><tr><td>Q1</td><td>2018</td><td>2199.0</td><td>2102.0</td><td>97.0</td><td>20.0</td><td>251.0</td><td>1291.0</td><td>908.0</td><td>41.3</td><td>297.0</td><td>632.0</td></tr>"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void PDFNumberDetectionWithPAGE_V2() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("ft_p7.pdf");
            PDDocument pdDocument = getPDFDocument(is);
            PDFManager pdfManager = new PDFManager(true);
            ArrayList<String> content = pdfManager.read(pdDocument, true, true);
            pdDocument.close();
            assertFalse(content == null);

            ArrayList<DictItm> items = new ArrayList<>();
            items.add(new DictItm("Adjusted EBITDA", "Adjusted EBITDA"));

            Map<String, List<DictItm>> dicts = new HashMap<>();
            dicts.put("Adjusted EBITDA", items);

            CommonQTDocumentHelper helper = new ENDocumentHelper();

            Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
            Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

            Dictionary dictionary_1 = new Dictionary(dicts, "test", DOUBLE, 130,
                    padding_bet_key_value, padding_bet_values, null, null);

            QTSearchable qtSearchable = new QTSearchable(dictionary_1);
            List<QTSearchable> dictionaries = new ArrayList<>();
            dictionaries.add(qtSearchable);
            QTDocument doc = new ENDocumentInfo("", content.get(0), helper);
            helper.extract(doc, dictionaries, true, "");

            doc.convertValues2titleTable();

            assertFalse(doc.getValues() == null);
            assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>Adjusted EBITDA</td><td>908.0</td><td>884.0</td><td>878.0</td><td>895.0</td><td>873.0</td><td>882.0</td><td>804.0</td></tr></table>");

     //       System.out.println(content.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
