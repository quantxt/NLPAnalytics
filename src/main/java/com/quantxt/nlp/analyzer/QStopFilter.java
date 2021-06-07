package com.quantxt.nlp.analyzer;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

public class QStopFilter extends TokenFilter {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private final CharArraySet stopwords;

    public QStopFilter(TokenStream in, CharArraySet stopwords) {
        super(in);
        this.stopwords = stopwords != null && stopwords.size() > 0 ? stopwords : CharArraySet.EMPTY_SET ;
    }

    public final boolean incrementToken() throws IOException {
        if (stopwords.size() == 0) return this.input.incrementToken();
        while (true) {
            boolean hasNext = this.input.incrementToken();
            if (hasNext) {
                if (stopwords.contains(termAtt.toString())) {
                    posIncrAtt.setPositionIncrement(0);
                    continue;
                }
            }
            return hasNext;
        }
    }
}