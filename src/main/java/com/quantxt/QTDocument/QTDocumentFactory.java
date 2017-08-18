package com.quantxt.QTDocument;

import com.quantxt.doc.QTDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by matin on 8/6/17.
 */
public class QTDocumentFactory {

    private static final Logger logger = LoggerFactory.getLogger(QTDocumentFactory.class);


    public static QTDocument createQTDoct(String body, String title, QTDocument.Language lang){
        if (lang == QTDocument.Language.SPANISH){
            return new ESDocumentInfo(body, title);
        }
        return new ENDocumentInfo(body, title);
    }

    public static QTDocument createQTDoct(String body, String title){
        String [] parts = body == null ? title.toLowerCase().split("\\s+") : body.toLowerCase().split("\\s+");

        int en = 0;
        int es = 0;
        int max = 500;
        for (String p : parts){
            if (max-- < 0) break;
            if (ESDocumentInfo.isStopWord(p)){
                es++;
            } else if (ENDocumentInfo.isStopWord(p)){
                en++;
            }
        }
        if (es > en){
            return new ESDocumentInfo(body, title);
        }

        return new ENDocumentInfo(body, title);
    }
}
