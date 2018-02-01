package com.quantxt.doc.helper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.annolab.tt4j.TokenHandler;
import org.annolab.tt4j.TreeTaggerWrapper;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */
public class RUDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(RUDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    private static final String POS_FILE_PATH = "/ru/ru-pos-maxent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String STOPLIST_FILE_PATH = "/ru/stoplist.txt";
    private static final String VERB_FILE_PATH = "/ru/context.json";

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays
            .asList("Он", "Его", "Ему", "онá", "oна", "oн", "eму", "eго"));

    public RUDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH, STOPLIST_FILE_PATH,
                VERB_FILE_PATH, PRONOUNS);
    //    init();
    }

    public RUDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);
    }

    @Override
    public void preInit(){
        //Analyzer
        analyzer = new RussianAnalyzer();
        //Tokenizer : TODO: This is not right for russian.. need to build a custome one
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);
    }

    protected boolean isTagDC(String tag){
        return tag.equals("C") || tag.equals("I") || tag.startsWith("S");
    }

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String[] parts) {
        String lowerCase_orig = orig.toLowerCase();
        int numTokens = parts.length;
        String [] taags = getPosTags(parts);
        if (taags.length != parts.length) {
            logger.error("Number of tags and parts is not the same: " + orig);
            return null;
        }
        List<String> tokenList= new ArrayList<>();
        List<ExtInterval> phrases = new ArrayList<>();
        String type = "X";
        for (int j = numTokens-1; j>=0; j--) {
            final String tag = taags[j];
            final String word = parts[j];
            if ( isTagDC(tag) ) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) ||
                        (type.equals("N") && nextTag.startsWith("N") ) ||
                        (type.equals("V") && nextTag.startsWith("V") ))
                {
                    tokenList.add(word);
                }
                continue;
            }
            if (tag.startsWith("N") || tag.startsWith("P-")){
                if (!type.equals("N") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 1 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    /*
                    String str = String.join(" ", tokenList);
                    int start = orig.indexOf(str);
                    if (start == -1){
                        logger.error("NOT FOUND 1 " + str);
                    }
                    ExtInterval eit = new ExtInterval(start, start+str.length());
                    */

                    tokenList.clear();
                }
                type = "N";
                tokenList.add(word);
            }  else if ( tag.startsWith("A")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            } else if (tag.startsWith("V")){
                if (!type.equals("V") && tokenList.size() >0){
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
            } else if (tag.startsWith("Q") || tag.startsWith("R")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            }  else {
                if (!type.equals("X") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 3 " + String.join(" ", tokenList) + "' in: " + orig);
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

        if (!type.equals("X") && tokenList.size() >0){
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
