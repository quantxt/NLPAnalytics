package com.quantxt.nlp.comp.meteor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.nlp.comp.meteor.scorer.MeteorScorer;
import com.quantxt.nlp.comp.meteor.scorer.MeteorStats;

/**
 * Created by matin on 8/30/17.
 */
public class MeteorComp {

    final private static Logger logger = LoggerFactory
            .getLogger(MeteorComp.class);

    public static MeteorStats scorePlaintext(MeteorScorer scorer,
            List<String> lines1, List<String> lines2) throws IOException {

        MeteorStats aggStats = new MeteorStats();

        for (int i = 0; i < lines1.size(); i++) {
            MeteorStats stats = scorer.getMeteorStats(lines1.get(i),
                    lines2.get(i));
            logger.info("acc: " + stats.score);
            logger.info("P:" + stats.precision);
            logger.info("R:" + stats.recall);
            logger.info("F1:" + stats.f1);
            logger.info("FP:" + stats.fragPenalty);
            aggStats.addStats(stats);
        }
        return aggStats;
    }

    public static void main(String[] args) throws Exception {

        MeteorScorer scorer = new MeteorScorer();
        String s1 = "homeowners in high-tax states like New York, "
                + "New Jersey and California could be big losers as "
                + "their ability to deduct their local home taxes "
                + "and state and local income tax from their federal tax "
                + "bills is now capped at $10,000. ";
        String s2 = "Under the GOP's final tax bill, millions of Americans will "
                + "lose the income tax benefits they enjoy from owning their homes.";
        ArrayList<String> l1 = new ArrayList<>();
        ArrayList<String> l2 = new ArrayList<>();
        l1.add(s1);
        l2.add(s2);
        MeteorStats stats = scorePlaintext(scorer, l1, l2);
        logger.info(stats.toString());
    }
}
