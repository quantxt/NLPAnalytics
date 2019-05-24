package com.quantxt.doc;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ESDocumentHelper;

/**
 * Created by matin on 1/20/18.
 */
public class ESDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ESDocumentInfo.class);


    public ESDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.SPANISH;
    }

    public ESDocumentInfo(String body, String title) {
        super(body, title, new ESDocumentHelper());
        language = Language.SPANISH;
    }

    public ESDocumentInfo(Elements body, String title) {
        super(body.html(), title, new ESDocumentHelper());
    }

    @Override
    public List<QTDocument> getChilds(boolean splitOnNewLine) {
        List<QTDocument> childs = new ArrayList<>();
        if (body == null || body.isEmpty())
            return childs;

        String[] sentences = null;
        if (splitOnNewLine){
            sentences = body.split("[\\n\\r]+");
        } else {
            sentences = helper.getSentences(body);
        }
        for (String s : sentences){
            ESDocumentInfo sDoc = new ESDocumentInfo("", s.trim(), helper);
            sDoc.setDate(getDate());
            sDoc.setLink(getLink());
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
