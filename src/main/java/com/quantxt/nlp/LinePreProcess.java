package com.quantxt.nlp;

import com.quantxt.doc.QTDocument;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultStreamTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Created by matin on 5/3/2016.
 */
public class LinePreProcess implements TokenizerFactory {

    final private static Logger logger = LoggerFactory.getLogger(LinePreProcess.class);
    private TokenPreProcess tokenPreProcess;
    private QTDocument qt;

    public LinePreProcess(QTDocument doc) {
        qt = doc;
    }

    public Tokenizer create(String toTokenize) {
        toTokenize = qt.normalize(toTokenize);
        DefaultTokenizer t = new DefaultTokenizer(toTokenize.trim());
        t.setTokenPreProcessor(this.tokenPreProcess);
        return t;
    }

    public Tokenizer create(InputStream toTokenize) {
        DefaultStreamTokenizer t = new DefaultStreamTokenizer(toTokenize);
        t.setTokenPreProcessor(this.tokenPreProcess);
        return t;
    }

    public void setTokenPreProcessor(TokenPreProcess preProcessor) {
        this.tokenPreProcess = preProcessor;
    }

    @Override
    public TokenPreProcess getTokenPreProcessor() {
        return null;
    }

}
