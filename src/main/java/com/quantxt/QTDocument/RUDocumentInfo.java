package com.quantxt.QTDocument;

import com.memetix.mst.language.Language;

public class RUDocumentInfo extends QTDocument {
	public RUDocumentInfo (String body, String title){
		super(body, title);
	}
	
	public void processDoc() throws Exception{
		englishTitle = Translate(title, Language.RUSSIAN, Language.ENGLISH);
	}

	@Override
	protected boolean isStatement(String s) {
		// TODO Auto-generated method stub
		return false;
	}

}
