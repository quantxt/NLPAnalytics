package com.quantxt.doc;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.trie.Trie;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

/**
 * Created by matin on 1/20/18.
 */
public class ENDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);
    protected static SentenceDetectorME sentenceDetector = null;
    private static POSTaggerME posModel = null;
    private static Analyzer analyzer;
    private static CharArraySet stopwords;
    private static HashSet<String> pronouns;
    private static Trie verbTree;

    public ENDocumentInfo (String body, String title) {
        super(body, title);
        language = Language.ENGLISH;
    }

    public ENDocumentInfo (Elements body, String title) {
        super(body.html(), title);
        rawText = body.text();
    }

    public static boolean isStopWord(String p){
        return stopwords.contains(p);
    }

    protected static synchronized SentenceDetectorME getENSentenceDetector() throws IOException {
        if ( !(sentenceDetector == null)) return sentenceDetector;
        String model_base_dir = System.getenv("nlp_model_dir");
        if (model_base_dir == null){
            logger.error("Model DIR is not set");
            return null;
        }

        FileInputStream fis = new FileInputStream(model_base_dir + "/en/en-sent.bin");
        SentenceModel sentenceModel = new SentenceModel(fis);
        fis.close();
        return new SentenceDetectorME(sentenceModel);
    }


    public static void init(InputStream contextFile) throws Exception{
        //Already initialized
        if (sentenceDetector != null) return;

        pronouns = new HashSet<>();
        pronouns.add("he");
        pronouns.add("she");

        String model_base_dir = System.getenv("nlp_model_dir");
        if (model_base_dir == null){
            logger.error("nlp_model_dir is not set");
            return;
        }

        analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
        sentenceDetector = getENSentenceDetector();

        //	url = ENDocumentInfo.class.getClassLoader().getResource("en/en-pos-maxent.bin");
        FileInputStream fis = new FileInputStream(model_base_dir + "/en/en-pos-maxent.bin");
        POSModel model = new POSModel(fis);
        posModel = new POSTaggerME(model);

        stopwords = new CharArraySet(800, true);
        try {
            //		url = ENDocumentInfo.class.getClassLoader().getResource("en/stoplist.txt");
            fis = new FileInputStream(model_base_dir + "/en/stoplist.txt");
            List<String> sl = IOUtils.readLines(fis, "UTF-8");
            for (String s : sl){
                stopwords.add(s);
            }
        } catch (IOException e) {
            logger.equals(e.getMessage());
        }

        //verbs

        //	url = ENDocumentInfo.class.getClassLoader().getResource("en/context.json");
        fis = new FileInputStream(model_base_dir + "/en/context.json");
        byte[] verbArr = contextFile != null ? IOUtils.toByteArray(contextFile) : IOUtils.toByteArray(fis);
        QTDocument doc = new ENDocumentInfo("", "");
        verbTree = doc.buildVerbTree(verbArr);
        logger.info("English models initiliazed");
    }

    @Override
    public List<QTDocument> getChilds(){
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            ENDocumentInfo sDoc = new ENDocumentInfo("", s);
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
            //		result = new SnowballFilter(result, "English");

            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                tokens.add(resultAttr.toString());
            }
            result.close();
        } catch (Exception e){
            logger.error("English Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    public static ExtInterval findSpan(String str,
                                          List<String> tokenList)
    {
        if (tokenList == null || tokenList.size() == 0) return null;

        int shift = 0;
        int end = str.length();
        while (str.length() >= end) {
            String str_part = str.substring(0, end);
            int start = str.substring(0, end).lastIndexOf(tokenList.get(0));
            if (start ==-1){
                logger.error("no first match");
                return null;
            }
            end = start;

            for (int c = 0; c < tokenList.size(); c++) {
                String t = tokenList.get(c);
                //		int t_length = t.length();
                int n_start = str.indexOf(t, start);
                if (n_start >= 0) {
                    //	str = str.substring(n_start);
                    int sh = str.indexOf(' ', n_start);
                    if (sh > 0) {
                        shift = sh;
                    } else if (sh == -1 && (c == tokenList.size() - 1)) {
                        shift = str.length();
                    } else {
                        //wrong token
                        shift = 0;
                        break;
                    }
                }
            }
            if (shift > 0) {
                return new ExtInterval(start, shift);
            }
        }
        return null;
    }

    public static synchronized ArrayList<String> stemmer(String str){
        ArrayList<String> postEdit = new ArrayList<>();

        try {
            TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
            stream = new StopFilter(stream, stopwords);
            CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
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
    public double [] getVectorizedTitle(QTExtract speaker){
        return speaker.tag(title);
    }

    @Override
    public String normalize(String string)
    {
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

    //pos tagger is NOT thread safe  :-/

    @Override
    public String [] getPosTags(String [] text)
    {
        String [] tags;
        synchronized (posModel)	{
            tags = posModel.tag(text);
        }
        return tags;
    }

    private boolean isTagDC(String tag){
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC") || tag.equals("DT");
    }

    @Override
    public boolean isStatement(String s) {
        return false;
    }


    //https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig,
                                                      String[] parts){
        String lowerCase_orig = orig.toLowerCase();
        int numTokens = parts.length;
        String [] taags = getPosTags(parts);
        List<String> tokenList= new ArrayList<>();
        List<ExtInterval> phrases = new ArrayList<>();
        String type = "X";
        for (int j=numTokens-1; j>=0; j--){
            final String tag = taags[j];
            final String word = parts[j];
            if ( isTagDC(tag) ) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) &&
                        (isTagDC(tag) ) ||
                        (type.equals("N") && nextTag.startsWith("NN") ) ||
                        (type.equals("V") && nextTag.startsWith("VB") ))
                {
                    tokenList.add(word);
                }
                continue;
            }
            if (tag.startsWith("NN") || tag.equals("PRP")){
                if (!type.equals("N") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
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
					eit.setType(type);
					phrases.add(eit);
					*/
                    tokenList.clear();
                }
                type = "N";
                tokenList.add(word);
            }  else if ( tag.startsWith("JJ")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            } else if (tag.startsWith("VB")){
                if (!type.equals("V") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 2 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
					/*
					String str = String.join(" ", tokenList);
					int start = orig.indexOf(str);
					if (start == -1){
						logger.error("NOT FOUND 2 " + str);
					}
					ExtInterval eit = new ExtInterval(start, start+str.length());
					eit.setType(type);
					phrases.add(eit);
					*/
                    tokenList.clear();
                }
                type = "V";
                tokenList.add(word);
            } else if (tag.startsWith("MD") || tag.startsWith("RB")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            }  else {
                if (!type.equals("X") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 3 " + String.join(" ", tokenList));
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
					/*
					String str = String.join(" ", tokenList);
					int start = orig.indexOf(str);
					if (start == -1){
						logger.error("NOT FOUND 3 " + str);
					}
					ExtInterval eit = new ExtInterval(start, start+str.length());
					eit.setType(type);
					phrases.add(eit);
					*/
                    tokenList.clear();
                }
                type = "X";
            }
        }

        if (!type.equals("X") && tokenList.size() >0){
            Collections.reverse(tokenList);
            ExtInterval eit = findSpan(lowerCase_orig, tokenList);

            if (eit == null) {
                logger.error("NOT FOUND 4 '" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType(type);
                phrases.add(eit);
            }
			/*
			ExtInterval eit = new ExtInterval(start, start+str.length());
			String str = String.join(" ", tokenList);
			int start = orig.indexOf(str);
			eit.setType(type);
			phrases.add(eit);
			*/
        }

        Collections.reverse(phrases);
        return phrases;
    }

    public static void main(String[] args) throws Exception {
        ESDocumentInfo.init(null);
        ENDocumentInfo.init(null);
        String title = "Деньги благотворительного фогда пойдут на исследования преимуществ MDMA в области здравоохранения. MDMA –психоактивное соединение из ряда амфетаминов";
        String body = "В Китае , где расположено 80% мощностей по “добыче” биткоинов, могут запретить майнинг. Представители государственных агентств, опасаясь финансовых рисков, выпустили на прошлой неделе обращение к местным властям с призывом заняться прекращением деятельности, в ходе которой “майнят” криптовалюты, пишет The Wall Street Journal. Речь идет об обращении от имени Leading Group of Internet Financial Risks Remediation — госагентства, занимающегося изучением финансовых рисков в интернете, которое стало инициатором такой идеи. Группа сама по себе не контролирует использование электроэнергии в стране, но это влиятельный политический игрок, возглавляемый заместителем управляющего Народного банка Китая Паном Гоншеном. Представители группы на местах должны отчитываться о прогрессе по устранению майнеров в своем регионе каждый месяц.";

        ENDocumentInfo doc = new ENDocumentInfo(body, title);

//		BufferedReader br = new BufferedReader(new FileReader("/Users/matin/git/TxtAlign/out.txt"));
//		try {
        String line = "Bitcoin by the end of the year will cost twice as much - an expert How much will bitcoin cost by the end of 2017, the expert said. The value of the crypto currency on Thursday was $ 14 thousand. BISHKEK, Dec 7 - Sputnik.";
//			while ((line = br.readLine() ) != null) {
        logger.info(line);
        String[] parts = line.split("\\s+");
        List<ExtInterval> res = doc.getNounAndVerbPhrases(line, parts);
        logger.info("=========");
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
