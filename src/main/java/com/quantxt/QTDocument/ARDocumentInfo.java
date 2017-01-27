package com.quantxt.QTDocument;

import com.memetix.mst.language.Language;

public class ARDocumentInfo extends QTDocument {
	public ARDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.ARABIC, Language.ENGLISH);
	}

	@Override
	protected boolean isStatement(String s) {
		// TODO Auto-generated method stub
		return false;
	}
}

