package com.quantxt.doc;

import java.util.ArrayList;
import java.util.Arrays;
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
            RUDocumentInfo sDoc = new RUDocumentInfo("", str, helper);
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
