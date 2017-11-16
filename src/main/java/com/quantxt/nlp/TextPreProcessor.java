package com.quantxt.nlp;

import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by matin on 5/3/2016.
 */

public class TextPreProcessor implements TokenPreProcess {

    final private static Logger logger = LoggerFactory.getLogger(TextPreProcessor.class);

    @Override
    public String preProcess(String token) {
    //    token = token.toLowerCase();
        return token;
    }
}
