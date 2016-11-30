package com.quantxt.QTDocument;

import com.memetix.mst.language.Language;

public class KODocumentInfo extends QTDocument {
	public KODocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() {
		englishTitle = Translate(title, Language.KOREAN, Language.ENGLISH);
	}

	@Override
	protected boolean isStatement(String s) {
		// TODO Auto-generated method stub
		return false;
	}

}
