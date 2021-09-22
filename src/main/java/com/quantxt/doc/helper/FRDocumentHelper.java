package com.quantxt.doc.helper;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by matin on 5/28/18.
 */

public class FRDocumentHelper extends CommonQTDocumentHelper {
    private static Logger logger = LoggerFactory.getLogger(FRDocumentHelper.class);

    private static Pattern NounPhrase = Pattern.compile("(N([LPN]*N|N*A+|N*))|AN+");
    private static Pattern VerbPhrase = Pattern.compile("V+");
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("il", "elle", "Elle", "Il"));

    public FRDocumentHelper() {
        analyzer = new FrenchAnalyzer();
    }
}
