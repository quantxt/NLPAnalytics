package com.quantxt.io.pdf;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.*;

public class QTPDFTextStripper extends PDFTextStripper {

    private char [][] lines;
    private float minHeigh;
    private float minWidth;

    private Map<Float, Integer> heightDist;
    private Map<Float, Integer> weidthDist;
    private boolean avgIsSet = false;

    public QTPDFTextStripper() throws IOException {
        super();
        heightDist = new TreeMap<>();
        weidthDist = new TreeMap<>();
    }

    public QTPDFTextStripper(PDPage pdPage,
                             Map<Float, Integer> heightDist,
                             Map<Float, Integer> weidthDist) throws IOException {
        avgIsSet = true;
        ArrayList<Float> allWdiths = new ArrayList<>(weidthDist.keySet());
        this.minWidth = allWdiths.get(0);

        ArrayList<Float> allHeight = new ArrayList<>(heightDist.keySet());
        this.minHeigh = allHeight.get(0);

        float page_height = pdPage.getMediaBox().getHeight();
        float page_width = pdPage.getMediaBox().getWidth();
        lines = new char[ (int)(page_height/ this.minHeigh)][(int)(page_width/ this.minWidth)];
  //      System.out.println(" ====== " + this.avgWidth + " / " + this.avgHeigh);
    }

    public Map<Float, Integer> getHeightMap(){
        return heightDist;
    }

    public Map<Float, Integer> getWeidthMap(){
        return weidthDist;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions)
    {
        if (!avgIsSet){
            for (TextPosition textPosition : textPositions){
                float w = Math.round(textPosition.getWidth()  * 100f) / 100f ;
                float h = Math.round(textPosition.getHeightDir() * 100f) / 100f;
                Integer hc = heightDist.get(h);
                Integer wc = weidthDist.get(w);
                if (hc == null){
                    hc = 0;
                }
                if (wc == null){
                    wc = 0;
                }
                heightDist.put(h, hc+1);
                weidthDist.put(w,  wc+1);
            }
            return;
        }

        TextPosition firstTextPosition = textPositions.get(0);
        int center = (int) (firstTextPosition.getYDirAdj() - .5 * firstTextPosition.getHeightDir());

        Integer line_number = (int) (center / minHeigh) ;
        char [] line_text = lines[line_number];

        if (line_text == null){
            float pageLineWdith = firstTextPosition.getPageWidth() / minWidth ;
            line_text = new char[(int)pageLineWdith];
            //By default each line is filled with space character
            Arrays.fill(line_text, ' ');
            lines[line_number] = line_text;
        }

        int index = (int) ( firstTextPosition.getXDirAdj()  / minWidth);

   //     logger.info(" ==> " + text + " / " + firstTextPosition.getX() + " / " + firstTextPosition.getXDirAdj() + " / " + index);
        for (int i=0; i<text.length();i++){
            char c = text.charAt(i);
            line_text[index++] = c;
        }
    }

    @Override
    protected void processTextPosition(TextPosition text)
    {
        String character = text.getUnicode();
        if (character == null || character.length() == 0) return;
        super.processTextPosition(text);
    }

    public char [][] getLines(){
        return lines;
    }
}
