package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtIntervalSimple;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.doc.helper.CommonQTDocumentHelper.QTPosTags.*;
import static com.quantxt.helper.types.QTField.QTFieldType.NOUN;
import static com.quantxt.util.NLPUtil.findAllSpans;


/**
 * Created by matin on 2/6/18.
 */
public class JADocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(JADocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "";

    private static final String STOPLIST_FILE_PATH = "/ja/stoplist.txt";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("此奴", "其奴", "彼", "彼女"));
    private static Map<String, QTPosTags> TAGS = new HashMap<>();
    private static final String SENTENCE_DELIMITER = "(?<=[。！])";

    static {
        TAGS.put("その他", X);
        TAGS.put("その他-間投", INTJ);
        TAGS.put("フィラー", X);
        TAGS.put("副詞", ADV);
        TAGS.put("副詞-一般", ADV);
        TAGS.put("副詞-助詞類接続", ADV);
        TAGS.put("助動詞", AUX);
        TAGS.put("助詞", ADP);
        TAGS.put("助詞-並立助詞", CCONJ);
        TAGS.put("助詞-係助詞", ADP);
        TAGS.put("助詞-副助詞", ADP);
        TAGS.put("助詞-副助詞／並立助詞／終助詞", ADP);
        TAGS.put("助詞-副詞化", ADP);
        TAGS.put("助詞-接続助詞", ADP);
        TAGS.put("助詞-格助詞", ADP);
        TAGS.put("助詞-格助詞-一般", ADP);
        TAGS.put("助詞-格助詞-引用", ADP);
        TAGS.put("助詞-格助詞-連語", ADP);
        TAGS.put("助詞-特殊", ADP);
        TAGS.put("助詞-終助詞", ADP);
        TAGS.put("助詞-連体化", ADP);
        TAGS.put("助詞-間投助詞", ADP);
        TAGS.put("動詞", VERBB);
        TAGS.put("動詞-接尾", VERBB);
        TAGS.put("動詞-自立", VERBB);
        TAGS.put("動詞-非自立", AUX);
        TAGS.put("名詞", NOUNN);
        TAGS.put("名詞-サ変接続", NOUNN);
        TAGS.put("名詞-ナイ形容詞語幹", NOUNN);
        TAGS.put("名詞-一般", NOUNN);
        TAGS.put("名詞-代名詞", PRON);
        TAGS.put("名詞-代名詞-一般", PRON);
        TAGS.put("名詞-代名詞-縮約", PRON);
        TAGS.put("名詞-副詞可能", NOUNN);
        TAGS.put("名詞-動詞非自立的", NOUNN);
        TAGS.put("名詞-固有名詞", PROPN);
        TAGS.put("名詞-固有名詞-一般", PROPN);
        TAGS.put("名詞-固有名詞-人名", PROPN);
        TAGS.put("名詞-固有名詞-人名-一般", PROPN);
        TAGS.put("名詞-固有名詞-人名-名", PROPN);
        TAGS.put("名詞-固有名詞-人名-姓", PROPN);
        TAGS.put("名詞-固有名詞-地域", PROPN);
        TAGS.put("名詞-固有名詞-地域-一般", PROPN);
        TAGS.put("名詞-固有名詞-地域-国", PROPN);
        TAGS.put("名詞-固有名詞-組織", PROPN);
        TAGS.put("名詞-引用文字列", NOUNN);
        TAGS.put("名詞-形容動詞語幹", NOUNN);
        TAGS.put("名詞-接尾", NOUNN);
        TAGS.put("名詞-接尾-サ変接続", NOUNN);
        TAGS.put("名詞-接尾-一般", NOUNN);
        TAGS.put("名詞-接尾-人名", NOUNN);
        TAGS.put("名詞-接尾-副詞可能", NOUNN);
        TAGS.put("名詞-接尾-助動詞語幹", NOUNN);
        TAGS.put("名詞-接尾-助数詞", NOUNN);
        TAGS.put("名詞-接尾-地域", NOUNN);
        TAGS.put("名詞-接尾-形容動詞語幹", NOUNN);
        TAGS.put("名詞-接尾-特殊", NOUNN);
        TAGS.put("名詞-接続詞的", NOUNN);
        TAGS.put("名詞-数", NUM);
        TAGS.put("名詞-特殊", NOUNN);
        TAGS.put("名詞-特殊-助動詞語幹", NOUNN);
        TAGS.put("名詞-非自立", NOUNN);
        TAGS.put("名詞-非自立-一般", NOUNN);
        TAGS.put("名詞-非自立-副詞可能", NOUNN);
        TAGS.put("名詞-非自立-助動詞語幹", NOUNN);
        TAGS.put("名詞-非自立-形容動詞語幹", NOUNN);
        TAGS.put("形容詞", ADJ);
        TAGS.put("形容詞-接尾", ADJ);
        TAGS.put("形容詞-自立", ADJ);
        TAGS.put("形容詞-非自立", ADJ);
        TAGS.put("感動詞", INTJ);
        TAGS.put("接続詞", CCONJ);
        TAGS.put("接頭詞", X);
        TAGS.put("接頭詞-動詞接続", VERBB);
        TAGS.put("接頭詞-名詞接続", NOUNN);
        TAGS.put("接頭詞-形容詞接続", ADJ);
        TAGS.put("接頭詞-数接続", NUM);
        TAGS.put("記号", SYM);
        TAGS.put("記号-アルファベット", SYM);
        TAGS.put("記号-一般", SYM);
        TAGS.put("記号-句点", PUNCT);
        TAGS.put("記号-括弧閉", PUNCT);
        TAGS.put("記号-括弧開", PUNCT);
        TAGS.put("記号-空白", PUNCT);
        TAGS.put("記号-読点", PUNCT);
        TAGS.put("語断片", X);
        TAGS.put("連体詞", ADJ);
        TAGS.put("非言語音", X);
    }

    private static Pattern NounPhrase = Pattern.compile("N+");

    private Tokenizer tokenizer;

    public JADocumentHelper() {

    }

    @Override
    public JADocumentHelper init(){
        try {
            init(SENTENCES_FILE_PATH, STOPLIST_FILE_PATH, PRONOUNS);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public void loadNERModel(){
        logger.warn("Japanese doesn't have a separate POS model");
    }

    public QTPosTags getQtPosTag(String t){
        return TAGS.get(t);
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

        //Tokenizer : Mode is SERACH to be consistent with Lucene
        //https://lucene.apache.org/core/7_0_0/analyzers-kuromoji/org/apache/lucene/analysis/ja/JapaneseTokenizer.html#DEFAULT_MODE
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

        return tokStrings;
    }

    protected boolean isTagDC(String tag) {
        return tag.equals("助詞") || tag.startsWith("接") || tag.startsWith("記号");
    }

    @Override
    public boolean isSentence(String str, List<String> tokens) {
        int numTokens = tokens.size();
        //TODO: This is bad logic
        if (numTokens < 5 || numTokens > 750) {
            return false;
        }
        return true;
    }

    @Override
    public String[] getSentences(String text) {

        ArrayList<String> allSents = new ArrayList<>();

        String[] parts = text.split(SENTENCE_DELIMITER);
        for (String p : parts) {
            String trimmed = p
                    .replaceAll("^[ \\t\\u00A0\\u1680\\u180e\\u2000\\u200a\\u202f\\u205f\\u3000]+", "")
                    .replaceAll("[ \\t\\u00A0\\u1680\\u180e\\u2000\\u200a\\u202f\\u205f\\u3000]+$","");
            if (trimmed.isEmpty()) continue;
            allSents.add(trimmed);
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

        List<String> jaPosTags = getPosTagsJa(orig_str);

        StringBuilder allTags = new StringBuilder();
        ExtIntervalSimple [] tokenSpans = findAllSpans(orig_str, tokens);


        for (int i=0; i<jaPosTags.size(); i++){
            QTPosTags posTagCurrent = TAGS.get(jaPosTags.get(i));
            QTPosTags posTagBefore = i==0 ? X : TAGS.get(jaPosTags.get(i-1));
            if (posTagCurrent == NOUNN) {
                allTags.append("N");
            } else if (posTagCurrent == ADP && posTagBefore == NOUNN){
                allTags.append("N");
            } else {
                allTags.append("X");
            }
        }

        List<ExtIntervalSimple> intervals = new ArrayList<>();
        if (jaPosTags.size() != tokens.length) return intervals;

        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            int ss = tokenSpans[s].getStart();
            int ee = tokenSpans[e].getEnd();
            ExtIntervalSimple eit = new ExtIntervalSimple(ss, ee);
            String str = orig_str.substring(ss, ee);
            eit.setCustomData(str);
            eit.setStringValue(str);
            eit.setType(NOUN);
            intervals.add(eit);
        }

        // find verbs differently and by just regex search

        /*
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
        */

        return intervals;
    }

    public static void main(String[] args) throws Exception {
        JADocumentHelper jHelper = new JADocumentHelper();

        String str1 = "コーディング・フリスCEOは「消費者はシンプルなデザインを好むようになっており、われわれの予測よりも需要が低かった」と語った。入居者の毎日の生活をサポートいたします。";
        String str2 = "室内に設置されたセントラルコントローラーは、各種IoT機器の操作だけでなく、コンシェルジュによる、水漏れなどのトラブルや退居時の連絡など、入居者の毎日の生活をサポートいたします。";
        String str3 = "現在、日本のスマートスピーカー所有率は5%程度(*4)に過ぎませんが、米国で昨年よりAmazonが実施しており、公式ブログでは今年から日本でも実施していく予定とされる報酬プログラム(*5)の開始などにより、参入企業も増加していくと見込まれます。";


        List<String> tokens = jHelper.tokenize(str1);
        List<String> qtTags = jHelper.getPosTagsJa(str1);

        String s [] = jHelper.getSentences(str1);

        for (int i =0; i<tokens.size(); i++){
            System.out.println(tokens.get(i) + "  |  " + TAGS.get(qtTags.get(i)));
        }
    }
}
