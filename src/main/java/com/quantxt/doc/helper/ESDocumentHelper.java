package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dejani on 1/24/18.
 */
public class ESDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ESDocumentHelper.class);
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella" , "Ella", "Él"));

    private static Pattern NounPhrase = Pattern.compile("N+S*N+|N+A*");
    private static Pattern VerbPhrase = Pattern.compile("RV+|V+");

    public ESDocumentHelper() {
        analyzer = new SpanishAnalyzer();
    }
}
