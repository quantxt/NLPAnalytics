package com.quantxt.doc;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.RUDocumentHelper;

/**
 * Created by matin on 1/20/18.
 */
public class RUDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(RUDocumentInfo.class);

    public RUDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo(String body, String title) {
        super(body, title, new RUDocumentHelper());
        language = Language.RUSSIAN;
    }

}
