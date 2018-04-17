package com.quantxt.doc.helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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
    private static final String TOKENIZER_FILE_PATH = "/en/en-token.bin";
    private static final String VERB_FILE_PATH = "/en/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(
            Arrays.asList("he", "she", "He", "She"));

    private Tokenizer tokenizer;

    public ENDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                    STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS);

    }

    public ENDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);
    }


    protected boolean isTagDC(String tag) {
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC")
                || tag.equals("DT");
    }

    @Override
    public List<String> tokenize(String str) {
        String[] toks = tokenizer.tokenize(str);
        return Arrays.asList(toks);
    }

    @Override
    public void preInit(){
        //Analyzer
    //    analyzer = new StandardAnalyzer();
        analyzer = new EnglishAnalyzer();
        try (FileInputStream fis = new FileInputStream(getModelBaseDir() + TOKENIZER_FILE_PATH)) {
            TokenizerModel tokenizermodel = new TokenizerModel(fis);
            tokenizer = new TokenizerME(tokenizermodel);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    //https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig,
            String[] parts) {
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
                if (tokenList.size() == 0) continue;
                int nextIdx = j - 1;
                if (nextIdx < 0)
                    continue;
                String nextTag = taags[nextIdx];

                if ((type.equals("N") && nextTag.startsWith("N"))
                        || (type.equals("V") && nextTag.startsWith("V"))) {
                    tokenList.add(word);
                }
                continue;
            }
            if ((tag.startsWith("N") || (tag.startsWith("P")))) {
                if (!type.equals("N") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.debug(
                                "NOT FOUND 1 '" + String.join(" ", tokenList)
                                        + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "N";
                tokenList.add(word);
            } else if (tag.startsWith("J")) {
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else if (tag.startsWith("V")) {
                if (!type.equals("V") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.debug(
                                "NOT FOUND 2 '" + String.join(" ", tokenList)
                                        + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
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
                        logger.debug(
                                "NOT FOUND 3 " + String.join(" ", tokenList)
                                        + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0,
                                eit.getStart());
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
                logger.debug("NOT FOUND 4 '" + String.join(" ", tokenList)
                        + "' in: " + orig);
            } else {
                eit.setType(type);
                phrases.add(eit);
            }
        }

        Collections.reverse(phrases);
        return phrases;
    }

}
