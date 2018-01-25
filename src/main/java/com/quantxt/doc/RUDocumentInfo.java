package com.quantxt.doc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quantxt.types.DateTimeTypeConverter;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jsoup.select.Elements;
import com.quantxt.doc.helper.RUDocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.Speaker;
import com.quantxt.types.Entity;

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
        rawText = body.text();
    }

    @Override
    List<QTDocument> getChilds() {
        if (body == null || body.isEmpty())
            return null;

        String sentences[] = rawText == null ? helper.getSentences(body)
                                             : helper.getSentences(rawText);
        List<QTDocument> childs = new ArrayList<>();
        for (String s : sentences){
            RUDocumentInfo sDoc = new RUDocumentInfo("", s, helper);
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

    //http://corpus.leeds.ac.uk/mocky/ru-table.tab
    public List<ExtInterval> hack(String orig, String[] parts) {
        return helper.getNounAndVerbPhrases(orig, parts);
    }

    public static void main(String[] args) throws Exception {
        // String title = "Силуанов: законопроект, запрещающий россиянам пользоваться криптовалютами уже готов Биткоин против Apple: экономисты прогнозируют рост объема торгов криптовалютой до уровня торга акициями IT-гиганта Биткоин продолжает рост и уже превысил $14 000 <a href=\"\" title=\"\"> <abbr title=\"\"> <acronym title=\"\"> <b> <blockquote cite=\"\"> <cite> <code> <del datetime=\"\"> <em> <i> <q cite=\"\"> <strike> <strong> Copyright © 2018 Русский Еврей - All Rights Reserved Powered by WordPress & Atahualpa";
        // String body = "В Китае , где расположено 80% мощностей по “добыче” биткоинов, могут запретить майнинг. Представители государственных агентств, опасаясь финансовых рисков, выпустили на прошлой неделе обращение к местным властям с призывом заняться прекращением деятельности, в ходе которой “майнят” криптовалюты, пишет The Wall Street Journal. Речь идет об обращении от имени Leading Group of Internet Financial Risks Remediation — госагентства, занимающегося изучением финансовых рисков в интернете, которое стало инициатором такой идеи. Группа сама по себе не контролирует использование электроэнергии в стране, но это влиятельный политический игрок, возглавляемый заместителем управляющего Народного банка Китая Паном Гоншеном. Представители группы на местах должны отчитываться о прогрессе по устранению майнеров в своем регионе каждый месяц.";

        // RUDocumentInfo doc = new RUDocumentInfo("", "");

        Map<String, Entity[]> entMap = new HashMap<>();
        ArrayList<Entity> entityArray1 = new ArrayList<>();
        entityArray1.add(new Entity("Bitcoin" , new String[]{"Bitcoin" , "Биткоин"} , true));
        entMap.put("Bitcoin" , entityArray1.toArray(new Entity[entityArray1.size()]));
        QTExtract enx = new Speaker(entMap, (String)null, null);

        Gson JODA_GSON = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter())
                .create();
        BufferedReader br = new BufferedReader(new FileReader("/Users/matin/git/TxtAlign/o.txt"));
        try {
            String line = null;
            while ((line = br.readLine() ) != null) {

                QTDocument p = JODA_GSON.fromJson(line, QTDocument.class);
                QTDocument parent = QTDocumentFactory.createQTDoct(p.getBody(), p.getTitle());
                logger.info("L: " + parent.getLanguage());

                // Files.write(Paths.get("o.txt"), (theString + "\n").getBytes(), StandardOpenOption.APPEND);

                parent.setDate(p.getDate());
                parent.setLink(p.getLink());
                parent.setSource(p.getSource());

                ArrayList<QTDocument> children = null;
                synchronized (enx) {
                    children = parent.extractEntityMentions(enx);
                }
                logger.info("size: " + children.size());
				/*

				List<String> p2 = doc.tokenize(line);
				logger.info(line);

				List<ExtInterval> res = doc.hack(line, p2.toArray(new String[p2.size()]));

				if (res == null) continue;
				*/
                logger.info("=========");

            }
        } finally {
            br.close();
        }

		/*

		QTDocument doc = QTDocumentFactory.createQTDoct(body, title);
		logger.info("lang is: " + doc.getLanguage());
		doc.processDoc();

		ArrayList<Entity> entityArray1 = new ArrayList<>();
		entityArray1.add(new Entity("Wall Street" , null , true));
		entityArray1.add(new Entity("China" , new String[]{"Китае"} , true));

		Map<String, Entity[]> entMap = new HashMap<>();
		entMap.put("Company" , entityArray1.toArray(new Entity[entityArray1.size()]));
		QTExtract enx = new Speaker(entMap, (String)null, null);
		ArrayList<QTDocument> docs = doc.extractEntityMentions(enx);
		Gson gson =new Gson();
		for (QTDocument d : docs){
			Map<String, LinkedHashSet<String>> entityMap = d.getEntity();
			logger.info(d.getDocType() + " / " + gson.toJson(entityMap));
		}
		*/
    }

}
