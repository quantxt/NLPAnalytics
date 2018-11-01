package com.quantxt.doc.helper;

import com.quantxt.doc.JADocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.trie.Emit;
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
    private static final String VERB_FILE_PATH = "/ja/context.json";
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
        return tag.equals("助詞") || tag.startsWith("接") || tag.startsWith("記号");
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
            String [] parts = s.split("。");
            for (String p : parts) {
                allSents.add(p + "。");
            }
        }
        return allSents.toArray(new String[allSents.size()]);
    }

    @Override
    public String normalize(String workingLine) {

        // New: Normalize quotes
        workingLine = r_quote_norm.matcher(workingLine).replaceAll(s_quote_norm);
        workingLine = r_quote_norm2.matcher(workingLine).replaceAll(s_quote_norm2);

        // New: Normalize dashes
        workingLine = workingLine.replace(s_dash_norm, s_dash_norm2);
        workingLine = workingLine.replace(s_dash_norm3, s_dash_norm2);

        // Normalize whitespace
        workingLine = r_white.matcher(workingLine).replaceAll(s_white).trim();

        return workingLine;
    }

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String str, String[] parts) {
        QTDocument doc = new JADocumentInfo("", str, this);
        doc.setRawTitle(str);
        return getNounAndVerbPhrases(doc, parts);
    }

    //http://universaldependencies.org/tagset-conversion/ja-ipadic-uposf.html
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(QTDocument doc, String[] parts) {
    //    orig = orig.replaceAll("[\\p{Cf}]", "");

        String tokenized_title = doc.getTitle();
        tokenized_title = StringUtil.removePrnts(tokenized_title).trim();
        String[] taags = getPosTagsJa(tokenized_title);
        StringBuilder allTags = new StringBuilder();

        for (String t : taags){
            allTags.append(t.substring(0,1));
        }

        HashMap<ExtInterval, Integer> intervals = new HashMap<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()){
            int s = m.start();
            int e = m.end();
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));
            ExtInterval eit = StringUtil.findSpan(tokenized_title, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 1" + String.join(" ", tokenList) + "' in: " + tokenized_title);
            } else {
                eit.setType("N");
                intervals.put(eit, s);
            }
        }

        // find verbs differently and by just regex search

        String raw_title = doc.getRawTitle();
        Collection<Emit> detectedVerbs = getVerbTree().parseText(raw_title);
        if (detectedVerbs != null && detectedVerbs.size() >0){
            for (Emit dv : detectedVerbs){
                ExtInterval eit = new ExtInterval(dv.getStart(), dv.getEnd());
                eit.setType("V");
                intervals.put(eit, dv.getStart());
            }
        }

        /*
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
        */

        List<ExtInterval> phrases = new ArrayList<>();
        Map<ExtInterval, Integer> intervalSorted = MapSort.sortByValue(intervals);

        for (Map.Entry<ExtInterval, Integer> e : intervalSorted.entrySet()){
            ExtInterval eit = e.getKey();
            phrases.add(eit);
        }
        return phrases;
    }
}
