package com.quantxt.nlp.search.span;

import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class QToken {
    String str;
    int start;
    int end;
    int pos;
    int postInc;

    int num_merged = 0;


    public QToken(String text,
                  OffsetAttribute o,
                  PositionIncrementAttribute p,
                  int pos){
        this.start = o.startOffset();
        this.end = o.endOffset();
        this.str = text.substring(start, end);
        this.postInc = p.getPositionIncrement();
        this.pos = pos;
    }

    public QToken(String text,
                  int start,
                  int end,
                  int postInc,
                  int pos){
        this.start = start;
        this.end = end;
        this.str = text.substring(start, end);
        this.postInc = postInc;
        this.pos = pos;
    }

    public String getStr() {
        return str;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getPos() {
        return pos;
    }

    public int getPostInc() {
        return postInc;
    }
}
