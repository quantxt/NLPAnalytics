package com.quantxt.nlp.types;

import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtInterval;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.ExtInterval.ExtType.NUMBER;
import static com.quantxt.helper.types.ExtInterval.ExtType.PERCENT;
import static com.quantxt.nlp.types.QTValue.getPad;
import static com.quantxt.nlp.types.QTValue.logger;

/**
 * Created by matin on 11/24/18.
 */

public class QTValueNumber {

    final private static Pattern PATTERN  = Pattern.compile("(((?:[\\-\\+])?(\\d[,\\.\\d]*))|(\\(\\d[\\.,\\d]*\\)))(?:\\s*(hundred|thousand|million|billion|M|B))?(?:[\\s,\\.;]+)?");

    public static String detect(String str,
                                List<ExtInterval> valIntervals)
    {
        ArrayList<ExtInterval> dates = DateResolver.resolveDate(str);
        if (dates.size() > 0){
            for (ExtInterval e : dates){
                int st = e.getStart();
                int ed = e.getEnd();
                String pad = getPad(st, ed);
                str = StringUtils.overlay(str, pad, st, ed);
            }
            valIntervals.addAll(dates);
        }

        Matcher m = PATTERN.matcher(str);
        while (m.find()){
            int start = m.start();
            int end = m.end();
            String extractionStr = str.substring(start, end);
            double mult = 1;
            if (extractionStr.contains("hundred")) mult  = 100;
            if (extractionStr.contains("thousand")) mult = 1000;
            if (extractionStr.contains("million")) mult  = 1000000;
            if (extractionStr.contains("billion")) mult  = 1000000000;
            extractionStr = extractionStr.replace(",", "").trim();
            if (extractionStr.startsWith("-") ||
                    (extractionStr.contains("(") && extractionStr.contains(")"))){
                mult = -1;
            }

            extractionStr = extractionStr.replaceAll("[^\\d\\.]+", "");
            try {
                double parsed = Double.parseDouble(extractionStr);
                ExtInterval.ExtType t = NUMBER;
                if (str.length() > end && str.charAt(end) == '%'){
                    end +=1;
                    t = PERCENT;
                }
                ExtInterval ext = new ExtInterval(start, end);
                ext.setType(t);
                parsed *= mult;
                ext.setNumbervalue(parsed);
                ext.setCustomData(String.valueOf(parsed));
                valIntervals.add(ext);
            } catch (Exception e){
                logger.error(e.getMessage() + " " + extractionStr);
            }
        }

        for (ExtInterval e : valIntervals){
            int st = e.getStart();
            int ed = e.getEnd();
            String pad = getPad(st, ed);
            str = StringUtils.overlay(str, pad, st, ed);
        }
        return str;
    }
}
