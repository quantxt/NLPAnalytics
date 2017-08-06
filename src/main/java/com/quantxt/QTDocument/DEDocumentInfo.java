package com.quantxt.QTDocument;

import com.quantxt.doc.QTDocument;

public class DEDocumentInfo extends QTDocument {
	public DEDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.GERMAN, Language.ENGLISH);
	}

	@Override
	public String Translate(String text, Language inLang, Language outLang) {
		return null;
	}

	@Override
	public boolean isStatement(String s) {
		// TODO Auto-generated method stub
		return false;
	}
}
