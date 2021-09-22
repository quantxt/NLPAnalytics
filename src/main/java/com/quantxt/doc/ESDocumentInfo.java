package com.quantxt.doc;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ESDocumentHelper;

/**
 * Created by matin on 1/20/18.
 */
public class ESDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);


    public ESDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.SPANISH;
    }

    public ESDocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.SPANISH;
    }

    public ESDocumentInfo(String body, String title) {
        super(body, title, new ESDocumentHelper());
        language = Language.SPANISH;
    }
}
