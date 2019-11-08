package com.quantxt.doc;

import com.quantxt.doc.helper.FRDocumentHelper;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
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
    }

    @Override
    public List<QTDocument> getChunks(CHUNK chunking) {
        List<QTDocument> chunk_docs = new ArrayList<>();
        if (body == null || body.isEmpty())
            return chunk_docs;

        List<String> chunks = new ArrayList<>();
        switch (chunking){
            case NONE:
                chunks.add(body);
                break;
            case LINE:
                String [] lines = body.split("[\\n\\r]+");
                chunks.addAll(Arrays.asList(lines));
                break;
            case SENTENCE:
                String [] sentences = helper.getSentences(body);;
                chunks.addAll(Arrays.asList(sentences));
                break;
            case PARAGRAPH:
                String [] paragraphs = body.split("[\\?\\.][\\n\\r]+");
                chunks.addAll(Arrays.asList(paragraphs));
                break;
        }

        for (String chk : chunks) {
            String str = chk.trim();
            if (str.isEmpty()) continue;
            FRDocumentInfo sDoc = new FRDocumentInfo("", str, helper);
            sDoc.setDate(getDate());
            sDoc.setLink(getLink());
            sDoc.setSource(getSource());
            sDoc.setLanguage(getLanguage());
            chunk_docs.add(sDoc);
        }

        return chunk_docs;
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