package com.quantxt.doc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.RUDocumentHelper;

/**
 * Created by matin on 1/20/18.
 */
public class RUDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(RUDocumentInfo.class);

    public RUDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.RUSSIAN;
    }

    public RUDocumentInfo (String body, String title) {
        super(body, title, new RUDocumentHelper());
        language = Language.RUSSIAN;
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
            case PAGE:
                chunks.addAll(body);
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
}
