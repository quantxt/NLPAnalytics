package com.quantxt.doc;

import com.quantxt.doc.helper.FRDocumentHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Created by matin on 5/28/18.
 */
public class FRDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(FRDocumentInfo.class);

    public FRDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.FRENCH;
    }

    public FRDocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.FRENCH;
    }

    public FRDocumentInfo(String body, String title) {
        super(body, title, new FRDocumentHelper());
        language = Language.FRENCH;
    }
}