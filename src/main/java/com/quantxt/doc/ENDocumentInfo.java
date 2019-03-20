package com.quantxt.doc;

import java.util.ArrayList;
import java.util.List;

import com.quantxt.util.StringUtil;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ENDocumentHelper;

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
        rawTitle = body.text();
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
            sentences = rawTitle == null ? helper.getSentences(body)
                    : helper.getSentences(rawTitle);
        }

        for (String s : sentences) {
            ENDocumentInfo sDoc = new ENDocumentInfo("", s.trim(), helper);
            sDoc.setRawTitle(s.trim());
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
