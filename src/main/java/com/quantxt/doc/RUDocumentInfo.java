package com.quantxt.doc;

import java.util.ArrayList;
import java.util.List;

import com.quantxt.helper.types.ExtIntervalSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jsoup.select.Elements;
import com.quantxt.doc.helper.RUDocumentHelper;
import com.quantxt.helper.types.ExtInterval;

/**
 * Created by matin on 1/20/18.
 */
public class RUDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(RUDocumentInfo.class);

    public RUDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo (String body, String title) {
        super(body, title, new RUDocumentHelper());
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo (Elements body, String title) {
        super(body.html(), title, new RUDocumentHelper());
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
            RUDocumentInfo sDoc = new RUDocumentInfo("", s.trim(), helper);
            sDoc.setDate(getDate());
            sDoc.setLink(getLink());
            sDoc.setSource(getSource());
            sDoc.setLanguage(getLanguage());
            childs.add(sDoc);
        }
        return childs;
    }

    @Override
    public double [] getVectorizedTitle(QTExtract speaker){
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

    //http://corpus.leeds.ac.uk/mocky/ru-table.tab
    public List<ExtIntervalSimple> hack(QTDocument doc, String[] parts) {
        return helper.getNounAndVerbPhrases(doc.getTitle(), parts);
    }
}
