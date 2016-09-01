package com.quantxt.QTDocument;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.memetix.mst.language.Language;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;

public class ESDocumentInfo extends QTDocument {
	
	private static Pattern statementWords;
	private static NameFinderME nameFinder = null;
	private static NameFinderME organizationFinder = null;

	public ESDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public static void init() throws Exception{
		statementWords = Pattern.compile("(?i)\\bdijo|dice|añadió\\b");
//		InputStream sentenceModellIn = new FileInputStream("models/es-sent.bin");
		InputStream nerPersonmodelIn = new FileInputStream("models/es-ner-person.bin");
		InputStream nerOrganizationnmodelIn = new FileInputStream("models/es-ner-organization.bin");
//		InputStream tokenizerModelIn = new FileInputStream("models/es-token.bin");

		TokenNameFinderModel nerPersonModel = new TokenNameFinderModel(nerPersonmodelIn);
		nameFinder = new NameFinderME(nerPersonModel);
		TokenNameFinderModel nerOrganizationModel = new TokenNameFinderModel(nerOrganizationnmodelIn);
		organizationFinder = new NameFinderME(nerOrganizationModel);
		System.out.println("spanish models initiliazed");
	}
	
	
	public void processDoc() throws Exception{
		if (body == null || body.isEmpty())
			return;		

		String sentences[] = body.split("\\.");
//		System.out.println(body);
	    for(String sentence: sentences) {
	    	if (isStatement(sentence))
	    		addStatement(sentence);
	    	else
	    		continue;

//	    	String tokens[] = tokenizer.tokenize(sentence);
	    	String tokens[] = sentence.split("[\\s\\:\\.\\,\\)\\(\\-\"]+");
	    	Span nameSpans[] 			= nameFinder.find(tokens);
	    	Span organizationSpans[] 	= organizationFinder.find(tokens);
	    	for (Span ns : nameSpans)
	    	{
	    		String p = "";
	    		for (int i= ns.getStart(); i < ns.getEnd(); i++)
	    			p += tokens[i] + " ";
	    		p = p.substring(0, p.length() -1 );
	    		addPerson(p);
	    	}
	    	for (Span os : organizationSpans)
	    	{
	    		String p = "";
	    		for (int i= os.getStart(); i < os.getEnd(); i++)
	    			p += tokens[i] + " ";
	    		p = p.substring(0, p.length() -1 );
	    		addOrganization(p);
	    	}
 	    }
	    nameFinder.clearAdaptiveData();
	    organizationFinder.clearAdaptiveData();
	    
	    englishTitle = Translate(title, Language.SPANISH, Language.ENGLISH);
	}

	@Override
	protected boolean isStatement(String s) {
		Matcher m = statementWords.matcher(s);
		if (m.find())
			return true;
		return false;
	}
}
