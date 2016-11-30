package com.quantxt.QTDocument;

import com.memetix.mst.language.Language;

public class ITDocumentInfo extends QTDocument {
	public ITDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.ITALIAN, Language.ENGLISH);
	}

	@Override
	protected boolean isStatement(String s) {
		// TODO Auto-generated method stub
		return false;
	}
}
