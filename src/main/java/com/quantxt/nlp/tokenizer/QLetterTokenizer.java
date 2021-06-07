package com.quantxt.nlp.tokenizer;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

public class QLetterTokenizer extends CharTokenizer {
    public QLetterTokenizer() {
    }

    public QLetterTokenizer(AttributeFactory factory) {
        super(factory);
    }

    public QLetterTokenizer(AttributeFactory factory, int maxTokenLen) {
        super(factory, maxTokenLen);
    }

    protected boolean isTokenChar(int c) {
        // Letter or digit or checked box ☒ or unchecked box  ☐
        return Character.isLetterOrDigit(c) || c == 9744 || c == 9746;
    }
}