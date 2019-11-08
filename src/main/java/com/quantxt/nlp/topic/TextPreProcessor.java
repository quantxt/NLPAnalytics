package com.quantxt.nlp.topic;

import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;

/**
 * Created by matin on 5/3/2016.
 */

public class TextPreProcessor implements TokenPreProcess {

    @Override
    public String preProcess(String token) {
        return token;
    }
}
