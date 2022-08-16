package com.quantxt.nlp.search.span;

import org.apache.lucene.analysis.TokenStream;

import java.io.IOException;

public interface QTScorer {

    TokenStream init(TokenStream var1) throws IOException;

    void startFragment(QTextFragment var1);

    float getTokenScore();

    float getFragmentScore();
}
