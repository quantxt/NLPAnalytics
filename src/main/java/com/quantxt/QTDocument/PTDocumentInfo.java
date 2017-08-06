package com.quantxt.QTDocument;

import com.quantxt.doc.QTDocument;

public class PTDocumentInfo extends QTDocument {
	public PTDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
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
