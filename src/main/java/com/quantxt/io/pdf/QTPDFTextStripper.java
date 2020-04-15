package com.quantxt.io.pdf;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.*;

public class QTPDFTextStripper extends PDFTextStripper {

    private char [][] lines;
    private float[] yPixelDistr;

    public QTPDFTextStripper(PDPage pdPage) throws IOException {
        super();
        yPixelDistr = new float[(int)pdPage.getMediaBox().getHeight()];
    }

    public QTPDFTextStripper(PDPage pdPage,
                             float[] yPixelDistr) throws IOException {
        lines = new char[ (int)pdPage.getMediaBox().getHeight()][(int)pdPage.getMediaBox().getWidth()];
        this.yPixelDistr = yPixelDistr;

        float pageLineWdith = pdPage.getMediaBox().getWidth();
        for (int i=0; i < yPixelDistr.length; i++){
            char [] line_text = new char[(int)pageLineWdith];
            //By default each line is filled with space character
            Arrays.fill(line_text, ' ');
            lines[i] = line_text;
        }
    }

    public float[] getYPixelDistr(){
        return yPixelDistr;
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions)
    {
        TextPosition firstTp = textPositions.get(0);
        if (lines == null){
            int start_line_top = (int) firstTp.getYDirAdj();
            int start_line_bot = (int) (firstTp.getYDirAdj() + firstTp.getHeight());

            for (TextPosition tp : textPositions){
                int start_line_top_next = (int) tp.getYDirAdj();
                if (start_line_top_next != start_line_top){
                    int start_line_bot_next = (int) (tp.getYDirAdj() + tp.getHeight());
                    start_line_top =  start_line_top_next;
                    start_line_bot = start_line_bot_next;
                }

                for (int i = start_line_top; i < start_line_bot; i++){
                    yPixelDistr[i] += 1;
                }
            }
            return;
        }

        int start_line_top = (int)  firstTp.getYDirAdj();
        int start_line_bot = (int) (firstTp.getYDirAdj() + firstTp.getHeight());
        int line_number = start_line_top;

        int start_search_range = start_line_top > 4 ? start_line_top - 4 : start_line_top;
        int end_search_range = start_line_bot < lines.length - 4 ? start_line_bot + 4: start_line_bot;
        float intensity =  yPixelDistr[start_search_range];
        for (int i=start_search_range; i <= end_search_range; i++){
            if (yPixelDistr[i] > intensity){
                line_number= i;
                intensity = yPixelDistr[i];
            }
        }

        char [] line_text = lines[line_number];
        int index = (int) ( firstTp.getXDirAdj());

        for (TextPosition tp : textPositions){
            int start_line_top_next = (int) tp.getYDirAdj();
            if (start_line_top_next != start_line_top){
                start_search_range = start_line_top > 4 ? start_line_top - 4 : start_line_top;
                end_search_range = start_line_bot < lines.length - 4 ? start_line_bot + 4: start_line_bot;
                intensity =  yPixelDistr[start_search_range];
                for (int i=start_search_range; i <= end_search_range; i++){
                    if (yPixelDistr[i] > intensity){
                        line_number= i;
                        intensity = yPixelDistr[i];
                    }
                }

                start_line_top =  start_line_top_next;
                line_text = lines[line_number];
                index = (int) ( tp.getXDirAdj());
            }

            char c = tp.getUnicode().charAt(0);
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
