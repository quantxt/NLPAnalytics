package com.quantxt.nlp;

import org.apache.log4j.Logger;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;

/**
 * Created by u6014526 on 5/3/2016.
 */
public class TextPreProcessor implements TokenPreProcess {

    final private static Logger logger = Logger.getLogger(TextPreProcessor.class);

    @Override
    public String preProcess(String token) {
        token = token.toLowerCase();
        return token;
    }
}
