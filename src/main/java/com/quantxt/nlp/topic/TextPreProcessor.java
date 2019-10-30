package com.quantxt.nlp.topic;

import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by matin on 5/3/2016.
 */

public class TextPreProcessor implements TokenPreProcess {

    @Override
    public String preProcess(String token) {
        return token;
    }
}
