package com.quantxt.doc;

import com.quantxt.doc.helper.JADocumentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Created by matin on 2/6/18.
 */
public class JADocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(JADocumentInfo.class);

    private static HashSet<String> puntuations = new HashSet<>(Arrays.asList("・", "？", "。", "！", "．"));

    public JADocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.JAPANESE;
    }

    public JADocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.JAPANESE;
    }

    public JADocumentInfo (String body, String title) {
        super(body, title, new JADocumentHelper());
        language = Language.JAPANESE;
    }
}
