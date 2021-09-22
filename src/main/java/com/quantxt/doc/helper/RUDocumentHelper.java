package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by dejani on 1/24/18.
 */

public class RUDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(RUDocumentHelper.class);

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays
            .asList("Он", "Его", "Ему", "онá", "oна", "oн", "eму", "eго"));

    private static Pattern NounPhrase = Pattern.compile("N([N]*N|N*A+|N*)|A+N+");
    private static Pattern VerbPhrase = Pattern.compile("V+");

    public RUDocumentHelper() {
        analyzer = new RussianAnalyzer();
    }
}
