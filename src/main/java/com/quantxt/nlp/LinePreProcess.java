package com.quantxt.nlp;

import cc.mallet.pipe.Pipe;
import cc.mallet.types.Instance;
import com.quantxt.nlp.types.TextNormalizer;
import org.apache.log4j.Logger;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultStreamTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.DefaultTokenizer;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import java.io.*;

/**
 * Created by matin on 5/3/2016.
 */
public class LinePreProcess extends Pipe implements TokenizerFactory {

    final private static Logger logger = Logger.getLogger(LinePreProcess.class);
    private TokenPreProcess tokenPreProcess;

    public LinePreProcess() {
        TextNormalizer.init();
    }

    public Instance pipe (Instance carrier)
    {
        String string = (String) carrier.getData();
        string = TextNormalizer.normalize(string);

        carrier.setData(string);
        return carrier;
    }

    public Tokenizer create(String toTokenize) {
        toTokenize = TextNormalizer.normalize(toTokenize);
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
