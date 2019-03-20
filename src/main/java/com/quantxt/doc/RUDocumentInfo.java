package com.quantxt.doc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quantxt.types.DateTimeTypeConverter;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jsoup.select.Elements;
import com.quantxt.doc.helper.RUDocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.types.Entity;

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
        rawTitle = body.text();
    }

    @Override
    List<QTDocument> getChilds(boolean splitOnNewLine) {
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

        for (String s : sentences){
            RUDocumentInfo sDoc = new RUDocumentInfo("", s.trim(), helper);
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
    public List<ExtInterval> hack(QTDocument doc, String[] parts) {
        return helper.getNounAndVerbPhrases(doc.getTitle(), parts);
    }
}
