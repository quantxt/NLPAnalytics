package com.quantxt.QTDocument;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.memetix.mst.language.Language;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class NLDocumentInfo extends QTDocument {
	private static Pattern statementWords;
	private static SentenceDetectorME sentenceDetector = null;
	private static NameFinderME nameFinder = null;
	private static NameFinderME organizationFinder = null;
	private static Tokenizer tokenizer = null;
	
	public NLDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public static void init() throws Exception{
		statementWords = Pattern.compile("(?i)\\bzegt|vertelt|zei|meldde|aldus\\b");

		InputStream sentenceModellIn = new FileInputStream("models/nl-sent.bin");
		InputStream nerPersonmodelIn = new FileInputStream("models/nl-ner-person.bin");
		InputStream nerOrganizationnmodelIn = new FileInputStream("models/nl-ner-organization.bin");
		InputStream tokenizerModelIn = new FileInputStream("models/nl-token.bin");

		SentenceModel sentenceModel = new SentenceModel(sentenceModellIn);
		sentenceDetector = new SentenceDetectorME(sentenceModel);
		TokenNameFinderModel nerPersonModel = new TokenNameFinderModel(nerPersonmodelIn);
		nameFinder = new NameFinderME(nerPersonModel);
		TokenNameFinderModel nerOrganizationModel = new TokenNameFinderModel(nerOrganizationnmodelIn);
		organizationFinder = new NameFinderME(nerOrganizationModel);
		TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelIn);
		tokenizer = new TokenizerME(tokenizerModel);
		System.out.println("dutch models initiliazed");
		
	}
	
	public void processDoc() {
	    englishTitle = Translate(title, Language.DUTCH, Language.ENGLISH);
	    if (body == null || body.isEmpty())
			return;		
		String sentences[] = sentenceDetector.sentDetect(body);
		getSentenceNER(sentences, nameFinder, organizationFinder);
	}

	@Override
	protected boolean isStatement(String s) {
		Matcher m = statementWords.matcher(s);
		if (m.find())
			return true;
		return false;
	}

	@Override
	protected String[] getTokens(String s) {
		return tokenizer.tokenize(s);
	}
}
