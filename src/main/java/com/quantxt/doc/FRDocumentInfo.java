package com.quantxt.doc;

import com.quantxt.doc.helper.FRDocumentHelper;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by matin on 5/28/18.
 */
public class FRDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(FRDocumentInfo.class);

    public FRDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.FRENCH;
    }

    public FRDocumentInfo(String body, String title) {
        super(body, title, new FRDocumentHelper());
        language = Language.FRENCH;
    }

    public FRDocumentInfo(Elements body, String title) {
        super(body.html(), title, new FRDocumentHelper());
        rawTitle = body.text();
    }

    @Override
    List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawTitle == null ? helper.getSentences(body)
                : helper.getSentences(rawTitle);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            FRDocumentInfo sDoc = new FRDocumentInfo("", s, helper);
            sDoc.setDate(getDate());
            sDoc.setLink(getLink());
            sDoc.setLogo(getLogo());
            sDoc.setSource(getSource());
            sDoc.setLanguage(getLanguage());
            childs.add(sDoc);
        }
        return childs;
    }

    @Override
    public double[] getVectorizedTitle(QTExtract speaker) {
        return speaker.tag(title);
    }

    @Override
    public String Translate(String text, Language inLang, Language outLang) {
        logger.error("Translation is not supported at this time");
        return null;
    }

    @Override
    public boolean isStatement(String s) {
        return false;
    }
}