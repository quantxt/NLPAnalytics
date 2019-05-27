package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.helper.types.ExtIntervalSimple;
import com.quantxt.trie.Emit;
import com.quantxt.util.StringUtil;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.NOUN;
import static com.quantxt.helper.types.QTField.QTFieldType.VERB;


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
//    protected Tokenizer analyzer;

    public JADocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS, true);
    }

    public JADocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS, true);

    }

    @Override
    public List<String> tokenize(String text) {
        List<String> tokStrings = new ArrayList<>();
        try {
            Reader reader = new StringReader(text);
            CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
            tokenizer.setReader(reader);
            tokenizer.reset();
            while (tokenizer.incrementToken()) {
                tokStrings.add(termAtt.toString());
            }
            tokenizer.end();
            tokenizer.close();
        } catch (Exception e){
            logger.error(e.getMessage());
        }

        /*
        List<Token> tokens = tokenizer.tokenize(str);
        List<String> tokStrings = new ArrayList<>();
        for (Token e : tokens){
            tokStrings.add(e.getSurface());
        }
        return tokStrings;
        */
        return tokStrings;
    }

    public List<String> getPosTagsJa(String text) {
        List<String> postags = new ArrayList<>();
        try {
            Reader reader = new StringReader(text);
            PartOfSpeechAttribute pattr = tokenizer.addAttribute(PartOfSpeechAttribute.class);
            tokenizer.setReader(reader);
            tokenizer.reset();
            while (tokenizer.incrementToken()) {
                String pos[] = pattr.getPartOfSpeech().split("-");
                postags.add(pos[0]);
            }
            tokenizer.end();
            tokenizer.close();
        } catch (Exception e){
            logger.error(e.getMessage());
        }

        return postags;
    }

    @Override
    public void preInit() {
        //Analyzer
        analyzer = new JapaneseAnalyzer();
  //      analyzer = new Tokenizer();
        //Tokenizer
        tokenizer = new JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.EXTENDED);
    }

    @Override
    public ArrayList<String> stemmer(String str) {
        ArrayList<String> tokStrings = new ArrayList<>();
        try {
            TokenStream tokens = analyzer.tokenStream("field", str);
            CharTermAttribute cattr = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();

            while (tokens.incrementToken()) {
                String term = cattr.toString();
                tokStrings.add(term);

            }
            if (tokStrings.size() == 0) return null;
            tokens.end();
            tokens.close();
        } catch (Exception e){
            return null;
        }

        /*
        List<Token> tokens = tokenizer.tokenize(str);
        ArrayList<String> tokStrings = new ArrayList<>();
        for (Token e : tokens){
            tokStrings.add(e.getBaseForm());
        }
        */
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
    public List<ExtIntervalSimple> getNounAndVerbPhrases(final String orig_str,
                                                         String[] tokens) {

    //    List<Token> taags = getPosTagsJa(orig_str);
        List<String> taags = getPosTagsJa(orig_str);

        StringBuilder allTags = new StringBuilder();
        ExtIntervalSimple [] tokenSpans = StringUtil.findAllSpans(orig_str, tokens);

      //  for (Token t : taags) {
        for (String t : taags){
            allTags.append(t.substring(0, 1));
    //        allTags.append(t.getPartOfSpeechLevel1().substring(0, 1));
        }

        List<ExtIntervalSimple> intervals = new ArrayList<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            int ss = tokenSpans[s].getStart();
            int ee = tokenSpans[e].getEnd();
            ExtIntervalSimple eit = new ExtIntervalSimple(ss, ee);
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setCustomData(str);
            eit.setStringValue(str);
            eit.setType(NOUN);
            intervals.add(eit);
        }

        // find verbs differently and by just regex search

        Collection<Emit> detectedVerbs = getVerbTree().parseText(orig_str);
        if (detectedVerbs != null && detectedVerbs.size() > 0){
            for (Emit dv : detectedVerbs){
                // special case
                ExtIntervalSimple eit = new ExtIntervalSimple(dv.getStart(), dv.getEnd()+1);
                String str = orig_str.substring(eit.getStart(), eit.getEnd());
                eit.setCustomData(str);
                eit.setStringValue(str);
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
