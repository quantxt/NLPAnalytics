package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.types.MapSort;
import com.quantxt.util.StringUtil;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by matin on 2/6/18.
 */
public class JADocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(JADocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String POS_FILE_PATH = "";
    private static final String STOPLIST_FILE_PATH = "/ja/stoplist.txt";
    private static final String VERB_FILE_PATH = "/en/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("此奴", "其奴", "彼", "彼女"));

    private static Pattern NounPhrase = Pattern.compile("名+");
    private static Pattern VerbPhrase = Pattern.compile("動+");

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

    @Override
    public String[] getSentences(String text) {

        ArrayList<String> allSents = new ArrayList<>();
        String [] sentStage1 = super.getSentences(text);
        for (String s : sentStage1){
            String [] p = s.split("。");
            allSents.addAll(Arrays.asList(p));
        }
        return allSents.toArray(new String[allSents.size()]);
    }

    //http://universaldependencies.org/tagset-conversion/ja-ipadic-uposf.html
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String[] parts) {
        String[] taags = getPosTagsJa(orig);

 //      for (int i=0; i < parts.length; i++){
 //          logger.info(parts[i] +"____________" + taags[i] + " ");
 //      }

        StringBuilder allTags = new StringBuilder();

        for (String t : taags){
            allTags.append(t.substring(0,1));
        }

        HashMap<ExtInterval, Integer> intervals = new HashMap<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()){
            String match = m.group();
            int s = m.start();
            int e = m.end();
   //         if (match.contains("P") && !taags[s].equals(taags[e-1])){
   //             String tagStr = String.join("_", Arrays.copyOfRange(taags, s , e));
   //             if (!tagStr.contains("_P_")) continue;
   //         }
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));
            ExtInterval eit = StringUtil.findSpan(orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 1" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType("N");
                intervals.put(eit, s);
            }
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()){
            int s = m.start();
            int e = m.end();
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));
            ExtInterval eit = StringUtil.findSpan(orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 2" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType("V");
                intervals.put(eit, s);
            }

        }

        List<ExtInterval> phrases = new ArrayList<>();
        Map<ExtInterval, Integer> intervalSorted = MapSort.sortByValue(intervals);

        for (Map.Entry<ExtInterval, Integer> e : intervalSorted.entrySet()){
            ExtInterval eit = e.getKey();
            phrases.add(eit);
  //                   logger.info(eit.getType() + " -> " + orig.substring(eit.getStart(), eit.getEnd()));
        }
        return phrases;

        /*
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
        */
    }
}
