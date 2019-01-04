package com.quantxt.nlp.types;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtInterval;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.ExtInterval.ExtType.MONEY;
import static com.quantxt.helper.types.ExtInterval.ExtType.NUMBER;
import static com.quantxt.helper.types.ExtInterval.ExtType.PERCENT;
import static com.quantxt.nlp.types.QTValue.getPad;

/**
 * Created by matin on 11/24/18.
 */

public class QTValueNumber {

    final private static Logger logger = LoggerFactory.getLogger(QTValueNumber.class);

    final private static String simple_number = "(([\\+\\-]?\\d+\\.\\d+|\\d[,\\d]+\\d|\\d+)|\\(\\s*(\\d+\\.\\d+|\\d[,\\d]+\\d|\\d+)\\s*\\))";

    final private static Pattern currencies = Pattern.compile("(\\p{Sc})\\s*$");
    final private static Pattern units = Pattern.compile("hundred|thousand|million|billion|M|B");

    final private static Pattern PATTERN  = Pattern.compile(simple_number);


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
        int numberGroups = 1;
        while (m.find()){
            int start = m.start(numberGroups);
            int end = m.end(numberGroups);
            String extractionStr = str.substring(start, end);
            double mult = 1;
            extractionStr = extractionStr.replace(",", "").trim();
            if (extractionStr.contains("(") && extractionStr.contains(")")){
                mult = -1;
                extractionStr = m.group(3).replace(",", "").trim();
            //    extractionStr = extractionStr.replaceAll("\\(\\s*|\\s*\\)]", "");
            }

            try {
                double parsed = Double.parseDouble(extractionStr);
                ExtInterval.ExtType t = NUMBER;
                if (str.length() > end && str.charAt(end) == '%'){
                    end +=1;
                    t = PERCENT;
                } else {
                    // search for currency
                    String string_to_look_for_currencieis = str.substring(Math.max(0, start - 50), start);
                    Matcher currency_matcher = currencies.matcher(string_to_look_for_currencieis);
                    if (currency_matcher.find()){
                        t = MONEY;
                    }
                }
                ExtInterval ext = new ExtInterval(start, end);
                ext.setType(t);


                String subStr = str.substring(end).trim();
                Matcher unitMatch = units.matcher(subStr);
                if (unitMatch.find() && unitMatch.start() < 3){
                    String unitMatched = unitMatch.group();
                    switch (unitMatched) {
                        case "hundred" : mult *=100; break;
                        case "thousand" : mult *=1000; break;
                        case "million" : case "M" : mult *=1000000; break;
                        case "billion" : case "B" : mult *=1000000000; break;
                    }
                }

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

    public static void main(String[] args) throws Exception {
        String url = "https://www.sec.gov/Archives/edgar/data/310158/000031015815000058/mrk0930201510q.htm";
        QTDocumentHelper helper = new ENDocumentHelper();

        Pattern key_value  = Pattern.compile("[\n\r]+ *([A-Z][A-Za-z\\-\\(\\),\\. ]+[a-z])[\n\r ]*");

        Document doc = Jsoup.connect(url)
                .header("Accept-Encoding", "gzip, deflate")
                .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:23.0) Gecko/20100101 Firefox/23.0")
                .maxBodySize(0)
                .timeout(600000)
                .get();

        logger.info("Document downloaded");
        long start = System.currentTimeMillis();
        //    logger.info(doc.text());
        String body = Jsoup.clean(doc.body().html(), "", Whitelist.none(), new Document.OutputSettings().prettyPrint(false));
        body = body.replace("&nbsp;" , " ").toString();
        //   logger.info(body);


        ArrayList<ExtInterval> numbers = new ArrayList<>();
        /*
        ArrayList<ExtInterval> key_Values = new ArrayList<>();
        Matcher m = key_value.matcher(body);
        while (m.find()){
            logger.info(m.group(1));
        }
*/

        //    logger.info(body.replaceAll(" ", "+"));

     //   for (String str : helper.getSentences(body)) {
            QTValueNumber.detect(body, numbers);
     //   }

        long end = System.currentTimeMillis();
        logger.info("Numbers {} in {}", numbers.size(), end - start);

    }
}
