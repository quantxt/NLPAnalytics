package com.quantxt.nlp.types;

import java.io.*;

import java.util.Collection;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import org.apache.poi.xwpf.converter.pdf.PdfConverter;



/**
 * Created by matin on 2/5/18.
 */
public class DOCManager {


    public static XWPFDocument write(String title1,
                             String title2,
                             Collection<String> strArr) throws IOException {
        //Blank Document
        XWPFDocument document = new XWPFDocument();

        //Write the Document in file system
        FileOutputStream out = new FileOutputStream(new File("createparagraph.docx"));

        //create Paragraph
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText("Г. Манучехри заметил, что статистика Ирана и ОПЕК отличается, при этом Иран собственных данных о добыче нефти Целью страны является выход на досанкционный уровень нефтедобычи в  И в рамках переговоров со странами ОПЕК о сокращении добычи нефти, Иран занял твердую позицию и эту позицию ОПЕК учла.");

        document.write(out);
        out.close();

        return document;
    }

    public static void conver2PDF(XWPFDocument document) throws IOException {
        File outFile=new File("res.pdf");
        OutputStream pdfout=new FileOutputStream(outFile);
        PdfConverter.getInstance().convert(document ,pdfout, null);

    }

    public static void main(String[] args) throws Exception {
        XWPFDocument doc = write("", "", null);
        conver2PDF(doc);
    }
}