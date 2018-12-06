package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.trie.Emit;
import com.quantxt.util.StringUtil;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.ExtInterval.ExtType.NOUN;
import static com.quantxt.helper.types.ExtInterval.ExtType.VERB;


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
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS, true);
    }

    public JADocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS, true);

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

    public List<Token> getPosTagsJa(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        return tokens;
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
        String[] sentStage1 = super.getSentences(text);
        for (String s : sentStage1) {
            String[] parts = s.split("。");
            for (String p : parts) {
                allSents.add(p + "。");
            }
        }
        return allSents.toArray(new String[allSents.size()]);
    }

    @Override
    public String normalize(String workingLine) {

        // New: Normalize quotes
        List<String> tokens = tokenize(workingLine);
        workingLine = String.join(" ", tokens);
        workingLine = r_quote_norm.matcher(workingLine).replaceAll(s_quote_norm);
        workingLine = r_quote_norm2.matcher(workingLine).replaceAll(s_quote_norm2);

        // New: Normalize dashes
        workingLine = workingLine.replace(s_dash_norm, s_dash_norm2);
        workingLine = workingLine.replace(s_dash_norm3, s_dash_norm2);

        // Normalize whitespace
        workingLine = r_white.matcher(workingLine).replaceAll(s_white).trim();

        return workingLine;
    }

    //http://universaldependencies.org/tagset-conversion/ja-ipadic-uposf.html
    public List<ExtInterval> getNounAndVerbPhrases(final String orig_str,
                                                   String[] tokens) {

        List<Token> taags = getPosTagsJa(orig_str);

        StringBuilder allTags = new StringBuilder();
        ExtInterval [] tokenSpans = StringUtil.findAllSpans(orig_str, tokens);

        for (Token t : taags) {
            allTags.append(t.getPartOfSpeechLevel1().substring(0, 1));
        }

        List<ExtInterval> intervals = new ArrayList<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtInterval eit = new ExtInterval(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            eit.setType(NOUN);
            intervals.add(eit);
        }

        // find verbs differently and by just regex search

        Collection<Emit> detectedVerbs = getVerbTree().parseText(orig_str);
        if (detectedVerbs != null && detectedVerbs.size() > 0){
            for (Emit dv : detectedVerbs){
                // special case
                ExtInterval eit = new ExtInterval(dv.getStart(), dv.getEnd()+1);
                eit.setType(VERB);
                intervals.add(eit);
            }
        }

        return intervals;
    }

    public static void main(String[] args) throws Exception {
        String str = "コーディング・フリスCEOは「消費者はシンプルなデザインを好むようになっており、われわれの予測よりも需要が低かった」と語った。";
        String substr = str.substring(59, 61+1);
        logger.info("'" + substr + "'");
    }
}
