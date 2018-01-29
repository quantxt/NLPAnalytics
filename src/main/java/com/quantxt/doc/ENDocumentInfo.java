package com.quantxt.doc;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.types.ExtInterval;

/**
 * Created by matin on 1/20/18.
 */
public class ENDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);

    public ENDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.ENGLISH;
    }

    public ENDocumentInfo(String body, String title) {
        super(body, title, new ENDocumentHelper());
        language = Language.ENGLISH;
    }

    public ENDocumentInfo(Elements body, String title) {
        super(body.html(), title, new ENDocumentHelper());
        rawText = body.text();
    }

    @Override
    public List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? helper.getSentences(body)
                                             : helper.getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences) {
            ENDocumentInfo sDoc = new ENDocumentInfo("", s, helper);
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
