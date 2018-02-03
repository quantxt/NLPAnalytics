package com.quantxt.doc.helper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */
public class ESDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ESDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String POS_FILE_PATH = "/es/es-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/es/stoplist.txt";
    private static final String VERB_FILE_PATH = "/es/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella" , "Ella", "Él"));

    public ESDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS);
    }

    public ESDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);

    }

    @Override
    public void preInit(){
        //Analyzer
        analyzer = new SpanishAnalyzer();
        //Tokenizer
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);
    }

    @Override
    public List<String> tokenize(String str) {
        String tokenized = str.replaceAll("([\",?\\>\\<\\'\\:\\]\\[\\(\\)\\”\\“])" , "$1 ");
        tokenized = tokenized.replaceAll("([^\\.]+)(\\.+)\\s*$", "$1 $2");
        String [] parts = tokenized.split("\\s+");
        return Arrays.asList(parts);
    }

    private static boolean isTagDC(String tag){
        return tag.equals("CS") || tag.startsWith("S") ||
                tag.equals("CC") || tag.startsWith("D");
    }

    //https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String [] parts) {
        String lowerCase_orig = orig;
        int numTokens = parts.length;
        String[] taags = getPosTags(parts);
        List<String> tokenList = new ArrayList<>();
        List<ExtInterval> phrases = new ArrayList<>();
        String type = "X";
        for (int j = numTokens - 1; j >= 0; j--) {
            String tag = taags[j];
            String word = parts[j];
            if (isTagDC(tag)) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) ||
                        (type.equals("N") && nextTag.startsWith("N")) ||
                        (type.equals("V") && nextTag.startsWith("V"))) {
                    tokenList.add(word);
                }
                continue;
            }
            if ((tag.startsWith("N") || (tag.startsWith("P")))) {
                if (!type.equals("N") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 1 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "N";
                tokenList.add(word);
            } else if (tag.startsWith("A")) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if (nextTag.startsWith("N")) {
                    tokenList.add(word);
                }
                type = "N";
            } else if (tag.startsWith("V")) {
                if (!type.equals("V") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 2 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "V";
                tokenList.add(word);
            } else if (tag.startsWith("R") && type.equals("V")) {
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else {
                if (!type.equals("X") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 3 " + String.join(" ", tokenList));
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "X";
            }
        }

        if (!type.equals("X") && tokenList.size() > 0) {
            Collections.reverse(tokenList);
            ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 4 '" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType(type);
                phrases.add(eit);
            }
        }

        Collections.reverse(phrases);
        return phrases;
    }

}
