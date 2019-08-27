package com.quantxt.nlp.types;

import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtIntervalSimple;
import com.quantxt.helper.types.QTField;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.PERCENT;
import static com.quantxt.nlp.types.QTValue.getPad;

/**
 * Created by matin on 11/24/18.
 */

public class QTValueNumber {

    final private static Logger logger = LoggerFactory.getLogger(QTValueNumber.class);

    final private static String simple_number = "(([\\+\\-]?\\d[,\\.\\d]+\\d|\\d+)|\\(\\s*(\\d[,\\.\\d]+\\d|\\d+)\\s*\\))(\\s*%)?";
    final private static Pattern currencies = Pattern.compile("(\\p{Sc})\\s*$");
    final private static Pattern units = Pattern.compile("(hundred|thousand|million|billion|M|B|百万円|億)($|[\b\\s\\.,])");
    final private static Pattern PATTERN  = Pattern.compile(simple_number);
    final private static Pattern units_prefix_thousands  = Pattern.compile("in thousands|in 000s|in \\$000\\'s", Pattern.CASE_INSENSITIVE);
    final private static Pattern units_prefix_million  = Pattern.compile("in millions|百万円", Pattern.CASE_INSENSITIVE);
    final private static Pattern units_prefix_billion  = Pattern.compile("in billions|億", Pattern.CASE_INSENSITIVE);


    private static double getMult(int start, int t_lastseen, int m_lastseen, int b_lastseen,
                                  ArrayList<Integer> thousands, ArrayList<Integer> millions, ArrayList<Integer> billions){
        if (thousands.size() == 0 && millions.size() == 0) return 1;
        int dist = 200;
        if (t_lastseen > 0 && (start - t_lastseen) < dist && (start - t_lastseen) >0) {
            return 1000;
        }
        if (m_lastseen > 0 && (start - m_lastseen) < dist && (start - m_lastseen) > 0) {
            return 1000000;
        }
        if (b_lastseen > 0 && (start - b_lastseen) < dist && (start - b_lastseen) > 0) {
            return 1000000000;
        }

        for (int t : thousands){
            if ((start - t) < dist && (start - t) > 0){
                return 1000;
            }
        }
        for (int m : millions){
            if ((start - m) < dist && (start - m) > 0){
                return 1000000;
            }
        }
        for (int m : billions){
            if ((start - m) < dist && (start - m) > 0){
                return 1000000000;
            }
        }
        return 1;
    }


    public static String detectDates(String str,
                                     String context_orig,
                                     List<ExtIntervalSimple> valIntervals) {

        ArrayList<ExtIntervalSimple> dates = DateResolver.resolveDate(str);
        valIntervals.addAll(dates);
        return str;
    }

    public static String detectPattern(String str,
                                       String context_orig,
                                       Pattern pattern,
                                       int [] groups,
                                       List<ExtIntervalSimple> valIntervals) {

        Matcher m = pattern.matcher(str);

        while (m.find()) {
            for (int g : groups) {
                int start = m.start(g);
                int end = m.end(g);
                String extractionStr = str.substring(start, end);
                ExtIntervalSimple ext = new ExtIntervalSimple(start, end);
                ext.setType(QTField.QTFieldType.KEYWORD);
                ext.setStringValue(extractionStr);
                ext.setCustomData(extractionStr);
                valIntervals.add(ext);
            }
        }

        return str;
    }

    public static String detect(String str,
                                String context_orig,
                                List<ExtIntervalSimple> valIntervals)
    {
        ArrayList<Integer> thousands = new ArrayList<>();
        ArrayList<Integer> millions = new ArrayList<>();
        ArrayList<Integer> billions = new ArrayList<>();

        // context --> str
        String context = (context_orig == null || context_orig.isEmpty()) ? str : context_orig + " " + str;

        Matcher m = units_prefix_thousands.matcher(context);
        while (m.find()){
            thousands.add(m.start() - context.length() + str.length() + 1);
        }
        m = units_prefix_million.matcher(context);
        while (m.find()){
            millions.add(m.start() -  context.length() + str.length() + 1);
        }
        m = units_prefix_billion.matcher(context);
        while (m.find()){
            billions.add(m.start() -  context.length() + str.length() + 1);
        }

        m = PATTERN.matcher(str);

        int numberGroups = 1;
        int thousands_offset = -1;
        int millions_offset = -1;
        int billions_offset = -1;
        while (m.find()){
            int start = m.start(numberGroups);
            int end   = m.end(numberGroups);
            double mult = getMult(start, thousands_offset, millions_offset, billions_offset, thousands,
                    millions, billions);

            if (mult == 1000){
                thousands_offset = start;
            } else  if (mult == 1000000){
                millions_offset = start;
            } else  if (mult == 1000000000){
                billions_offset = start;
            }

            String extractionStr = str.substring(start, end);

            extractionStr = extractionStr.replace(",", "").trim();
            if (extractionStr.contains("(") && extractionStr.contains(")")){
                mult *= -1;
                extractionStr = m.group(3).replace(",", "").trim();
            }

            try {
                double parsed = Double.parseDouble(extractionStr);
                QTField.QTFieldType t = QTField.QTFieldType.DOUBLE;
                if (m.group(4) != null){
                    t = PERCENT;
                } else {
                    // search for currency
                    String string_to_look_for_currencieis = str.substring(Math.max(0, start - 50), start);
                    Matcher currency_matcher = currencies.matcher(string_to_look_for_currencieis);
                    if (currency_matcher.find()){
                        t = QTField.QTFieldType.MONEY;
                    }
                }
                ExtIntervalSimple ext = new ExtIntervalSimple(start, end);
                ext.setType(t);


                String subStr = str.substring(end).trim();
                Matcher unitMatch = units.matcher(subStr);
                if (unitMatch.find() && unitMatch.start() < 3){
                    String unitMatched = unitMatch.group();
                    switch (unitMatched) {
                        case "hundred" : mult *= 100; break;
                        case "thousand" : mult *= 1000; break;
                        case "million" : case "M" : mult *=1000000; break;
                        case "billion" : case "B" : mult *=1000000000; break;
                    }
                }
                if (t != PERCENT) {
                    parsed *= mult;
                }
                ext.setDoubleValue(parsed);
                ext.setCustomData(String.valueOf(parsed));
                valIntervals.add(ext);
            } catch (Exception e){
                logger.error(e.getMessage() + " " + extractionStr);
            }
        }

        for (ExtIntervalSimple e : valIntervals){
            int st = e.getStart();
            int ed = e.getEnd();
            String pad = getPad(st, ed);
            str = StringUtils.overlay(str, pad, st, ed);
        }

        return str;
    }

    public static void main(String[] args) throws Exception {

        String body = "06/12/2019 06/12/2020 $ $ $\n" +
                "DIRECT AGENCY\n" +
                "APPLICANT INFORMATION\n" +
                "NAME (First Named Insured) AND MAILING ADDRESS (including ZIP+4) GL CODE SIC NAICS FEIN OR SOC SEC #\n" +
                "Seasons Condo Assoc of Ft Lauderdale, Inc The\n" +
                "c/o Castle Management, LLc 62003 8641 59-2051726\n" +
                "12270 SW 3rd Street BUSINESS PHONE #: (954) 462-8902\n" +
                "Suite 200\n" +
                "Plantation, FL 33325 WEBSITE ADDRESS\n" +
                "http://www.floridaseasons.com/index.html\n" +
                "CORPORATION JOINT VENTURE NOT FOR PROFIT ORG SUBCHAPTER \"S\" CORPORATION X Condo Association\n" +
                "INDIVIDUAL LLC NO. OF MEMBERSAND MANAGERS: PARTNERSHIP TRUST";
        String p = "(\\d{2}\\-\\d{7})";
        int[] g = new int[]{1};
        List<ExtIntervalSimple> valIntervals = new ArrayList<>();
        detectPattern(body, null, Pattern.compile(p), g, valIntervals);
        logger.info("Done");
    }
}
