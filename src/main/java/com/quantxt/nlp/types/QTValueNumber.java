package com.quantxt.nlp.types;

import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.doc.QTExtract;
import com.quantxt.doc.helper.ENDocumentHelper;
import com.quantxt.helper.DateResolver;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.nlp.ExtractLc;
import com.quantxt.trie.Emit;
import com.quantxt.types.Entity;
import com.quantxt.types.NamedEntity;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

    final private static String simple_number = "(([\\+\\-]?\\d[,\\.\\d]+\\d|\\d+)|\\(\\s*(\\d[,\\.\\d]+\\d|\\d+)\\s*\\))(\\s*%)?";
    final private static Pattern currencies = Pattern.compile("(\\p{Sc})\\s*$");
    final private static Pattern units = Pattern.compile("hundred|thousand|million|billion|M|B");
    final private static Pattern PATTERN  = Pattern.compile(simple_number);
    final private static Pattern units_prefix_thousands  = Pattern.compile("in thousands|in 000s|in \\$000\\'s", Pattern.CASE_INSENSITIVE);
    final private static Pattern units_prefix_million  = Pattern.compile("in millions", Pattern.CASE_INSENSITIVE);


    private static double getMult(int start, int t_lastseen, int m_lastseen, ArrayList<Integer> thousands, ArrayList<Integer> millions){
        if (thousands.size() == 0 && millions.size() == 0) return 1;
        int dist = 200;
        if (t_lastseen > 0 && (start - t_lastseen) < dist && (start - t_lastseen) >0) {
            return 1000;
        }
        if (m_lastseen > 0 && (start - m_lastseen) < dist && (start - m_lastseen) > 0) {
            return 1000000;
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
        return 1;
    }

    public static String detect(String str,
                                String context_orig,
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

        ArrayList<Integer> thousands = new ArrayList<>();
        ArrayList<Integer> millions = new ArrayList<>();


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

        m = PATTERN.matcher(str);

        int numberGroups = 1;
        int thousands_offset = -1;
        int millions_offset = -1;
        while (m.find()){
            int start = m.start(numberGroups);
            int end   = m.end(numberGroups);
            double mult = getMult(start, thousands_offset, millions_offset, thousands, millions);
            if (mult == 1000){
                thousands_offset = start;
            } else  if (mult == 1000000){
                millions_offset = start;
            }
            String extractionStr = str.substring(start, end);

            extractionStr = extractionStr.replace(",", "").trim();
            if (extractionStr.contains("(") && extractionStr.contains(")")){
                mult *= -1;
                extractionStr = m.group(3).replace(",", "").trim();
            }

            try {
                double parsed = Double.parseDouble(extractionStr);
                ExtInterval.ExtType t = NUMBER;
                if (m.group(4) != null){
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
                        case "hundred" : mult *= 100; break;
                        case "thousand" : mult *= 1000; break;
                        case "million" : case "M" : mult *=1000000; break;
                        case "billion" : case "B" : mult *=1000000000; break;
                    }
                }
                if (t != PERCENT) {
                    parsed *= mult;
                }
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
    // bug:    String url = "https://www.sec.gov/Archives/edgar/data/1178670/000095012310045489/b80508e10vq.htm";

        String url = "https://www.sec.gov/Archives/edgar/data/1048477/000119312508040467/d10k.htm";
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
        body = body.replace("&nbsp;" , " ");

        body = body.replaceAll(" +" , " ");
        body = body.replaceAll("[\\r\\n] +" , "\n");
        body = body.replaceAll("[\\r\\n]+" , "\n");
    //    body = body.replaceAll("\\<b\\>|\\<\\/b\\>" , "");

        logger.info(body);

    //    QTDocument qtdoc = new ENDocumentInfo(body, "hello" , helper);
        ArrayList<ExtInterval> numbers = new ArrayList<>();

        ArrayList<Entity> entityArray1 = new ArrayList<>();

  //      entityArray1.add(new Entity("Research and development" , new String[]{"Research and development"} , true));
  //      entityArray1.add(new Entity("Total Research and development expense" , new String[]{"Total research and development expense"} , true));
        entityArray1.add(new Entity("Research and development expenses increased by" ,new String [] {"Research and development expenses increased by"}, true));

        Map<String, Entity[]> entMap = new HashMap<>();
        entMap.put("Company" , entityArray1.toArray(new Entity[entityArray1.size()]));
        QTExtract qtExtract = new ExtractLc(entMap, null, null);

    //    List<QTDocument> docs2procss = qtdoc.extractEntityMentions(qtExtract);
        /*
        ArrayList<ExtInterval> key_Values = new ArrayList<>();
        Matcher m = key_value.matcher(body);
        while (m.find()){
            logger.info(m.group(1));
        }
*/
        //    logger.info(body.replaceAll(" ", "+"));

        String [] sentences = helper.getSentences(body);
        for (int i = 0; i<sentences.length; i++) {
            String str = sentences[i];
            String context = (i==0)? "" : sentences[i-1];
            if (!str.toLowerCase().contains("research and development")) continue;
   //         if (!(str.contains("Warrant liability") || str.contains("Prepaid expenses and other current assets")

            logger.info("======================");

            ENDocumentInfo endoc = new ENDocumentInfo(str, str, helper);

     //       extractKeyValues(qtExtract, str, helper, 5, true);

            endoc.extractKeyValues(qtExtract, context, 5, true);
            logger.info("++++ " + str);
       //     d.extractKeyValues(qtExtract, 9, true);
            if (endoc.getValues() != null && !(endoc.getTitle() == null || endoc.getTitle().isEmpty())) {
     //           logger.info(str);
                logger.info(endoc.getTitle());
            }
        //    QTValueNumber.detect(b, numbers);
 //           logger.info("======================");
        }

        long end = System.currentTimeMillis();
        logger.info("Numbers {} in {}", numbers.size(), end - start);

    }
}
