package com.quantxt.doc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public ESDocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.SPANISH;
    }

    public ESDocumentInfo(String body, String title) {
        super(body, title, new ESDocumentHelper());
        language = Language.SPANISH;
    }

    @Override
    public List<QTDocument> getChunks(CHUNK chunking) {
        List<QTDocument> chunk_docs = new ArrayList<>();
        if (body == null || body.isEmpty())
            return chunk_docs;

        List<String> chunks = new ArrayList<>();
        switch (chunking){
            case NONE:
                chunks.addAll(body);
                break;
            case LINE:
                for (String p : body) {
                    String[] lines = p.split("[\\n\\r]+");
                    chunks.addAll(Arrays.asList(lines));
                }
                break;
            case SENTENCE:
                for (String p : body) {
                    String[] sentences = helper.getSentences(p);
                    chunks.addAll(Arrays.asList(sentences));
                }
                break;
            case PARAGRAPH:
                for (String p : body) {
                    String[] paragraphs = p.split("[\\?\\.][\\n\\r]+");
                    chunks.addAll(Arrays.asList(paragraphs));
                }
                break;
            case PAGE:
                chunks.addAll(body);
        }

        for (String chk : chunks) {
            String str = chk.trim();
            if (str.isEmpty()) continue;
            ESDocumentInfo sDoc = new ESDocumentInfo("", str, helper);
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
