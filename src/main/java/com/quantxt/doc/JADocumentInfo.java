package com.quantxt.doc;

import com.quantxt.doc.helper.JADocumentHelper;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        rawText = body.text();
    }

    @Override
    public List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        ArrayList<String> sentences = new ArrayList<>();
        List<String> tokens = helper.tokenize(body);
        String [] postags = ((JADocumentHelper)helper).getPosTagsJa(body);
        ArrayList<String> sentTokens = new ArrayList();
        for (int i=0; i< postags.length; i++){
            String token = tokens.get(i);
            String tag = postags[i];
            sentTokens.add(token);
            if (token.equals("。")){  // japanese preiod.
                sentences.add(String.join(" ", sentTokens));
                sentTokens = new ArrayList<>();
            }else if (tag.equals("記号-句点")){
                sentences.add(String.join(" ", sentTokens));
                sentTokens = new ArrayList<>();
            } else if (tag.equals("記号") && ( puntuations.contains(token))) {
                sentences.add(String.join(" ", sentTokens));
                sentTokens = new ArrayList<>();
            }
        }
        if (sentTokens.size() > 0){
            sentences.add(String.join(" ", sentTokens));
        }

        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            JADocumentInfo sDoc = new JADocumentInfo("", s, helper);
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
}
