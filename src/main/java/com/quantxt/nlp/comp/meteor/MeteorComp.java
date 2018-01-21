package com.quantxt.nlp.comp.meteor;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.ESDocumentInfo;
import com.quantxt.nlp.comp.meteor.scorer.MeteorConfiguration;
import com.quantxt.nlp.comp.meteor.scorer.MeteorScorer;
import com.quantxt.nlp.comp.meteor.scorer.MeteorStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by matin on 8/30/17.
 */
public class MeteorComp {

    final private static Logger logger = LoggerFactory.getLogger(MeteorComp.class);


    public static MeteorStats scorePlaintext(MeteorScorer scorer,
                                      ArrayList<String> lines1, ArrayList<String> lines2)
            throws IOException {

        MeteorStats aggStats = new MeteorStats();

        for (int i = 0; i < lines1.size(); i++) {
            MeteorStats stats = scorer.getMeteorStats(lines1.get(i), lines2.get(i));
            logger.info("acc: " + stats.score );
            logger.info("P:" + stats.precision);
            logger.info("R:" + stats.recall);
            logger.info("F1:" + stats.f1);
            logger.info("FP:" + stats.fragPenalty);
            aggStats.addStats(stats);
        }
        return aggStats;
    }

    public static void main(String[] args) throws Exception {

        MeteorConfiguration config = new MeteorConfiguration();
        ENDocumentInfo.init(null);
        ESDocumentInfo.init(null);
        ENDocumentInfo endoc = new ENDocumentInfo("", "");

        String fileName = "/Users/matin/git/meteor/data/paraphrase-en.gz";
        config.setParaFileURL(new File(fileName).toURI().toURL());
        MeteorScorer scorer = new MeteorScorer(config);
        String s1 =
                "Homeowners in high-tax states like New York, New Jersey and California could be big losers as their ability to deduct their local property taxes and state and local income taxes from their federal tax bills is now capped at $10,000. ";
        String s2 =
                "Under the GOP's final tax bill, millions of Americans will lose the tax benefits they enjoy from owning their homes.";
        ArrayList<String> l1 = new ArrayList<>();
        ArrayList<String> l2 = new ArrayList<>();
        l1.add(s1);
        l2.add(s2);
        MeteorStats stats = scorePlaintext(scorer, l1, l2);
        logger.info(stats.toString());

    }
}
