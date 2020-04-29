package com.quantxt.doc;

import com.quantxt.doc.helper.CommonQTDocumentHelper;
import com.quantxt.doc.helper.JADocumentHelper;
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

    public JADocumentInfo(List<String> body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.JAPANESE;
    }

    public JADocumentInfo (String body, String title) {
        super(body, title, new JADocumentHelper());
        language = Language.JAPANESE;
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
                    List<String> tokens = helper.tokenize(p);
                    List<String> postags = ((JADocumentHelper) helper).getPosTagsJa(p);
                    ArrayList<String> sentTokens = new ArrayList();
                    int start = 0;

                    for (int i = 0; i < postags.size(); i++) {
                        String token = tokens.get(i);

                        String tag = postags.get(i);
                        sentTokens.add(token);
                        CommonQTDocumentHelper.QTPosTags qtPosTag = ((JADocumentHelper) helper).getQtPosTag(tag);
                        if (token.equals("。") || puntuations.contains(token) || qtPosTag == CommonQTDocumentHelper.QTPosTags.PUNCT) {
                            int end = p.indexOf(token, start) + token.length();
                            String raw = p.substring(start, end);
                            start = end;
                            chunks.add(raw);
                            sentTokens = new ArrayList();
                        }
                    }

                    if (sentTokens.size() > 0) {
                        String raw = p.substring(start);
                        chunks.add(raw);
                    }
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
            JADocumentInfo sDoc = new JADocumentInfo("", str, helper);
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
