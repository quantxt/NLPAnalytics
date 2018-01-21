package com.quantxt.doc;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.trie.Trie;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tartarus.snowball.ext.SpanishStemmer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

import static com.quantxt.doc.ENDocumentInfo.findSpan;
import static com.quantxt.doc.ENDocumentInfo.getENSentenceDetector;

/**
 * Created by matin on 1/20/18.
 */
public class ESDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);
    private static SentenceDetectorME sentenceDetector = null;
    private static POSTaggerME posModel = null;
    private static Analyzer analyzer;
    private static CharArraySet stopwords;
    private static HashSet<String> pronouns;
    private static Trie verbTree;

    public ESDocumentInfo (String body, String title) {
        super(body, title);
        language = Language.SPANISH;
    }

    public ESDocumentInfo (Elements body, String title) {
        super(body.html(), title);
        rawText = body.text();

    }

    public static boolean isStopWord(String p){
        return stopwords.contains(p);
    }

    public static void init(InputStream contextFile) throws Exception{
        if( sentenceDetector != null) return;

        pronouns = new HashSet<>();
        pronouns.add("él");
        pronouns.add("ella");

        analyzer = new SpanishAnalyzer();
        String model_base_dir = System.getenv("nlp_model_dir");
        if (model_base_dir == null){
            logger.error("nlp_model_dir is not set");
            return;
        }

        sentenceDetector = getENSentenceDetector();

        //	url = ESDocumentInfo.class.getClassLoader().getResource("es/es-pos-perceptron-500-0.bin");
        FileInputStream fis = new FileInputStream(model_base_dir + "/es/es-pos-perceptron-500-0.bin");
        POSModel model = new POSModel(fis);
        posModel = new POSTaggerME(model);

        stopwords = new CharArraySet(600, true);
        try {
            fis = new FileInputStream(model_base_dir + "/es/stoplist.txt");
            //	url = ESDocumentInfo.class.getClassLoader().getResource("es/stoplist.txt");
            List<String> sl = IOUtils.readLines(fis, "UTF-8");
            for (String s : sl){
                stopwords.add(s);
            }
        } catch (IOException e) {
            logger.equals(e.getMessage());
        }

        //verbs
        fis = new FileInputStream(model_base_dir + "/es/context.json");
        byte[] verbArr = contextFile != null ? IOUtils.toByteArray(contextFile) : IOUtils.toByteArray(fis);

        QTDocument doc = new ESDocumentInfo("", "");
        verbTree = doc.buildVerbTree(verbArr);

        logger.info("Spanish models initiliazed");
    }

    public static synchronized ArrayList<String> stemmer(String str){
        ArrayList<String> postEdit = new ArrayList<>();
        TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
        stream = new StopFilter(stream, stopwords);
        stream = new SnowballFilter(stream, new SpanishStemmer());
        CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);

        try {
            stream.reset();
            while (stream.incrementToken()) {
                String term = charTermAttribute.toString();
                postEdit.add(term);
            }
            stream.close();
        } catch (Exception e){

        }
        return postEdit;
    }

    @Override
    List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            ESDocumentInfo sDoc = new ESDocumentInfo("", s);
            sDoc.setDate(getDate());
            sDoc.setLink(getLink());
            sDoc.setLogo(getLogo());
            sDoc.setSource(getSource());
            sDoc.setLanguage(getLanguage());
            childs.add(sDoc);
        }
        return childs;
    }

    @Override
    public Trie getVerbTree(){
        return verbTree;
    }

    @Override
    public List<String> tokenize(String str) {
        List<String> tokens = new ArrayList<>();
        try {
            TokenStream result = analyzer.tokenStream(null, str);
            //	result = new SnowballFilter(result, "Spanish");

            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                tokens.add(resultAttr.toString());
            }
            result.close();
        } catch (Exception e){
            logger.error("Spanish Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    @Override
    public double [] getVectorizedTitle(QTExtract speaker){
        return speaker.tag(title);
    }

    @Override
    public String normalize(String string)
    {
        string = string.replaceAll("\\\\\"","\"");
        string = string.replaceAll("\\\\n","");
        string = string.replaceAll("\\\\r","");
        string = string.replaceAll("\\\\t","");
        string = string.replaceAll("[\\&\\!\\“\\”\\$\\=\\>\\<_\\'\\’\\-\\—\"\\‘\\.\\/\\(\\),?;:\\*\\|\\]\\[\\@\\#\\s+]+", " ");
        string = string.replaceAll("\\b\\d+\\b", "");
        string = string.toLowerCase();

        //This is thread safe
        ArrayList<String> postEdit = stemmer(string);
        return String.join(" ", postEdit);
    }

    @Override
    public void processDoc(){
 //       englishTitle = title;
 //       if (body == null || body.isEmpty())
 //           return;

 //       String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
 //       this.sentences = Arrays.asList(sentences);
    }

    @Override
    public String Translate(String text, Language inLang, Language outLang) {
        logger.error("Translation is not supported at this time");
        return null;
    }

    //sentence detc is NOT thread safe  :-/
    public static synchronized String [] getSentences(String text){
        return sentenceDetector.sentDetect(text);
    }


    @Override
    public boolean isStatement(String s) {
        return false;
    }

    public String [] getPosTags(String [] text)
    {
        String [] tags;
        synchronized (posModel)	{
            tags = posModel.tag(text);
        }
        return tags;
    }

    private static boolean isTagDC(String tag){
        return tag.equals("CS") || tag.equals("CC") || tag.startsWith("D");
    }

    //https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig,
                                                      String [] parts) {
        String lowerCase_orig = orig.toLowerCase();
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
                if ((tokenList.size() != 0) &&
                        (isTagDC(tag)) ||
                        (type.equals("N") && nextTag.startsWith("N")) ||
                        (type.equals("V") && nextTag.startsWith("V"))) {
                    tokenList.add(word);
                }
                continue;
            }
            if ((tag.startsWith("N") || (tag.startsWith("P")))) {
                if (!type.equals("N") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
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
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else if (tag.startsWith("V")) {
                if (!type.equals("V") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
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
            } else if (tag.startsWith("S") || tag.startsWith("R")) {
                if (tokenList.size() != 0) {
                    tokenList.add(word);
                }
            } else {
                if (!type.equals("X") && tokenList.size() > 0) {
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
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
            ExtInterval eit = findSpan(lowerCase_orig, tokenList);
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

    @Override
    HashSet<String> getPronouns() {
        return null;
    }

    @Override
    CharArraySet getStopwords() {
        return null;
    }
}
