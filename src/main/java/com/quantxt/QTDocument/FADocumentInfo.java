package com.quantxt.QTDocument;

import com.memetix.mst.language.Language;
import com.quantxt.doc.QTDocument;

public class FADocumentInfo extends QTDocument {
	public FADocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.FARSI, Language.ENGLISH);
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
