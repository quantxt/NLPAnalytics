package com.quantxt.doc;

import com.atilika.kuromoji.ipadic.Token;
import com.google.gson.Gson;
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
        rawTitle = body.text();
    }

    @Override
    public List<QTDocument> getChilds() {
        List<QTDocument> childs = new ArrayList<>();
        if (body == null || body.isEmpty())
            return childs;

        List<String> tokens = helper.tokenize(body);
        List<Token> postags = ((JADocumentHelper)helper).getPosTagsJa(body);
        ArrayList<String> sentTokens = new ArrayList();
        JADocumentInfo sDoc;
        int start = 0;

        for (int i=0; i< postags.size(); i++){
            String token = tokens.get(i);
            Token PosTag = postags.get(i);
            String tag = PosTag.getPartOfSpeechLevel1();
            sentTokens.add(token);
            if (token.equals("。") || puntuations.contains(token)
                    || tag.equals("記号-句点") || tag.equals("記号-括弧閉") || tag.equals("記号-括弧開")
                    || tag.equals("記号-空白") || tag.equals("記号-読点") ) // japanese preiod.
            {
                int end = PosTag.getPosition()+ token.length();
                String raw = body.substring(start, end);
                start = end;
                sDoc = new JADocumentInfo("", raw, helper);
                sDoc.setDate(getDate());
                sDoc.setLink(getLink());
                sDoc.setLogo(getLogo());
                sDoc.setSource(getSource());
                sDoc.setLanguage(getLanguage());
                childs.add(sDoc);
                sentTokens = new ArrayList();
            }
        }

        if (sentTokens.size() > 0){
            String raw = body.substring(start);
            sDoc = new JADocumentInfo("", raw, helper);
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

    public static void main(String[] args) throws Exception {
        JADocumentHelper helper = new JADocumentHelper();
        File file = new File("/Users/matin/git/QTCurat/o.txt");

        BufferedReader br = new BufferedReader(new FileReader(file));
        Gson gson = new Gson();

        String st;
        while ((st = br.readLine()) != null){
            try {
                JADocumentInfo doc = gson.fromJson(st, JADocumentInfo.class);
                doc.setHelper(helper);
                List<QTDocument> childs = doc.getChilds();
                logger.info(doc.getTitle());
                for (QTDocument d : childs) {
                    String str = d.getTitle();
                    List<String> p = helper.tokenize(d.getTitle());
                    String[] parts = p.toArray(new String[p.size()]);
                    List<ExtInterval> nvs = helper.getNounAndVerbPhrases(str, parts);
                    for (ExtInterval ext : nvs){
                        logger.info("\t\t" + ext.getType() + " | " + str.substring(ext.getStart(), ext.getEnd()));
                    }
                }
            } catch (Exception e){
                logger.error(st);
            }
        }

    }
}
