package com.quantxt.doc;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.types.ExtInterval;

/**
 * Created by matin on 1/20/18.
 */
public class ENDocumentInfo extends QTDocument {

    private static final Logger logger = LoggerFactory.getLogger(ENDocumentInfo.class);

    public ENDocumentInfo(String body, String title, QTDocumentHelper helper) {
        super(body, title, helper);
        language = Language.ENGLISH;
    }

    public ENDocumentInfo(String body, String title) {
        super(body, title, new ENDocumentHelper());
        language = Language.ENGLISH;
    }

    public ENDocumentInfo(Elements body, String title) {
        super(body.html(), title, new ENDocumentHelper());
        rawText = body.text();
    }

    @Override
    public List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? helper.getSentences(body)
                                             : helper.getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences) {
            ENDocumentInfo sDoc = new ENDocumentInfo("", s, helper);
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
    public double[] getVectorizedTitle(QTExtract speaker) {
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
        String title = "Деньги благотворительного фогда пойдут на исследования преимуществ MDMA в области здравоохранения. MDMA –психоактивное соединение из ряда амфетаминов";
        String body = "В Китае , где расположено 80% мощностей по “добыче” биткоинов, могут запретить майнинг. Представители государственных агентств, опасаясь финансовых рисков, выпустили на прошлой неделе обращение к местным властям с призывом заняться прекращением деятельности, в ходе которой “майнят” криптовалюты, пишет The Wall Street Journal. Речь идет об обращении от имени Leading Group of Internet Financial Risks Remediation — госагентства, занимающегося изучением финансовых рисков в интернете, которое стало инициатором такой идеи. Группа сама по себе не контролирует использование электроэнергии в стране, но это влиятельный политический игрок, возглавляемый заместителем управляющего Народного банка Китая Паном Гоншеном. Представители группы на местах должны отчитываться о прогрессе по устранению майнеров в своем регионе каждый месяц.";

        ENDocumentInfo doc = new ENDocumentInfo(body, title);

        // BufferedReader br = new BufferedReader(new FileReader("/Users/matin/git/TxtAlign/out.txt"));
        // try {
        String line = "Bitcoin by the end of the year will cost twice as much - an expert How much will bitcoin cost by the end of 2017, the expert said. The value of the crypto currency on Thursday was $ 14 thousand. BISHKEK, Dec 7 - Sputnik.";
        // while ((line = br.readLine() ) != null) {
        logger.info(line);
        String[] parts = line.split("\\s+");
        List<ExtInterval> res = doc.getHelper().getNounAndVerbPhrases(line, parts);
        logger.info("=========");
    }

}
