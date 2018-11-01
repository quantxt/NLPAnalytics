package com.quantxt.nlp.types;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.helper.DateResolver;
import com.quantxt.interval.Interval;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Path path = Paths.get("/Users/matin/Downloads/ICAT.pdf");
        byte[] data = Files.readAllBytes(path);
        QTDocument doc = pdf2QT(data);

        Pattern MONEY   = Pattern.compile("([\\s+|^]\\$\\s*((\\d[,\\.\\d]*)|(\\(\\d[,\\.\\d]*\\))))(?:\\s*(hundred|thousand|million|billion|M|B)[\\s,\\.;]+)?");
        Pattern PERCENT = Pattern.compile("[\\.\\d]+\\%");
        Pattern NUMBER  = Pattern.compile("([\\s+|^](\\d[,\\.\\d]*)|(\\(\\d[,\\d]*\\)))(?:\\s*(hundred|thousand|million|billion|M|B)[\\s,\\.;]+)?");
        Pattern TAG = Pattern.compile("^[A-Z][a-z,\\.\\)\\(]+");

        Pattern MULT_SPACE = Pattern.compile("\\s{2,}");

        String [] lines = doc.getBody().split("\n");

        int id = 0;

        for (String l : lines){
            String original = l ;
            String l_copy = l;
            Matcher m = TAG.matcher(l);
            if (!m.find()) continue;
            ArrayList<Interval> money = new ArrayList<>();
            ArrayList<Interval> perc = new ArrayList<>();
            ArrayList<Interval> numb = new ArrayList<>();

            m = MONEY.matcher(l_copy);
            StringBuffer sb = new StringBuffer(l_copy.length());
            String space =  " ";
            while (m.find()){
        //        String tagid = " __MON__"+id++ + " ";
                String tagid = String.join("", Collections.nCopies(m.end() - m.start(), " "));
                m.appendReplacement(sb, Matcher.quoteReplacement(tagid));
                money.add(new Interval(m.start(), m.end()));
            }
            m.appendTail(sb);

            l_copy = sb.toString();
            m = PERCENT.matcher(l_copy);
            sb = new StringBuffer(l_copy.length());

            while (m.find()){
            //    String tagid = " __PERC__"+id++ + " ";
                String tagid = String.join("", Collections.nCopies(m.end() - m.start(), " "));
                m.appendReplacement(sb, Matcher.quoteReplacement(tagid));
                perc.add(new Interval(m.start(), m.end()));
            }
            m.appendTail(sb);

            l_copy = sb.toString();
            m = NUMBER.matcher(l_copy);
            sb = new StringBuffer(l_copy.length());

            while (m.find()){
            //    String tagid = " __NUM__"+id++ + " ";
                String tagid = String.join("", Collections.nCopies(m.end() - m.start(), " "));
                m.appendReplacement(sb, Matcher.quoteReplacement(tagid));
                numb.add(new Interval(m.start(), m.end()));
            }

            m.appendTail(sb);
            if ((numb.size() + money.size() + perc.size()) == 0) continue;
            int min_idx = 10000;
            int max_idx = 0;
            ArrayList<Interval> allIntervals = new ArrayList<>();
            allIntervals.addAll(money);
            allIntervals.addAll(numb);
            allIntervals.addAll(perc);
            for (Interval it : allIntervals){
                if (it.getStart() < min_idx){
                    min_idx = it.getStart();
                }
                if (it.getEnd() > max_idx){
                    max_idx = it.getEnd();
                }
            }
            String l_end = l_copy.substring(max_idx).trim();
            if (l_end.length() != 0) continue;
            l_copy = sb.toString().trim();
            m = MULT_SPACE.matcher(l_copy);
            if (m.find()) continue;
            l_copy = l_copy.replaceAll("\\b-\\b" , "").trim();

            ArrayList<String> vals = new ArrayList<>();
            vals.add(l_copy);
            if (money.size() > 0) {
                for (Interval it : money) {
                    int s = it.getStart();
                    int en = it.getEnd();
                    vals.add(l.substring(s, en).replace(" ", "").trim());
                }
                logger.info("Money: " + String.join("\t", vals));
            } else if (perc.size() > 0){
                for (Interval it : perc) {
                    int s = it.getStart();
                    int en = it.getEnd();
                    vals.add(l.substring(s, en).replace(" ", "").trim());
                }
                logger.info("Perc: " + String.join("\t", vals));
            } else if (numb.size() > 0){
                for (Interval it : numb) {
                    int s = it.getStart();
                    int en = it.getEnd();
                    vals.add(l.substring(s, en).replace(" ", "").trim());
                }
                logger.info("Number: " + String.join("\t", vals));
            }


        }

    }
}





