package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * Created by matin on 2/6/18.
 */
public class JADocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(JADocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String POS_FILE_PATH = "";
    private static final String STOPLIST_FILE_PATH = "";
    private static final String VERB_FILE_PATH = "/en/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella", "Ella", "Él"));

    private Tokenizer tokenizer;
    protected Tokenizer analyzer;

    public JADocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS);
    }

    public JADocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);

    }

    @Override
    public List<String> tokenize(String str) {
        List<Token> tokens = tokenizer.tokenize(str);
        List<String> tokStrings = new ArrayList<>();
        for (Token e : tokens){
            tokStrings.add(e.getSurface());
        }
        return tokStrings;
    }

    public String[] getPosTagsJa(String text) {
        List<String> tokStrings = new ArrayList<>();
        List<Token> tokens = tokenizer.tokenize(text);
        for (Token e : tokens){
            tokStrings.add(e.getPartOfSpeechLevel1());
        }
        return tokStrings.toArray(new String[tokStrings.size()]);
    }

    @Override
    public void preInit() {
        //Analyzer
        analyzer = new Tokenizer();
        //Tokenizer
        tokenizer = new Tokenizer();
    }

    @Override
    public ArrayList<String> stemmer(String str) {
        List<Token> tokens = tokenizer.tokenize(str);
        ArrayList<String> tokStrings = new ArrayList<>();
        for (Token e : tokens){
            tokStrings.add(e.getBaseForm());
        }
        return tokStrings;
    }

    protected boolean isTagDC(String tag) {
        return tag.equals("助詞") || tag.startsWith("接") || tag.equals("記号");
    }

    @Override
    public boolean isSentence(String str, List<String> tokens) {
        int numTokens = tokens.size();
        if (numTokens < 15 || numTokens > 400) {
            return false;
        }
        return true;
    }

    //http://universaldependencies.org/tagset-conversion/ja-ipadic-uposf.html
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String[] parts) {
        String lowerCase_orig = orig;
        int numTokens = parts.length;
        String[] taags = getPosTagsJa(orig);
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
                        (type.equals("N") && nextTag.startsWith("名")) ||
                        (type.equals("V") && ( nextTag.startsWith("動") || nextTag.equals("助動詞")))) {
                    tokenList.add(word);
                }
                continue;
            }
            if ((tag.startsWith("名") )) {
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
            } else if (tag.startsWith("形")) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if (nextTag.startsWith("名")) {
                    tokenList.add(word);
                }
                type = "N";
            } else if ( (tag.equals("助動詞")) || (tag.startsWith("動"))) {
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
            } else if (tag.startsWith("副") && type.equals("V")) {
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
