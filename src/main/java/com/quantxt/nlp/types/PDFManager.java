package com.quantxt.nlp.types;

import com.google.gson.Gson;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.ArticleBodyResolver;
import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.interval.Interval;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;
import com.quantxt.types.NamedEntity;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.nlp.types.QTValue.getPad;

/**
 * Created by matin on 6/28/16.
 */

public class PDFManager {

    final private static Logger logger = LoggerFactory.getLogger(PDFManager.class);

    public static PDDocument write(ArrayList<String> strArr){
        PDDocument document = new PDDocument();

        float fontSize = 12;
        float leading = 20;
        PDFont font = PDType1Font.TIMES_ROMAN;
        try {
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream contents = new PDPageContentStream(document, page);
            contents.beginText();
            contents.setFont(font, fontSize);
            contents.setLeading(leading);
            contents.newLineAtOffset(50, 725);

            float width = 470;
            int numlines = 36;
            int n = 0;

            logger.info("width : " + width);

            for (String str : strArr) {
            //    str = str.replaceAll("[^\\x00-\\x7F]", "");
                str = str.replaceAll(" \\.$", ".");
                String [] parts = str.split("\\s+");
                float c = 0;
                StringBuilder sb = new StringBuilder();
                for (String p : parts)
                {
                    if (c > width)  {
                        contents.showText(sb.toString().trim());
                        contents.newLineAtOffset(0, -15);
                        n++;
                        if (n > numlines){
                            contents.endText();
                            contents.close();
                            page = new PDPage();
                            document.addPage(page);
                            contents = new PDPageContentStream(document, page);
                            contents.beginText();
                            contents.setFont(font, fontSize);
                            contents.setLeading(leading);
                            contents.newLineAtOffset(50, 725);
                            n = 0;
                        }
                        sb = new StringBuilder();
                    }

                    sb.append(p).append(" ");
                    c = font.getStringWidth(sb.toString()) / 1000 * fontSize;
                }

                if (sb.length() > 0) {
                    contents.showText(sb.toString().trim());
                    n++;
                    if (n > numlines){
                        contents.endText();
                        contents.close();
                        page = new PDPage();
                        document.addPage(page);
                        contents = new PDPageContentStream(document, page);
                        contents.beginText();
                        contents.setFont(font, fontSize);
                        contents.setLeading(leading);
                        contents.newLineAtOffset(50, 725);
                        n = 0;
                    }
                }
                contents.newLine();
            }
            contents.endText();
            contents.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return document;
    }

    private static String extractNoSpaces(PDDocument document) throws IOException
    {
        PDFTextStripper stripper = new PDFTextStripper()
        {
            @Override
            protected void processTextPosition(TextPosition text)
            {
                String character = text.getUnicode();
                if (character != null && character.trim().length() != 0)
                    super.processTextPosition(text);
            }
        };
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    public static QTDocument pdf2QT(byte [] input)
    {
        try {
            PDFParser parser = new PDFParser(new RandomAccessBuffer(input)); // update for PDFBox V 2.0
            parser.parse();
            COSDocument cosDoc = parser.getDocument();
            PDFTextStripper pdfStripper = new PDFTextStripper();
            PDDocument pdDoc = new PDDocument(cosDoc);
            QTDocument doc = null;
    //        int num = pdDoc.getNumberOfPages();
            pdfStripper.setStartPage(1);
            //    pdfStripper.setEndPage(10);
            pdfStripper.setEndPage(pdDoc.getNumberOfPages());

            String body = extractNoSpaces(pdDoc);
            String title = pdDoc.getDocumentInformation().getTitle();
            doc = new ENDocumentInfo(body, title);
            String author = pdDoc.getDocumentInformation().getAuthor();
            doc.setAuthor(author);
            Calendar d = pdDoc.getDocumentInformation().getCreationDate();
            if (d != null) {
                doc.setDate(new DateTime(d.getTime()));
            }
            pdDoc.close();
            return doc;
        } catch (Exception e){
            logger.error("PDF ERROR " + e);
            return null;
        }
    }

    public static PDDocument write(String title1,
                                   String title2,
                                   Collection<String> strArr)
    {
        PDDocument document = new PDDocument();
        float fontSize = 12;
        float leading = 1.5f * fontSize;
        int numLines = 37;
        int lc = 0;

        PDFont font = PDType1Font.TIMES_ROMAN;

    //    PDFont font = PDType1Font.COURIER;
        try {
        //    PDFont font = PDTrueTypeFont.loadTTF(document , new File("Times_New_Roman_Normal.ttf"));
            PDPage page = new PDPage();
            document.addPage(page);
            PDPageContentStream contents = new PDPageContentStream(document, page);

            PDRectangle mediabox = page.getMediaBox();
            float margin = 72;
            float width = mediabox.getWidth() - 2*margin;
            float startX = mediabox.getLowerLeftX() + margin;
            float startY = mediabox.getUpperRightY() - margin;

            List<String> lines = new ArrayList<>();
            for (String text : strArr) {
                text = text.replaceAll("[^\\x00-\\x7F]", "");
                text = text.replaceAll(" \\.$", ".").trim();
                int lastSpace = -1;
                while (text.length() > 0) {
                    int spaceIndex = text.indexOf(' ', lastSpace + 1);
                    if (spaceIndex < 0) {
                        spaceIndex = text.length();
                    }
                    String subString = text.substring(0, spaceIndex);
                    float size = fontSize * font.getStringWidth(subString) / 1000;
                    if (size > width) {
                        if (lastSpace < 0) {
                            lastSpace = spaceIndex;
                        }
                        subString = text.substring(0, lastSpace);
                        lines.add(subString);
                        text = text.substring(lastSpace).trim();
                        lastSpace = -1;
                    } else if (spaceIndex == text.length()) {
                        lines.add(text);
                        text = "";
                    } else {
                        lastSpace = spaceIndex;
                    }
                }
            }

            float titleFontSize = 14;
            contents.beginText();
            contents.setFont(font, titleFontSize);

            float titleWidth = font.getStringWidth(title1) / 1000 * titleFontSize;
            float titleoffset1 = (580 - titleWidth) / 2;
            contents.newLineAtOffset(titleoffset1, startY);
            contents.showText(title1);

            titleWidth = font.getStringWidth(title2) / 1000 * titleFontSize;
            float titleoffset2 = (580 - titleWidth) / 2;
            contents.newLineAtOffset(-titleoffset1 + titleoffset2, -1.5f *titleFontSize);
            contents.showText(title2);

            contents.setFont(font, fontSize);
            contents.newLineAtOffset(-titleoffset2 + startX, -2f *titleFontSize);
            lc++;
            lc++;

            for (String line: lines)
            {
                if (lc++ > numLines){
                    contents.endText();
                    contents.close();
                    page = new PDPage();
                    document.addPage(page);
                    contents = new PDPageContentStream(document, page);
                    contents.beginText();
                    contents.setFont(font, fontSize);
                    contents.newLineAtOffset(startX, startY);
                    lc = 0;
                }
                contents.showText(line);
                contents.newLineAtOffset(0, -leading);
            }
            contents.endText();
            contents.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return document;
    }

    public static void main(String[] args) throws Exception {
        //     Path path = Paths.get("/Users/matin/Downloads/mu-emd-i.pdf");

        //     byte[] data = Files.readAllBytes(path);
        //     QTDocument doc = pdf2QT(data);
        InputStream is = new FileInputStream(new File("/Users/matin/Downloads/Document.htm"));
        org.jsoup.nodes.Document jdoc = Jsoup.parse(is, "UTF-8", "Document.htm");
        ENDocumentHelper helper = new ENDocumentHelper();
        ENDocumentInfo doc = new ENDocumentInfo(jdoc.body().text(), "sec", helper);

        String [] sentences = helper.getSentences(jdoc.body().text());

        Trie.TrieBuilder tries = Trie.builder().onlyWholeWords().ignoreOverlaps();
        tries.addKeyword("News product subscription revenues");
        tries.addKeyword("Other product subscription revenues");
        tries.addKeyword("News product subscriptions");
        tries.addKeyword("Other product subscriptions");
        tries.addKeyword("Total digital-only subscription revenues");

     //   tries.addKeyword("revenues increased");
     //   tries.addKeyword("year-over-year growth");

        Gson gson = new Gson();
    //    tries.addKeyword("liability");
        Trie keywords = tries.build();
        int dist = 9;
        for (String rawSent_curr : sentences) {

            rawSent_curr = rawSent_curr.replaceAll("(\\S+)\\:", "$1");
            rawSent_curr = rawSent_curr.replaceAll("(\\S+)\\(\\d+\\)", "$1");


            Collection<Emit> emits = keywords.parseText(rawSent_curr);
            if (emits.size() == 0) continue;
    //        logger.info(rawSent_curr);
            // get potential keys


            ArrayList<ExtInterval> values = new ArrayList<>();
            // get potential values
            helper.getValues(rawSent_curr, values);
            if (values.size() == 0) continue;

            // let's find all values that belong to this key

            for (Emit emit : emits) {
                String key = emit.getKeyword();
                ArrayList<ExtInterval> vals2print = new ArrayList<>();
                int keyEnd =  emit.getEnd();
                Iterator<ExtInterval> iter = values.iterator();
                while (iter.hasNext()) {
                    ExtInterval extv = iter.next();
                    int valStart = extv.getStart();
                    int diff = (valStart - keyEnd);
                    if (diff > 0 && diff < dist) {
            //            extv.setKey(key);
                        keyEnd = extv.getEnd();
                        vals2print.add(extv);
                        iter.remove();
                    }
                }
//                if (vals2print.size() > 1){
//                    logger.info(gson.toJson(vals2print));
//                }
            }

            StringBuilder sb = new StringBuilder();
            ArrayList<String> vals = new ArrayList<>();
            for (ExtInterval extv : values) {
                if (extv.getKey() != null) {
                    // start new row and print he previous row
                    if (vals.size() > 0) {
                        sb.append("<tr>");
                        for (String v : vals) {
                            sb.append("<td>").append(v).append("</td>");
                        }
                        sb.append("</tr>");
                    }
                    vals.clear();
   //                 vals.add(extv.getKey());
                }
                vals.add(extv.getCustomData().toString());

            }
            if (vals.size() > 0) {
                sb.append("<tr>");
                for (String v : vals) {
                    sb.append("<td>").append(v).append("</td>");
                }
                sb.append("</tr>");
            }

    //        logger.info(sb.toString());
        }
    }
}





