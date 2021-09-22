package com.quantxt.nlp.tokenizer;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

public class QLetterOnlyTokenizer extends CharTokenizer {
    public QLetterOnlyTokenizer() {
    }

    public QLetterOnlyTokenizer(AttributeFactory factory) {
        super(factory);
    }

    public QLetterOnlyTokenizer(AttributeFactory factory, int maxTokenLen) {
        super(factory, maxTokenLen);
    }

    protected boolean isTokenChar(int c) {
        // Letter
        return Character.isLetter(c);
    }
}