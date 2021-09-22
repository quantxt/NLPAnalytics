package com.quantxt.doc.helper;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dejani on 1/24/18.
 */

public class ENDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ENDocumentHelper.class);

    public ENDocumentHelper() {
        analyzer = new EnglishAnalyzer();
    }
}
