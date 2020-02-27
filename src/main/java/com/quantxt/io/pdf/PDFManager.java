package com.quantxt.io.pdf;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by matin on 6/28/16.
 */

public class PDFManager {

    final private static Logger logger = LoggerFactory.getLogger(PDFManager.class);

    final  boolean sortByPosition;

    public PDFManager(boolean sortByPosition){
        this.sortByPosition = sortByPosition;
    }

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

    private PDFTextStripper getPageLineStripper(Map<Integer, char[]> lines,
                                                int line_length,
                                                boolean sortByPosition) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper()
        {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions)
            {
                int line_number = (int)textPositions.get(0).getY();
                char [] line_text = lines.get(line_number);
                if (line_text == null){
                    line_text = new char[line_length];
                    //By default each line is filled with space character
                    Arrays.fill(line_text, ' ');
                    lines.put(line_number, line_text);
                }
                int firstX = (int)textPositions.get(0).getX();
                for (int i=0; i<text.length();i ++){
                    char c = text.charAt(i);
                    if (Character.getType(c) == Character.CONTROL) continue;
                    line_text[firstX+i] = c;
                }
            }

            @Override
            protected void processTextPosition(TextPosition text)
            {
                String character = text.getUnicode();
                if (character != null && character.trim().length() != 0) {
                    super.processTextPosition(text);
                }
            }
        };

        if (sortByPosition) {
            stripper.setSortByPosition(true);
        }

        return stripper;
    }
    private PDFTextStripper getPageStripper() throws IOException {
        PDFTextStripper stripper = new PDFTextStripper()
        {
            @Override
            protected void processTextPosition(TextPosition text)
            {
                String character = text.getUnicode();
                if (character != null && character.trim().length() != 0) {
                    super.processTextPosition(text);
                }
            }
        };

        if (sortByPosition) {
            stripper.setSortByPosition(true);
        }

        return stripper;
    }

    private ArrayList<String> read(PDDocument pdDoc,
                                   boolean readLineByLine,
                                   boolean removeNullCols) throws IOException {


        if (readLineByLine) {
            int line_length = (int) pdDoc.getPage(0).getMediaBox().getWidth();
            ArrayList<TreeMap<Integer, char []>> pages = new ArrayList<>();
            for (int page = 1; page <= pdDoc.getNumberOfPages(); page++) {
                TreeMap<Integer, char []> pagelines = new TreeMap<>();
                PDFTextStripper stripper = getPageLineStripper(pagelines, line_length, sortByPosition);
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.getText(pdDoc);
                pages.add(pagelines);
            }
            return getNonNullPageLineContent(pages, removeNullCols);
        } else {
            ArrayList<String> content_arr = new ArrayList<>();
            PDFTextStripper stripper = getPageStripper();
            for (int page = 1; page <= pdDoc.getNumberOfPages(); page++){
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String page_content = stripper.getText(pdDoc);
                if (page_content == null) continue;
                //Convert each page to one line (remove new lines)
                page_content = page_content.replaceAll("[\\r\\n]+", " ");
                content_arr.add(page_content);
            }
            return content_arr;
        }
    }

    private static String extractNoSpaces(PDDocument document,
                                          StringBuilder sb) throws IOException
    {
        PDFTextStripper stripper = new PDFTextStripper()
        {
            @Override
            protected void processTextPosition(TextPosition text)
            {
                String character = text.getUnicode();
                if (character == null) return;
                if (character.trim().length() != 0) {
                    sb.append(character);
                    super.processTextPosition(text);
                }
            }
        };

        StringBuilder allPagesContent = new StringBuilder();
        int numPage = document.getNumberOfPages();
        for (int i=1; i<=numPage; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);

            try {
                String t = stripper.getText(document);
                if (t != null){
                    allPagesContent.append(t);
                }
            } catch (Exception e) {
           //     e.printStackTrace();
                logger.error(e.getMessage());
            }
            sb.append("\n");
        }
        return allPagesContent.toString();
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

            StringBuilder sb = new StringBuilder();
            String body = extractNoSpaces(pdDoc, sb);
            logger.info(body);
            String title = pdDoc.getDocumentInformation().getTitle();
            doc = new ENDocumentInfo(body, title);
            Calendar d = pdDoc.getDocumentInformation().getCreationDate();
            if (d != null) {
                doc.setDate(LocalDateTime.from(d.toInstant()));
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

    private Map<Integer, boolean[]> getPageNullColumns(ArrayList<TreeMap<Integer, char[]>> pageLines){
        Map<Integer, boolean[]> nullPageColumns = new HashMap<>();
        //find left padding
        for (int pageNumber=0; pageNumber< pageLines.size(); pageNumber++) {
            TreeMap<Integer, char[]> page = pageLines.get(pageNumber);
            boolean [] blankCols = new boolean[page.entrySet().iterator().next().getValue().length];
            Arrays.fill(blankCols, true);
            for (Map.Entry<Integer, char[]> e : page.entrySet()) {
                char[] line = e.getValue();
                for (int i = 0; i< line.length-1; i++){
                    if (line[i] == ' ' && line[i+1] == ' ') continue;
                    blankCols[i] = false;
                }
            }
            nullPageColumns.put(pageNumber, blankCols);
        }
        return nullPageColumns;
    }

    private ArrayList<String> getNonNullPageLineContent(ArrayList<TreeMap<Integer, char[]>> pageLines,
                                                        boolean removeNullColumns){
        Map<Integer, boolean[]> nullPageColumns = null;
        if (removeNullColumns){
            nullPageColumns = getPageNullColumns(pageLines);
        }

        ArrayList<String> page_contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber< pageLines.size(); pageNumber++) {
            TreeMap<Integer, char[]> page = pageLines.get(pageNumber);
            StringBuilder pageStringBuilder = new StringBuilder();
            if (removeNullColumns){
                boolean [] blankCols = nullPageColumns.get(pageNumber);
                for (char [] line : page.values()) {
                    StringBuilder lineStringBuiler = new StringBuilder();
                    for (int j = 0; j < line.length; j++) {
                        if (blankCols[j]) continue;
                        char c = line[j];
                        lineStringBuiler.append(c);
                    }
                    String line_str = lineStringBuiler.toString();
                    if (line_str.trim().isEmpty()) continue;
                    line_str = line_str.replaceAll("\\s+$", "\n");
                    pageStringBuilder.append(line_str);
                }
            } else {
                for (char [] line : page.values()) {
                    String line_str = new String(line);
                    if (line_str.trim().isEmpty()) continue;
                    line_str = line_str.replaceAll("\\s+$", "\n");
                    pageStringBuilder.append(line_str);
                }
            }
            page_contents.add(pageStringBuilder.toString());
        }
        return page_contents;
    }

    public static void main(String[] args) throws Exception {
        String file = "/Users/matin/Downloads/11-10 17-18 PROP QTAP ACORDS (INTERNATIONAL VILLAGE ASSOCIATION, INC.).pdf";
        InputStream is = new FileInputStream(new File(file));

        PDFParser parser = new PDFParser(new RandomAccessBuffer(new BufferedInputStream(is)));
        parser.parse();

        COSDocument cosDoc = parser.getDocument();
        PDDocument pdDoc = new PDDocument(cosDoc);
        PDFManager pdfManager = new PDFManager(true);

        ArrayList<String> content = pdfManager.read(pdDoc, true, true);

        String out = String.join("\n", content);
        System.out.println(content.get(0));
        System.out.println(out.length());
    }
}





