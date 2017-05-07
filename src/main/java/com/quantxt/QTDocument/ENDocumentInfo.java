package com.quantxt.QTDocument;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import com.quantxt.doc.QTDocument;
import com.quantxt.nlp.Speaker;
import com.quantxt.nlp.types.TextNormalizer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import org.ahocorasick.trie.Trie;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ENDocumentInfo extends QTDocument {

	private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);

	private static Trie statementWords = null;
	private static boolean initialized = false;

	private String rawText;
	private String entity;
	private double score;

	private static SentenceDetectorME sentenceDetector = null;
	
	public ENDocumentInfo (String body, String title) {
		super(body, title);
	}

	public ENDocumentInfo (Elements body, String title) {
		super(body.html(), title);
		rawText = body.text();
	}
	
	public static void init() throws Exception{
		if( initialized) return;
		URL url = ENDocumentInfo.class.getClassLoader().getResource("en-sent.bin");
		InputStream sentenceModellIn = url.openStream();

		SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
		sentenceDetector = new SentenceDetectorME(sentenceModel);
		TextNormalizer.init();
		initialized = true;
		logger.info("English models initiliazed");
	}
	
	public double[] getTopicVec (String text){
		if (text == null || text.isEmpty())
			return null;
		//////remobe this
		return null;
	}

	public String getEntity(){return entity;}
	public void setEntity(String e){entity = e;}

	public double getScore(){return score;}
	public void setScore(double e){score = e;}

    @Override
	public void processDoc(){
		englishTitle = title;
		if (body == null || body.isEmpty())
			return;	

		String sentences[] = rawText == null ? getSentences(body) : getSentences(rawText);
		this.sentences = Arrays.asList(sentences);
	}

    @Override
    public String Translate(String text, Language inLang, Language outLang) {
        logger.error("Translation is not supported t this time");
        return null;
    }

	//sentence detc is NOT thread safe  :-/
    public static synchronized String [] getSentences(String text){
		return sentenceDetector.sentDetect(text);
	}

    @Override
    public boolean isStatement(String s) {
		return statementWords.containsMatch(s);
	}

	public static void main(String[] args) throws Exception {
		init();
		Speaker.init(new FileInputStream("cb.json") , null, null);

		ENDocumentInfo doc= new ENDocumentInfo("Federal Reserve Chair Janet Yellen said on Tuesday that investments in education and skills training are vital to address persistently high unemployment among lower-income and minority communities. Education levels have historically lagged in low- and moderate-income communities, particularly communities of color, Yellen said in prepared remarks on", "yellen sid");
		doc.processDoc();
		ArrayList<QTDocument> quotes = Speaker.extractQuotes(doc);
		for (QTDocument s : quotes){
			logger.info(s.getTitle());
		}
	}
}

