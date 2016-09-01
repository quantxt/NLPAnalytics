package com.quantxt.helpers;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.quantxt.QTDocument.ENDocumentInfo;
import com.quantxt.QTDocument.QTDocument;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by matin on 6/28/16.
 */

public class PDFManager {

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    final private static Logger logger = LoggerFactory.getLogger(PDFManager.class);

    public static QTDocument pdf2QT(byte [] input) throws IOException
    {
        PDFParser parser = new PDFParser(new RandomAccessBuffer(input)); // update for PDFBox V 2.0
        parser.parse();
        COSDocument cosDoc = parser.getDocument();

        PDFTextStripper pdfStripper = new PDFTextStripper();
        PDDocument pdDoc = new PDDocument(cosDoc);

        try {
            pdDoc.getNumberOfPages();
            pdfStripper.setStartPage(1);
            //    pdfStripper.setEndPage(10);
            pdfStripper.setEndPage(pdDoc.getNumberOfPages());

            String body = pdfStripper.getText(pdDoc);
            String title = pdDoc.getDocumentInformation().getTitle();
            QTDocument doc = new ENDocumentInfo(body, title);
            String author = pdDoc.getDocumentInformation().getAuthor();
            doc.setAuthor(author);
            Calendar d = pdDoc.getDocumentInformation().getCreationDate();
            String dateStr = dateFormat.format(d.getTime());
            doc.setDate(dateStr);
            pdDoc.close();
            return doc;
        } catch (IllegalArgumentException e){
            logger.error("PDF ERROR " + e.getMessage());
            return null;
        }
    }

}





