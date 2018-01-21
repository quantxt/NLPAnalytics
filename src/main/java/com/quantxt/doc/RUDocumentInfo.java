package com.quantxt.doc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.DateTimeTypeConverter;
import com.quantxt.trie.Trie;
import com.quantxt.types.Entity;
import opennlp.tools.sentdetect.SentenceDetectorME;
import org.annolab.tt4j.TokenHandler;
import org.annolab.tt4j.TreeTaggerWrapper;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.joda.time.DateTime;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import static com.quantxt.doc.ENDocumentInfo.findSpan;
import static com.quantxt.doc.ENDocumentInfo.getENSentenceDetector;

/**
 * Created by matin on 1/20/18.
 */
public class RUDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(RUDocumentInfo.class);
    private static SentenceDetectorME sentenceDetector = null;
    private static TreeTaggerWrapper<String> taggerWrapper;
    private static Analyzer analyzer;
    private static CharArraySet stopwords;
    private static HashSet<String> pronouns;
    private static Trie verbTree;

    public RUDocumentInfo (String body, String title) {
        super(body, title);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo (Elements body, String title) {
        super(body.html(), title);
        rawText = body.text();
    }

    public static boolean isStopWord(String p){
        return stopwords.contains(p);
    }

    public static void init(InputStream contextFile) throws Exception{
        //Already initialized
        if (sentenceDetector != null) return;
        String model_base_dir = System.getenv("nlp_model_dir");
        if (model_base_dir == null){
            logger.error("nlp_model_dir is not set");
            return;
        }


//		analyzer = new RussianAnalyzer(CharArraySet.EMPTY_SET);
        analyzer = new RussianAnalyzer();

        sentenceDetector = getENSentenceDetector();

        taggerWrapper = new TreeTaggerWrapper<>();
        System.setProperty("treetagger.home", model_base_dir+ "/ru");
        taggerWrapper.setModel(model_base_dir + "/ru/russian-utf8.par");

        stopwords = new CharArraySet(800, true);
        try {
            FileInputStream fis = new FileInputStream(model_base_dir + "/ru/stoplist.txt");
            //	url = RUDocumentInfo.class.getClassLoader().getResource("ru/stoplist.txt");
            List<String> sl = IOUtils.readLines(fis, "UTF-8");
            for (String s : sl){
                stopwords.add(s);
            }
        } catch (IOException e) {
            logger.equals(e.getMessage());
        }

        //verbs

        FileInputStream fis = new FileInputStream(model_base_dir + "/ru/context.json");
        byte[] verbArr = contextFile != null ? IOUtils.toByteArray(contextFile) : IOUtils.toByteArray(fis);
        QTDocument doc = new RUDocumentInfo("", "");
        verbTree = doc.buildVerbTree(verbArr);
        pronouns = new HashSet<>();
        pronouns.add("Он");
        pronouns.add("Его");
        pronouns.add("Ему");
        pronouns.add("онá");
        pronouns.add("oна");
        pronouns.add("oн");
        pronouns.add("eму");
        pronouns.add("eго");

        logger.info("Russian models initiliazed");
    }

    public double[] getTopicVec (String text){
        if (text == null || text.isEmpty())
            return null;
        //////remove this
        return null;
    }

    @Override
    List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            RUDocumentInfo sDoc = new RUDocumentInfo("", s);
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
            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                tokens.add(resultAttr.toString());
            }
            result.close();
        } catch (Exception e){
            logger.error("Russian Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    public static synchronized ArrayList<String> stemmer(String str){
        ArrayList<String> postEdit = new ArrayList<>();

        try {
            TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
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
        List<String> output = new ArrayList<>();
        //	synchronized (taggerWrapper)	{
        try {
            taggerWrapper.setHandler(new TokenHandler<String>() {
                public void token(String token, String pos, String lemma) {
                    //	logger.info(token + " / " + pos + " / " + lemma);
                    output.add(pos);
                }
            });
            taggerWrapper.process(text);
        } catch (Exception e){

        }
        //	}

        return output.toArray(new String[output.size()]);
    }

    private boolean isTagDC(String tag){
        return tag.equals("C") || tag.equals("I") || tag.startsWith("S");
    }

    @Override
    public boolean isStatement(String s) {
        return false;
    }


    //http://corpus.leeds.ac.uk/mocky/ru-table.tab
    public List<ExtInterval> hack(String orig,
                                  String[] parts){
        return getNounAndVerbPhrases(orig, parts);
    }

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig,
                                                      String[] parts)
    {
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
        for (int j = numTokens-1; j>=0; j--){
            final String tag = taags[j];
            final String word = parts[j];
            if ( isTagDC(tag) ) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) &&
                        (isTagDC(tag) ) ||
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
            } else if (tag.startsWith("Q") || tag.startsWith("R")){
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
                lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                eit.setType(type);
                phrases.add(eit);
            }
        }

        Collections.reverse(phrases);
        return phrases;
    }

    public static void main(String[] args) throws Exception {
        init(null);
        ESDocumentInfo.init(null);
        ENDocumentInfo.init(null);
        //	String title = "Силуанов: законопроект, запрещающий россиянам пользоваться криптовалютами уже готов Биткоин против Apple: экономисты прогнозируют рост объема торгов криптовалютой до уровня торга акициями IT-гиганта Биткоин продолжает рост и уже превысил $14 000 <a href=\"\" title=\"\"> <abbr title=\"\"> <acronym title=\"\"> <b> <blockquote cite=\"\"> <cite> <code> <del datetime=\"\"> <em> <i> <q cite=\"\"> <strike> <strong> Copyright © 2018 Русский Еврей - All Rights Reserved Powered by WordPress & Atahualpa";

        //	String body = "В Китае , где расположено 80% мощностей по “добыче” биткоинов, могут запретить майнинг. Представители государственных агентств, опасаясь финансовых рисков, выпустили на прошлой неделе обращение к местным властям с призывом заняться прекращением деятельности, в ходе которой “майнят” криптовалюты, пишет The Wall Street Journal. Речь идет об обращении от имени Leading Group of Internet Financial Risks Remediation — госагентства, занимающегося изучением финансовых рисков в интернете, которое стало инициатором такой идеи. Группа сама по себе не контролирует использование электроэнергии в стране, но это влиятельный политический игрок, возглавляемый заместителем управляющего Народного банка Китая Паном Гоншеном. Представители группы на местах должны отчитываться о прогрессе по устранению майнеров в своем регионе каждый месяц.";

        RUDocumentInfo doc = new RUDocumentInfo("", "");

        Map<String, Entity[]> entMap = new HashMap<>();
        ArrayList<Entity> entityArray1 = new ArrayList<>();
        entityArray1.add(new Entity("Bitcoin" , new String[]{"Bitcoin" , "Биткоин"} , true));
        entMap.put("Bitcoin" , entityArray1.toArray(new Entity[entityArray1.size()]));
        QTExtract enx = new Speaker(entMap, (String)null, null);

        Gson JODA_GSON = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter())
                .create();
        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/git/TxtAlign/o.txt"));
        try {
            String line = null;
            while ((line = br.readLine() ) != null) {

                QTDocument p = JODA_GSON.fromJson(line, QTDocument.class);
                QTDocument parent = QTDocumentFactory.createQTDoct(p.getBody(), p.getTitle());
                logger.info("L: " + parent.getLanguage());
                parent.processDoc();

                //    Files.write(Paths.get("o.txt"), (theString + "\n").getBytes(), StandardOpenOption.APPEND);

                parent.setDate(p.getDate());
                parent.setLink(p.getLink());
                parent.setSource(p.getSource());

                ArrayList<QTDocument> children = null;
                synchronized (enx) {
                    children = parent.extractEntityMentions(enx);
                }
                logger.info("size: " + children.size());
				/*

				List<String> p2 = doc.tokenize(line);
				logger.info(line);

				List<ExtInterval> res = doc.hack(line, p2.toArray(new String[p2.size()]));

				if (res == null) continue;
				*/
                logger.info("=========");

            }
        } finally {
            br.close();
        }

		/*

		QTDocument doc = QTDocumentFactory.createQTDoct(body, title);
		logger.info("lang is: " + doc.getLanguage());
		doc.processDoc();

		ArrayList<Entity> entityArray1 = new ArrayList<>();
		entityArray1.add(new Entity("Wall Street" , null , true));
		entityArray1.add(new Entity("China" , new String[]{"Китае"} , true));

		Map<String, Entity[]> entMap = new HashMap<>();
		entMap.put("Company" , entityArray1.toArray(new Entity[entityArray1.size()]));
		QTExtract enx = new Speaker(entMap, (String)null, null);
		ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
		Gson gson =new Gson();
		for (QTDocument d : docs){
			Map<String, LinkedHashSet<String>> entityMap = d.getEntity();
			logger.info(d.getDocType() + " / " + gson.toJson(entityMap));
		}
		*/
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
