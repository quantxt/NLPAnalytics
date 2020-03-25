package com.quantxt.nlp.entity;

import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtIntervalSimple;
import com.quantxt.helper.types.QTField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


import static com.quantxt.helper.types.QTField.QTFieldType.*;

/**
 * Created by matin on 11/24/18.
 */

@Slf4j
public class QTValueNumber {

    final private static String simple_number = "(([\\+\\-]?\\d[,\\.\\d]+\\d|\\d+)|\\(\\s*(\\d[,\\.\\d]+\\d|\\d+)\\s*\\))((?i)\\s*%|\\s*percent)?";
    final private static int simpleNumberGroup = 0;
    final private static int dist_bet_money_and_unit = 5;

    final private static Pattern currencies = Pattern.compile("(\\p{Sc})\\s*$");
    final private static Pattern units = Pattern.compile("(hundred|thousand|million|billion|M|B|百万円|億)($|[\b\\s\\.,])");
    final private static Pattern PATTERN  = Pattern.compile(simple_number);
    final private static Pattern units_prefix_thousands  = Pattern.compile("in thousands|in 000s|in \\$000\\'s", Pattern.CASE_INSENSITIVE);
    final private static Pattern units_prefix_million  = Pattern.compile("in millions|百万円", Pattern.CASE_INSENSITIVE);
    final private static Pattern units_prefix_billion  = Pattern.compile("in billions|億", Pattern.CASE_INSENSITIVE);


    private static double getMult(int start, int t_lastseen, int m_lastseen, int b_lastseen,
                                  ArrayList<Integer> thousands, ArrayList<Integer> millions, ArrayList<Integer> billions){
        if (thousands.size() == 0 && millions.size() == 0) return 1;
        if (t_lastseen > 0 && (start - t_lastseen) < dist_bet_money_and_unit && (start - t_lastseen) >0) {
            return 1000;
        }
        if (m_lastseen > 0 && (start - m_lastseen) < dist_bet_money_and_unit && (start - m_lastseen) > 0) {
            return 1000000;
        }
        if (b_lastseen > 0 && (start - b_lastseen) < dist_bet_money_and_unit && (start - b_lastseen) > 0) {
            return 1000000000;
        }

        for (int t : thousands){
            if ((start - t) < dist_bet_money_and_unit && (start - t) > 0){
                return 1000;
            }
        }
        for (int m : millions){
            if ((start - m) < dist_bet_money_and_unit && (start - m) > 0){
                return 1000000;
            }
        }
        for (int m : billions){
            if ((start - m) < dist_bet_money_and_unit && (start - m) > 0){
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
                                       java.util.regex.Pattern pattern,
                                       int [] groups,
                                       List<ExtIntervalSimple> valIntervals)
    {

        Matcher m = pattern.matcher(str);

        while (m.find()) {
            if ( groups == null){
                ExtIntervalSimple ext = new ExtIntervalSimple(m.start(), m.end());
                ext.setType(QTField.QTFieldType.KEYWORD);
                int start = m.start();
                int end = m.end();
                String extractionStr = str.substring(start, end);
                ext.setStringValue(extractionStr);
                ext.setCustomData(extractionStr);
                valIntervals.add(ext);
            } else {
                for (int g : groups) {
                    ExtIntervalSimple ext = new ExtIntervalSimple(m.start(), m.end());
                    ext.setType(QTField.QTFieldType.KEYWORD);
                    int start = m.start(g);
                    int end = m.end(g);
                    String extractionStr = str.substring(start, end);
                    ext.setStringValue(extractionStr);
                    ext.setCustomData(extractionStr);
                    valIntervals.add(ext);
                }
            }
        }

        return str;
    }

    public static List<ExtIntervalSimple> detectFirstPattern(String str,
                                                             String context_orig,
                                                             Pattern matchPattern,
                                                             boolean hasGroup)
    {

        Matcher m = matchPattern.matcher(str);

        List<ExtIntervalSimple> valIntervals = new ArrayList<>();
        int group = hasGroup ? 1 : 0;
        while (m.find()) {
            int start = m.start(group);
            int end = m.end(group);
            ExtIntervalSimple ext = new ExtIntervalSimple(start, end);
            ext.setType(QTField.QTFieldType.KEYWORD);
            String extractionStr = str.substring(ext.getStart(), ext.getEnd());
            ext.setStringValue(extractionStr);
            ext.setCustomData(extractionStr);
            valIntervals.add(ext);
        }
        return valIntervals;
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

        int thousands_offset = -1;
        int millions_offset = -1;
        int billions_offset = -1;
        while (m.find()){
            int start = m.start(simpleNumberGroup);
            int end   = m.end(simpleNumberGroup);

            String mmmm = m.group();
            //we want to make sur ethe number is not attached or part of a word like DS123
            if (start>0 ) {
                char char_before = str.charAt(start -1);
                if ((char_before >= 65 && char_before <=90) || (char_before >= 97 && char_before <=122) ) {
                    continue;
                }
            }

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
                QTField.QTFieldType t = null;

                if (m.group(4) != null){
                    t = PERCENT;
                    String percent_str = m.group(4);
                    extractionStr = extractionStr.replace(percent_str, "");
                } else {
                    // search for currency
                    String string_to_lookbehind_for_currencieis = str.substring(Math.max(0, start - 50), start);
                    Matcher currency_matcher = currencies.matcher(string_to_lookbehind_for_currencieis);
                    if (currency_matcher.find()){
                        t = QTField.QTFieldType.MONEY;
                        // and move the start to where the currency was found
                        int start_currency = currency_matcher.start();
                        int shift = string_to_lookbehind_for_currencieis.length() - start_currency;
                        if (start > shift){
                            start -= shift;
                        }
                    }
                }

                if (t == null){
                    if (extractionStr.indexOf(".") < 0){
                        t = LONG;
                    } else {
                        t = QTField.QTFieldType.DOUBLE;
                    }
                }

                ExtIntervalSimple ext = new ExtIntervalSimple(start, end);
                ext.setType(t);

                String subStr = str.substring(end);
                Matcher unitMatch = units.matcher(subStr);
                if (unitMatch.find() && unitMatch.start() < 4){
                    String unitMatched = unitMatch.group(1);
                    switch (unitMatched) {
                        case "hundred" : mult *= 100; break;
                        case "thousand" : mult *= 1000; break;
                        case "million" : case "M" : mult *=1000000; break;
                        case "billion" : case "B" : mult *=1000000000; break;
                    }
                }
                double parsed = Double.parseDouble(extractionStr);
                if (t != PERCENT) {
                    parsed *= mult;
                }

                if (t == LONG){
                    long long_value = (long) parsed;
                    ext.setIntValue(long_value);
                    ext.setCustomData(String.valueOf(long_value));
                } else {
                    ext.setCustomData(String.valueOf(parsed));
                    ext.setDoubleValue(parsed);
                }
                valIntervals.add(ext);
            } catch (Exception e){
                log.error(e.getMessage() + " " + extractionStr);
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

    private static String getPad(final int s, final int e){
        return String.join("", Collections.nCopies(e - s, " "));
    }


    public static void main(String[] args) {

        String body = "Mortgage banking revenue \nincreased \nby \n$13.5 million \nin the \nthird quarter of 2019 \nas compared to the \nsecond quarter of 2019 \nprimarily as a result of higher production revenues and an increase in the fair value of the mortgage servicing rights portfolio \nin the third quarter of 2019.";
        String p = "(\\d{2}\\-\\d{7})";
        int[] g = new int[]{1};
        List<ExtIntervalSimple> valIntervals = new ArrayList<>();
        detect(body, "", valIntervals);
        log.info("Done");
    }
}
