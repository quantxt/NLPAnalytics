package com.quantxt.doc;

import static com.quantxt.doc.QTDocument.Language.ENGLISH;
import static com.quantxt.doc.QTDocument.Language.RUSSIAN;
import static com.quantxt.doc.QTDocument.Language.SPANISH;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.doc.helper.ESDocumentHelper;
import com.quantxt.doc.helper.RUDocumentHelper;
import com.quantxt.types.MapSort;

/**
 * Created by matin on 8/6/17.
 */
public class QTDocumentFactory {

    private static final Logger logger = LoggerFactory.getLogger(QTDocumentFactory.class);

    public static QTDocument createQTDoct(String body, String title,
            QTDocument.Language lang) {
        if (lang == SPANISH) {
            return new ESDocumentInfo(body, title);
        } else if (lang == RUSSIAN) {
            return new RUDocumentInfo(body, title);
        }
        return new ENDocumentInfo(body, title);
    }

    public static QTDocument createQTDoct(String body, String title) {
        String [] parts = body == null ? title.toLowerCase().split("\\s+")
                                       : body.toLowerCase().split("\\s+");

        HashMap<QTDocument.Language, Integer> map = new HashMap<>();
        map.put(ENGLISH, 0);
        map.put(SPANISH, 0);
        map.put(RUSSIAN, 0);
        int max = 500;
        for (String p : parts) {
            if (max-- < 0) break;
            if (new ESDocumentHelper().getStopwords().contains(p)) {
                int c = map.get(SPANISH);
                map.put(SPANISH, c + 1);
            } else if (new RUDocumentHelper().getStopwords().contains(p)) {
                int c = map.get(RUSSIAN);
                map.put(RUSSIAN, c + 1);
            } else if (new ENDocumentHelper().getStopwords().contains(p)) {
                int c = map.get(ENGLISH);
                map.put(ENGLISH, c + 1);
            }
        }
        HashMap<QTDocument.Language, Integer> sorted = MapSort.sortdescByValue(map);
        QTDocument.Language lang = sorted.entrySet().iterator().next().getKey();
        switch (lang) {
            case ENGLISH: return new ENDocumentInfo(body, title);
            case RUSSIAN: return new RUDocumentInfo(body, title);
            case SPANISH: return new ESDocumentInfo(body, title);
        }
        // we should never get here
        return new ENDocumentInfo(body, title);
    }
}
