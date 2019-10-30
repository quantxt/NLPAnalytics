package com.quantxt.doc;

import com.google.gson.Gson;
import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.JADocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Created by matin on 2/6/18.
 */
public class JADocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(JADocumentInfo.class);

    private static HashSet<String> puntuations = new HashSet<>(Arrays.asList("・", "？", "。", "！", "．"));

    public JADocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.JAPANESE;
    }

    public JADocumentInfo (String body, String title) {
        super(body, title, new JADocumentHelper());
        language = Language.JAPANESE;
    }

    public JADocumentInfo (Elements body, String title) {
        super(body.html(), title, new JADocumentHelper());
    }

    @Override
    public List<QTDocument> getChilds(boolean splitOnNewLine) {
        List<QTDocument> childs = new ArrayList<>();
        if (body == null || body.isEmpty())
            return childs;

        if (splitOnNewLine) {
            String[] sentences = body.split("\\n");
            for (String s : sentences) {
                ENDocumentInfo sDoc = new ENDocumentInfo("", s.trim(), helper);
                sDoc.setDate(getDate());
                sDoc.setLink(getLink());
                sDoc.setSource(getSource());
                sDoc.setLanguage(getLanguage());
                childs.add(sDoc);
            }
        } else {
            List<String> tokens = helper.tokenize(body);
            List<String> postags = ((JADocumentHelper) helper).getPosTagsJa(body);
            ArrayList<String> sentTokens = new ArrayList();
            JADocumentInfo sDoc;
            int start = 0;

            for (int i = 0; i < postags.size(); i++) {
                String token = tokens.get(i);

                String tag = postags.get(i);
                sentTokens.add(token);
                CommonQTDocumentHelper.QTPosTags qtPosTag = ((JADocumentHelper) helper).getQtPosTag(tag);
                if (token.equals("。") || puntuations.contains(token) || qtPosTag == CommonQTDocumentHelper.QTPosTags.PUNCT)
                {
                    int end = body.indexOf(token, start) + token.length();
                    String raw = body.substring(start, end);
                    start = end;
                    sDoc = new JADocumentInfo("", raw, helper);
                    sDoc.setDate(getDate());
                    sDoc.setLink(getLink());
                    sDoc.setSource(getSource());
                    sDoc.setLanguage(getLanguage());
                    childs.add(sDoc);
                    sentTokens = new ArrayList();
                }
            }

            if (sentTokens.size() > 0) {
                String raw = body.substring(start);
                sDoc = new JADocumentInfo("", raw, helper);
                sDoc.setDate(getDate());
                sDoc.setLink(getLink());
                sDoc.setSource(getSource());
                sDoc.setLanguage(getLanguage());
                childs.add(sDoc);
            }
        }

        return childs;
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
