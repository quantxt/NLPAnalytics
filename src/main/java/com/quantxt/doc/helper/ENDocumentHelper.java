package com.quantxt.doc.helper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */
public class ENDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ENDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    private static final String POS_FILE_PATH = "/en/en-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/en/stoplist.txt";
    private static final String VERB_FILE_PATH = "/en/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(
            Arrays.asList("he", "she"));

    public ENDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                    STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS);

    }

    public ENDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);
    }


    private boolean isTagDC(String tag) {
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC")
                || tag.equals("DT");
    }

    @Override
    public void preInit(){
        //Analyzer
        analyzer = new StandardAnalyzer();
        //Tokenizer
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);
    }

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig,
            String[] parts) {
        String lowerCase_orig = orig.toLowerCase();
        int numTokens = parts.length;
        String[] taags = getPosTags(parts);
        List<String> tokenList = new ArrayList<>();
        List<ExtInterval> phrases = new ArrayList<>();
        String type = "X";
        for (int j = numTokens - 1; j >= 0; j--) {
            final String tag = taags[j];
            final String word = parts[j];
            if (isTagDC(tag)) {
                int nextIdx = j - 1;
                if (nextIdx < 0)
                    continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) && (isTagDC(tag))
                        || (type.equals("N") && nextTag.startsWith("NN"))
                        || (type.equals("V") && nextTag.startsWith("VB"))) {
                    tokenList.add(word);
                }
                continue;
            }
            if (tag.startsWith("NN") || tag.equals("PRP")) {
                if (!type.equals("N") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error(
                                "NOT FOUND 1 '" + String.join(" ", tokenList)
                                        + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    /*
                     * String str = String.join(" ", tokenList); int start =
                     * orig.indexOf(str); if (start == -1){
                     * logger.error("NOT FOUND 1 " + str); } ExtInterval eit =
                     * new ExtInterval(start, start+str.length());
                     * eit.setType(type); phrases.add(eit);
                     */
                    tokenList.clear();
                }
                type = "N";
                tokenList.add(word);
            } else if (tag.startsWith("JJ")) {
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else if (tag.startsWith("VB")) {
                if (!type.equals("V") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error(
                                "NOT FOUND 2 '" + String.join(" ", tokenList)
                                        + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    /*
                     * String str = String.join(" ", tokenList); int start =
                     * orig.indexOf(str); if (start == -1){
                     * logger.error("NOT FOUND 2 " + str); } ExtInterval eit =
                     * new ExtInterval(start, start+str.length());
                     * eit.setType(type); phrases.add(eit);
                     */
                    tokenList.clear();
                }
                type = "V";
                tokenList.add(word);
            } else if (tag.startsWith("MD") || tag.startsWith("RB")) {
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else {
                if (!type.equals("X") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error(
                                "NOT FOUND 3 " + String.join(" ", tokenList));
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    /*
                     * String str = String.join(" ", tokenList); int start =
                     * orig.indexOf(str); if (start == -1){
                     * logger.error("NOT FOUND 3 " + str); } ExtInterval eit =
                     * new ExtInterval(start, start+str.length());
                     * eit.setType(type); phrases.add(eit);
                     */
                    tokenList.clear();
                }
                type = "X";
            }
        }

        if (!type.equals("X") && tokenList.size() > 0) {
            Collections.reverse(tokenList);
            ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);

            if (eit == null) {
                logger.error("NOT FOUND 4 '" + String.join(" ", tokenList)
                        + "' in: " + orig);
            } else {
                eit.setType(type);
                phrases.add(eit);
            }
            /*
             * ExtInterval eit = new ExtInterval(start, start+str.length());
             * String str = String.join(" ", tokenList); int start =
             * orig.indexOf(str); eit.setType(type); phrases.add(eit);
             */
        }

        Collections.reverse(phrases);
        return phrases;
    }

}
