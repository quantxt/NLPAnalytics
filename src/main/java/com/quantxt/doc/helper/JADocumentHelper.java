package com.quantxt.doc.helper;

import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;


/**
 * Created by matin on 2/6/18.
 */
public class JADocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(JADocumentHelper.class);

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("此奴", "其奴", "彼", "彼女"));
    private static final String SENTENCE_DELIMITER = "(?<=[。！])";

    private static Pattern NounPhrase = Pattern.compile("N+");

    public JADocumentHelper() {
        analyzer = new JapaneseAnalyzer();
    }

}
