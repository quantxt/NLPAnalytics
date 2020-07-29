package com.quantxt.io.pdf;

import com.quantxt.commons.model.SearchDocument;
import com.quantxt.commons.service.reader.PdfDocumentReader;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.quantxt.types.Dictionary.ExtractionType.NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class PDFManagerTest {

    @Test
    public void PDFOnePageReadNoSort() throws IOException {

        InputStream is = getClass().getClassLoader().getResourceAsStream("803673_p1-compressed.pdf");

        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);
        assertFalse(pages.isEmpty());
        String page = String.join("\n", pages);

        /*
        System.out.println(page.indexOf("Years in Business"));
        System.out.println(page.indexOf("9/12/2019"));
        System.out.println(page.indexOf("Sq Ft of Entire Bldg"));
         */

        assertThat(page.indexOf("Years in Business:")).isEqualTo(3049);
        assertThat(page.indexOf("9/12/2019")).isEqualTo(1370);
        assertThat(page.indexOf("Sq Ft of Entire Bldg")).isEqualTo(16144);
    }

    @Test
    public void PDFOnePageReadSort() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("803673_p1-compressed.pdf");
        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);
        assertFalse(pages.isEmpty());
        String page = String.join("\n", pages);

        /*
        System.out.println(page.indexOf("Years in Business"));
        System.out.println(page.indexOf("9/12/2019"));
        System.out.println(page.indexOf("Sq Ft of Entire Bldg"));
        */
        assertThat(page.indexOf("Years in Business:")).isEqualTo(3049);
        assertThat(page.indexOf("9/12/2019")).isEqualTo(1370);
        assertThat(page.indexOf("Sq Ft of Entire Bldg")).isEqualTo(16144);
    }

    @Test
    public void PDFOneBadPageReadSort() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("acord_p8.pdf");

        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);
        assertFalse(pages.isEmpty());
        String page = String.join("\n", pages);

        /*
        System.out.println(page.indexOf("BUS PERS PROP"));
        System.out.println(page.indexOf("10,196"));
        System.out.println(page.indexOf("CONSTRUCTION"));
        System.out.println(page.indexOf("TOTAL AREA"));
         */


        assertThat(page.indexOf("BUS PERS PROP")).isEqualTo(2633);
        assertThat(page.indexOf("10,196")).isEqualTo(3044);
        assertThat(page.indexOf("CONSTRUCTION")).isEqualTo(4639);
        assertThat(page.indexOf("TOTAL AREA")).isEqualTo(5147);
    //    assertThat(page.indexOf("YR BUILT")).isEqualTo(4096);
    }

    @Test
    public void PDFDeck_v1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");

        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);
        assertFalse(pages.isEmpty());

        Assert.assertEquals(pages.size(), 17);
        /*
        System.out.println(pages.get(6).indexOf("Adjusted EBITDA Margin"));
        System.out.println(pages.get(6).indexOf("Adjusted EBITDA*"));
        System.out.println(pages.get(6).indexOf("CapEx"));
         */

        assertThat(pages.get(6).indexOf("Adjusted EBITDA Margin")).isEqualTo(11255);
        assertThat(pages.get(6).indexOf("Adjusted EBITDA*")).isEqualTo(9855);
        assertThat(pages.get(6).indexOf("CapEx")).isEqualTo(12650);
    }

    @Test
    public void PDFNumberDetectionWithPAGE() throws IOException {

        InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");
        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);


        ArrayList<DictItm> items = new ArrayList<>();
        items.add(new DictItm("Adjusted EBITDA", "Adjusted EBITDA"));

        CommonQTDocumentHelper helper = new ENDocumentHelper();

        Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
        Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

        Dictionary dictionary_1 = new Dictionary(items, null, "EBITDA", NUMBER,
                padding_bet_key_value, padding_bet_values, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary_1);
        List<DictSearch> dictionaries = new ArrayList<>();
        dictionaries.add(qtSearchable);
        QTDocument doc = new ENDocumentInfo("", pages.get(6), helper);
        helper.extract(doc, dictionaries, true, "");

        doc.convertValues2titleTable();
        // THEN
        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>Adjusted EBITDA</td><td>908.0</td><td>884.0</td><td>878.0</td><td>895.0</td><td>873.0</td><td>882.0</td><td>804.0</td></tr></table>");

    }

    @Test
    public void PDFNumberDetectionWithPAGE_Label() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FT_ir_2019Q3.pdf");
        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);

        assertFalse(pages == null);


        ArrayList<DictItm> items = new ArrayList<>();
        items.add(new DictItm("Q1", "Q1"));
        items.add(new DictItm("Q2", "Q2"));
        items.add(new DictItm("Q3", "Q3"));
        items.add(new DictItm("Q4", "Q4"));

        CommonQTDocumentHelper helper = new ENDocumentHelper();

        Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
        Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

        Dictionary dictionary_1 = new Dictionary(items, null, "Quarters", NUMBER,
                padding_bet_key_value, padding_bet_values, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary_1);
        List<DictSearch> dictionaries = new ArrayList<>();
        dictionaries.add(qtSearchable);
        QTDocument doc = new ENDocumentInfo("", pages.get(6), helper);
        helper.extract(doc, dictionaries, true, "");

        doc.convertValues2titleTable();

        // THEN
        assertFalse(doc.getValues() == null);
        assertTrue(doc.getTitle().startsWith(
                "<table width=\"100%\"><tr><td>Q1</td><td>2018</td><td>2199.0</td><td>2102.0</td><td>97.0</td><td>20.0</td><td>251.0</td><td>1291.0</td><td>908.0</td><td>41.3</td><td>297.0</td><td>632.0</td>"));
    }

    @Test
    public void PDFNumberDetectionWithPAGE_V2() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("ft_p7.pdf");
        SearchDocument searchDocument = new SearchDocument();
        searchDocument.setFileName("test.pdf");
        searchDocument.setInputStream(is);
        PdfDocumentReader pdfDocumentReader = new PdfDocumentReader();
        List<String> pages = pdfDocumentReader.readByPage(searchDocument, true);
        assertFalse(pages == null);

        ArrayList<DictItm> items = new ArrayList<>();
        items.add(new DictItm("Adjusted EBITDA", "Adjusted EBITDA"));

        CommonQTDocumentHelper helper = new ENDocumentHelper();

        Pattern padding_bet_values = Pattern.compile("^[\\.%^&*;:\\s\\-\\$]+$");
        Pattern padding_bet_key_value = Pattern.compile("^[\\.%^&*;:\\s\\*\\-\\$\\(\\)\\d]+$");

        Dictionary dictionary_1 = new Dictionary(items, null, "Adjusted EBITDA", NUMBER,
                padding_bet_key_value, padding_bet_values, null, null);

        QTSearchable qtSearchable = new QTSearchable(dictionary_1);
        List<DictSearch> dictionaries = new ArrayList<>();
        dictionaries.add(qtSearchable);
        QTDocument doc = new ENDocumentInfo("", pages.get(0), helper);
        helper.extract(doc, dictionaries, true, "");

        doc.convertValues2titleTable();

        assertFalse(doc.getValues() == null);
        assertEquals(doc.getTitle(), "<table width=\"100%\"><tr><td>Adjusted EBITDA</td><td>908.0</td><td>884.0</td><td>878.0</td><td>895.0</td><td>873.0</td><td>882.0</td><td>804.0</td></tr></table>");
    }
}
