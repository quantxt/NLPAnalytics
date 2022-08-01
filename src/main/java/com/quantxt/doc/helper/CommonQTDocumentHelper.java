package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.doc.helper.textbox.TextBox;
import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
import com.quantxt.types.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

import static com.quantxt.doc.helper.textbox.TextBox.*;
import static com.quantxt.types.QSpan.EXTBOXType.HORIZENTAL_ONE;
import static com.quantxt.types.QSpan.EXTBOXType.VERTICAL_MANY;

/**
 * Created by dejani on 1/24/18.
 */

public class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    private static String AUTO = "__AUTO__";

    private static int max_string_length_for_search = 30000;
    private static Pattern WORD_PTR = Pattern.compile("\\S+");
    private static Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static Pattern SPC_BET_WORDS_PTR = Pattern.compile("\\S(?= \\S)");
    private static Pattern LONG_SPACE = Pattern.compile(" {5,}");
    private static Pattern START_SPACE = Pattern.compile("^ *");

    // bullets
    protected static String UTF8_BULLETS = "\\u2022|\\u2023|\\u25E6|\\u2043|\\u2219";
    protected static Pattern UTF8_TOKEN = Pattern.compile("^(?:[a-zA-Z]\\.){2,}|([\\p{L}\\p{N}]+[\\.\\&]{0,1}[\\p{L}\\p{N}])");

 //   final private static Pattern simple_form_val   = Pattern.compile("^[^\\p{L}\\p{N}:]*: {0,20}((?:[\\p{L}\\p{N}]\\S* )*\\S+)");
    final private static Pattern simple_form_val   = Pattern.compile("[^\\:]*: *((?:\\S([^\\:\\s]+ )*[^\\:\\s]+))(?=$|\\s{2,})");

    final private static String begin_pad = "(?<=^|[:\\s])";
    final private static String end_pad   = "(?=$|\\n| {2,})";
    final private static String genricPharse =  "\\S+";
    final private static String numAlphabet =  "(\\p{N}[\\p{L}\\p{N}\\-\\/\\)\\(\\.]+|[\\p{L}]|[\\p{L}]+\\p{N}[\\p{L}\\p{N}\\-\\/\\)\\(\\.]*)";

    final private static Pattern FormKey  = Pattern.compile("((?:[^\\:\\s]+ )*[^\\s\\:]+ *\\:)");

    final private static Pattern GenericDate1  = Pattern.compile(begin_pad + "((?:[1-9]|[01]\\d)[ -\\/](?:[1-9]|[0123]\\d)[ -\\/](?:19\\d{2}|20\\d{2}|\\d{2}))" + end_pad);  // mm dd yyyy
    final private static Pattern GenericDate2  = Pattern.compile(begin_pad + "/([12]\\d{3}[ -\\/](?:0[1-9]|1[0-2])[ -\\/](?:0[1-9]|[12]\\d|3[01]))/" + end_pad);  // YYYY-mm-dd
    final private static Pattern Numbers       = Pattern.compile(begin_pad + "((?:\\p{Sc} {0,6})?[+\\-]{0,1}[0-9]{1,3}(?:[\\.,]?[0-9]{3})*(?:[,\\.][0-9]{2})?%?)" + end_pad);  // mm dd yyyy
    final private static Pattern Id1           = Pattern.compile(begin_pad + "(" +numAlphabet + ")"+ end_pad);

    final private static Pattern Digits =  Pattern.compile("\\p{N}");
    final private static Pattern Alphabets =  Pattern.compile("((?:[#\\p{L}]+[ \\-_\\/\\.%])*[#\\p{L}]+[\\/\\.%]*(?=$| ))");
    final private static Pattern inParentheses =  Pattern.compile("\\([^\\)]+\\)");

    final private static Pattern Generic = Pattern.compile("(?<=^|  )" + "((?:"+ genricPharse+" )*" + genricPharse + ")"  + "(?=$|\\s{2,})");
    final private static Pattern [] AUTO_Patterns = new Pattern[] {GenericDate1, GenericDate2, Numbers, Id1, Generic};

    protected Analyzer analyzer;

    public CommonQTDocumentHelper() {
        analyzer = new ClassicAnalyzer();
    }

    @Override
    public List<String> tokenize(String str) {
        List<String> tokens = new ArrayList<>();
        try {
            TokenStream result = analyzer.tokenStream(null, str);
            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                tokens.add(resultAttr.toString());
            }
            result.close();
        } catch (Exception e) {
            logger.error("Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    private String tokenize(Analyzer analyzer , String str){
        StringBuilder sb = new StringBuilder();
        try {
            TokenStream result = analyzer.tokenStream(null, str);
            CharTermAttribute resultAttr = result.addAttribute(CharTermAttribute.class);
            result.reset();

            while (result.incrementToken()) {
                sb.append(resultAttr.toString()).append(" ");
            }
            result.close();
        } catch (Exception e) {
            logger.error("Analyzer: " + e.getMessage());
        }
        return sb.toString().trim();
    }

    private void findLabelsHelper(DictSearch dictSearch,
                                   Map<Integer, BaseTextBox> lineTextBoxMap,
                                   String content,
                                   int shift,
                                   int slop,
                                   List<QSpan> dicLabels){
        if (content.isEmpty()) return;
        Collection<QSpan> qspans = dictSearch.search(content, lineTextBoxMap, slop);

        for (QSpan qSpan : qspans) {
            ExtInterval ext = qSpan.getExtInterval();
            int start = ext.getStart() + shift;
            int end = ext.getEnd() + shift;
            ext.setStart(start);
            ext.setEnd(end);
            dicLabels.add(qSpan);
        }
    }

    private Map<String, Collection<QSpan>> findLabels(List<DictSearch> extractDictionaries,
                                                            Map<Integer, BaseTextBox> lineTextBoxMap,
                                                            String content,
                                                            int slop) {
        int content_length = content.length();
        Map<String, Collection<QSpan>> labels = new LinkedHashMap<>();

        for (DictSearch dictSearch : extractDictionaries) {
            String dict_id = dictSearch.getDictionary().getId();
            List<QSpan> dicLabels = new ArrayList<>();

            if (content_length > max_string_length_for_search){
                int cnt_idx = 0;
                while (cnt_idx < content_length){
                    String cnt_chunk = content.substring(cnt_idx, Math.min(cnt_idx + max_string_length_for_search, content_length));
                    findLabelsHelper(dictSearch, lineTextBoxMap, cnt_chunk, cnt_idx, slop, dicLabels);
                    cnt_idx += max_string_length_for_search - 1000;
                }
            } else {
                findLabelsHelper(dictSearch, lineTextBoxMap, content, 0, slop, dicLabels);
            }
            if (!dicLabels.isEmpty()) {
                labels.put(dict_id, dicLabels);
            }
        }
        return labels;
    }

    private Map<Integer, BaseTextBox> getLineTextBoxMap(List<BaseTextBox> rawTextBoxes){
        if (rawTextBoxes == null || rawTextBoxes.size() == 0) return null;

        rawTextBoxes.sort((p1, p2) -> Float.compare(p1.getBase(), p2.getBase()));
        List<BaseTextBox> textBoxes = TextBox.process(rawTextBoxes);
        Map<Integer, BaseTextBox> lineTextBoxes = new TreeMap<>();
        if (textBoxes == null) return lineTextBoxes;
        for (BaseTextBox tb : textBoxes){
            int l = tb.getLine();
            lineTextBoxes.put(l, tb);
        }
        return lineTextBoxes;
    }

    private Map<Integer, List<ExtIntervalTextBox>> getLocalLineAndTextBox(String content,
                                                                          Map<Integer, BaseTextBox> lineTextBoxMap,
                                                                          Map<String, Collection<ExtInterval>> labels,
                                                                          boolean ignore_prefix_boxes){

        Map<Integer, List<ExtIntervalTextBox>> lineTolabelsWithLineAndTextbox = new HashMap<>();
        for (Map.Entry<String, Collection<ExtInterval>> e : labels.entrySet()) {
            Collection<ExtInterval> extIntervals = e.getValue();
            List<ExtIntervalTextBox> flist = new ArrayList<>();
            for (ExtInterval extInterval : extIntervals) {
                LineInfo lineInfo = new LineInfo(content, extInterval);

                BaseTextBox tb = null;
                if (lineTextBoxMap != null) {

                    tb = findAssociatedTextBox(lineTextBoxMap, extInterval.getStr(),
                            lineInfo, ignore_prefix_boxes);
                    if (tb == null) {
                        logger.debug("Didn't find tb got {}", extInterval.getStr());
                        continue;
                    }
                    tb.setLine(lineInfo.getLineNumber());
                }

                ExtIntervalLocal extIntervalLocal = new ExtIntervalLocal(extInterval);
                extIntervalLocal.setLocal_start(lineInfo.getLocalStart());
                extIntervalLocal.setLocal_end(lineInfo.getLocalEnd());
                extIntervalLocal.setLine(lineInfo.getLineNumber());

                ExtIntervalTextBox eitb = new ExtIntervalTextBox(extIntervalLocal, tb);

                List<ExtIntervalTextBox> tbList = lineTolabelsWithLineAndTextbox.get(lineInfo.getLineNumber());
                if (tbList == null){
                    tbList = new ArrayList<>();
                    lineTolabelsWithLineAndTextbox.put(lineInfo.getLineNumber(), tbList );
                }
                tbList.add(eitb);
                flist.add(eitb);
            }
            if (lineTextBoxMap != null) {
       //         combineStackedTextBoxes(flist);
            }
        }
        return lineTolabelsWithLineAndTextbox;
    }

    private void addNeighbors(QSpan qSpan, BaseTextBox tb2){

        BaseTextBox tb1 = qSpan.getTextBox();

        float b1 = tb1.getBase();
        float b2 = tb2.getBase();

        float t1 = tb1.getTop();
        float t2 = tb2.getTop();

        float l1 = tb1.getLeft();
        float l2 = tb2.getLeft();

        float r1 = tb1.getRight();
        float r2 = tb2.getRight();

        //TODO: we only over values below or to the right of the key
        float vOverlap = getVerticalOverlap(tb1, tb2);
        if (vOverlap > .4 ){
            if ( (l2 - r1) >0 ) {
                double d = l2 - r1;
                qSpan.getNeighbors().put(tb2, d);
                return;
            }
        }

        float hOverlap = getHorizentalOverlap(tb1, tb2);
        if (hOverlap > 0 ){
            if ( (t2 - b1) >0 && (t2 - b1) < 100){
                double d = t2 - b1;
                qSpan.getNeighbors().put(tb2, d);
                return;
            }
        }

        if ( (t2-b1)<100 && (t2-b1)>0 ){
            if ((l2 - r1)> 0 && (l2 - r1) <100) {
                double d = Math.sqrt(Math.pow(b2 - b1, 2) + Math.pow(l2 - r1, 2));
                qSpan.getNeighbors().put(tb2, d);
                return;
            }
            if ((l1 - r2)> 0 && (l1 - r2) <100) {
                double d = Math.sqrt(Math.pow(b2 - b1, 2) + Math.pow(l1 - r2, 2));
                qSpan.getNeighbors().put(tb2, d);
                return;
            }
        }
    }

    private Map<Integer, List<ExtIntervalTextBox>> getLocalLineAndTextBox2(Map<Integer, BaseTextBox> lineTextBoxMap,
                                                                           Map<String, Collection<QSpan>> labels){

        List<QSpan> allLabels = new ArrayList<>();
        Map<Integer, List<ExtIntervalTextBox>> lineTolabelsWithLineAndTextbox = new HashMap<>();
        for (Map.Entry<String, Collection<QSpan>> e : labels.entrySet()) {
            for (QSpan qSpan : e.getValue()) {
                ExtIntervalTextBox eitb = new ExtIntervalTextBox(qSpan.getExtInterval(), qSpan.getTextBox());
                List<ExtIntervalTextBox> tbList = lineTolabelsWithLineAndTextbox.get(qSpan.getLine());
                if (tbList == null){
                    tbList = new ArrayList<>();
                    lineTolabelsWithLineAndTextbox.put(qSpan.getLine(), tbList );
                }
                tbList.add(eitb);

                //find potential associated value(s)
                String lbl = qSpan.getStr();
                int lblLine = qSpan.getLine();
                BaseTextBox lblTextbox = qSpan.getTextBox();
                if (lblTextbox == null) continue;
                allLabels.add(qSpan);
                for (int l=lblLine; l< lblLine+5; l++){
                    BaseTextBox lineTexbox = lineTextBoxMap.get(l);

                    if (lineTexbox == null) continue;
                    for (BaseTextBox c : lineTexbox.getChilds()){
                        addNeighbors(qSpan, c);
                    }
                }


                /*
                Map<BaseTextBox, Double> sortedValues = MapSort.sortByValue(values);

                logger.info(lblTextbox.getStr());
                for (Map.Entry<BaseTextBox, Double> ee : sortedValues.entrySet()){
                    logger.info("\t {} {}", ee.getKey().getStr(), ee.getValue());
                }

                 */

            }
        }

        // trying to detect table headers
        // we must have at least two hearder in the label set

        for (int i=0; i< allLabels.size(); i++) {
            QSpan qSpan1 = allLabels.get(i);
            if (qSpan1.getSpanType() != null) continue;
            BaseTextBox baseTextBox1 = qSpan1.getTextBox();
            boolean hasOverlapWithOneLabel = false;
            int line1 = qSpan1.getLine();

            for (int j=i+1; j< allLabels.size(); j++) {
                QSpan qSpan2 = allLabels.get(j);
                if (qSpan2.getSpanType() != null) continue;
                BaseTextBox baseTextBox2 = qSpan2.getTextBox();
                float ov = getVerticalOverlap(baseTextBox1, baseTextBox2);
                if (ov > .7){
                    hasOverlapWithOneLabel = true;
                    break;
                }
            }
            if (!hasOverlapWithOneLabel) continue;
            String lineStr = lineTextBoxMap.get(line1).getLine_str();
            if (lineStr.contains(":")) continue;

            String stripped = lineStr.replaceAll("[A-Za-z\\-\\.\\/\\:\\#\\%\\(\\)]+", "").trim();
            if (stripped.length() < 2) {
                //TODO: Basic form detection: we look at one and two lines below
                // Most basic case - one value for every key

                Matcher m1 = Generic.matcher(lineStr);
                Matcher m2 = lineTextBoxMap.containsKey(line1+1)? Generic.matcher(lineTextBoxMap.get(line1+1).getLine_str()): null;
                Matcher m3 = lineTextBoxMap.containsKey(line1+2)? Generic.matcher(lineTextBoxMap.get(line1+2).getLine_str()): null;
                Matcher m4 = lineTextBoxMap.containsKey(line1+3)? Generic.matcher(lineTextBoxMap.get(line1+3).getLine_str()): null;



                qSpan1.setSpanType(VERTICAL_MANY);
                logger.debug("{} is table header", qSpan1.getStr());
            }
        }


        for (QSpan qSpan : allLabels) {
            if (qSpan.getSpanType() != null) continue;
            BaseTextBox lblTextbox = qSpan.getTextBox();

            String lblStr = qSpan.getStr();
            // we first check 3 lines below and 3 lines above
            float l1 = lblTextbox.getLeft();
            float r1 = lblTextbox.getRight();
            int line1 = qSpan.getLine();
            String lineStr = lineTextBoxMap.get(line1).getLine_str();
            int s = lineStr.indexOf(lblStr);
            if (s >= 0 ) {
                String substr = lineStr.substring(s+lblStr.length());
                Pattern p = Pattern.compile("^[^A-Za-z0-9]{0,5}\\: {0,7}((\\S+ ){0,5}\\S+)");
                Matcher m = p.matcher(substr);
                if (m.find()) {
                    logger.debug("{} is strong form key - value is front", qSpan.getStr());
                    qSpan.setSpanType(HORIZENTAL_ONE);
                    continue;
                }
            }

            int potentialValueCount = 0;
            int total = 0;
            for (Map.Entry<Integer, BaseTextBox> ee : lineTextBoxMap.entrySet()) {
                List<BaseTextBox> childs = ee.getValue().getChilds();
                int line2 = ee.getValue().getLine();
                for (BaseTextBox c : childs) {
                    //check if this row has all qualified table header characters A-Aa-z-\.\/
                    float l2 = c.getLeft();
                    float r2 = c.getRight();
                    if (Math.abs(line1 - line2) > 4) continue;
                    if (Math.abs(l1 - l2) > 5 && Math.abs(r1 - r2) > 5) continue;
                    total++;
                    String stripped = c.getStr().replaceAll("[A-Za-z\\-\\.\\/\\:\\#\\%]+", "").trim();
                    if (stripped.length() > 2) {
                        potentialValueCount++;
                    }
                }

                if (total > 2 && potentialValueCount < 2) {
                    logger.debug("{} is form key - value is front", qSpan.getStr());
                    qSpan.setSpanType(HORIZENTAL_ONE);
                }
            }
        }

        return lineTolabelsWithLineAndTextbox;
    }

    private boolean hasOvelapWLabels(AutoValue autoValue, List<QSpan> allLabels){
        if (autoValue.hValue == null && autoValue.vValue.size() == 0) return true;
        if (autoValue.hValue != null){
            for (QSpan qSpan : allLabels){
                BaseTextBox bt = qSpan.getTextBox();
                if (bt == null) continue;
                float ho = getHorizentalOverlap(autoValue.hValue.getTextBox(), bt);
                float vo = getVerticalOverlap(autoValue.hValue.getTextBox(), bt);
                if (ho >0 && vo >0){
                    autoValue.hValue = null;
                    return true;
                }
            }
        }

        if (autoValue.vValue.size() > 0){
            for (QSpan qSpan : allLabels){
                BaseTextBox bt = qSpan.getTextBox();
                if (bt == null) continue;
                float ho = getHorizentalOverlap(autoValue.vValue.get(0).getTextBox(), bt);
                float vo = getVerticalOverlap(autoValue.vValue.get(0).getTextBox(), bt);
                if (ho >0 && vo >0){
                    autoValue.vValue = null;
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public List<ExtInterval> extract(final String content,
                                     List<DictSearch> extractDictionaries,
                                     List<BaseTextBox> textBoxes,
                                     boolean searchVertical) {

        Map<Integer, BaseTextBox> lineTextBoxMap = getLineTextBoxMap(textBoxes);
        Map<String, Collection<QSpan>> labels = findLabels(extractDictionaries, lineTextBoxMap, content, 0);
        Map<Integer, List<ExtIntervalTextBox>> lineLabelMap = getLocalLineAndTextBox2(lineTextBoxMap, labels);

        ArrayList<ExtInterval> foundValues = new ArrayList<>();
        Map<String, TreeMap<Integer, List<QSpan>>> content_custom_matches = new HashMap<>();

        for (DictSearch qtSearchable : extractDictionaries) {
            Pattern ptr = qtSearchable.getDictionary().getPattern();
            if (ptr == null || !ptr.pattern().equals(AUTO))  continue;
            for (Pattern aut_ptr : AUTO_Patterns) {
                TreeMap<Integer, List<QSpan>> candidateValues = findPatterns(content, aut_ptr, lineTextBoxMap, false);
        //        TreeMap<Integer, List<QSpan>> groupedVertical = groupVerticalValues2(candidateValues, lineLabelMap, 10);
                groupVerticalValues(candidateValues, lineLabelMap, 10);
                content_custom_matches.put(aut_ptr.pattern(), candidateValues);
            }
            break;
        }

        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();
        for (DictSearch qtSearchable : extractDictionaries) {
            String dicId = qtSearchable.getDictionary().getId();
            Collection<QSpan> extIntervals = labels.get(dicId);
            if (extIntervals == null) continue;

            Pattern ptr = qtSearchable.getDictionary().getPattern();
            String ptr_str = ptr == null ? "" : ptr.pattern();

            if (ptr_str.isEmpty()) {
                for (QSpan q : extIntervals) {
                    foundValues.add(q.getExtInterval());
                }
            } else {
                valueNeededDictionaryMap.put(dicId, qtSearchable);
                if (ptr_str.startsWith(AUTO) && !ptr_str.equals(AUTO)) {
                    Pattern pattern = Pattern.compile(ptr_str.replace(AUTO, ""));
                    TreeMap<Integer, List<QSpan>> candidateValues = findPatterns(content, pattern, lineTextBoxMap, false);
        //            TreeMap<Integer, List<QSpan>> grouped = groupVerticalValues2(candidateValues, null, 30);
                    groupVerticalValues(candidateValues, null, 30);
                    content_custom_matches.put(ptr_str, candidateValues);
                }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) {
            return foundValues;
        }

        // print associates
        /*
        for (Pattern aut_ptr : AUTO_Patterns){
            TreeMap<Integer, List<ExtIntervalTextBox>> v = content_custom_matches.get(aut_ptr.pattern());
            if (v == null) continue;
            for (Map.Entry<Integer, List<ExtIntervalTextBox>> e : v.entrySet()){
                for (ExtIntervalTextBox ee : e.getValue()){
                    String h = ee.interval.getStr();
                    List<String> as = new ArrayList<>();
                    for (ExtIntervalTextBox eee : ee.associates){
                        as.add(eee.interval.getStr());
                    }
                    logger.info(" {} -> {}", h, String.join(",", as));
                }
            }
        }

         */

        TreeMap<Integer, List<QSpan>> g1_vals      = content_custom_matches.get(GenericDate1.pattern());
        TreeMap<Integer, List<QSpan>> g2_vals      = content_custom_matches.get(GenericDate2.pattern());
        TreeMap<Integer, List<QSpan>> n1_vals      = content_custom_matches.get(Numbers.pattern());
        TreeMap<Integer, List<QSpan>> id_vals      = content_custom_matches.get(Id1.pattern());
        TreeMap<Integer, List<QSpan>> generic_vals = content_custom_matches.get(Generic.pattern());

        Map<Integer, Integer> allValueLines = new HashMap<>();
        for (TreeMap<Integer, List<ExtIntervalTextBox>> c_vals : new TreeMap [] {g1_vals, g2_vals, n1_vals, id_vals}) {
            if (c_vals == null) continue;
            for (Map.Entry<Integer, List<ExtIntervalTextBox>> e : c_vals.entrySet()){
                int line = e.getKey();
                Integer num = allValueLines.get(line);
                if (num == null){
                    num = 0;
                }
                allValueLines.put(line, num + e.getValue().size());
            }
        }

        List<QSpan> allLabels = new ArrayList<>();
        for (Map.Entry<String, Collection<QSpan>> e : labels.entrySet()) {
            allLabels.addAll(e.getValue());
        }

        for (QSpan qSpan : allLabels) {
            String dictId = qSpan.getDict_id();
            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            String ptr = dictSearch.getDictionary().getPattern().pattern();
            boolean isAuto = ptr.equals(AUTO);
            TreeMap<Integer, List<QSpan>> candidateValues = isAuto ? null : content_custom_matches.get(ptr);

            if (isAuto || (candidateValues != null && candidateValues.size() > 0)) {
                if (qSpan.getTextBox() == null) continue;

                //TODO:improve this
                LineInfo lineInfo = new LineInfo(content, qSpan.getExtInterval());
                String line_str = lineTextBoxMap.get(lineInfo.getLineNumber()).getLine_str();
                int offset = lineInfo.getLocalEnd();
                qSpan.setStart(lineInfo.getLocalStart());
                qSpan.setEnd(lineInfo.getLocalEnd());
                qSpan.setLine(lineInfo.getLineNumber());
                qSpan.setLine_str(line_str);

                //    int offset = extIntervalTextBox.getExtInterval().getLocal_end();
                String sub_str = line_str.substring(offset);
                Matcher m = simple_form_val.matcher(sub_str);
                ExtIntervalTextBox singleFormValueInterval = null;
                if (m.find()) {
                    ExtInterval interval = new ExtInterval();
                    interval.setStr(m.group(1));
                    interval.setLine(qSpan.getLine());
                    interval.setStart(offset + m.start(1));
                    interval.setEnd(offset + m.end(1));
                    singleFormValueInterval = new ExtIntervalTextBox(new ExtIntervalLocal(interval), qSpan.getTextBox());
                }

                AutoValue bestAutoValue = new AutoValue();

                if (isAuto) {
                    if (singleFormValueInterval != null ){
                        bestAutoValue = new AutoValue();
                        bestAutoValue.h_score = 0;
                        bestAutoValue.hValue = singleFormValueInterval;
                    } else {
                        Integer val_per_line = allValueLines.get(qSpan.getLine());
                        boolean isPotentialTableHeader =  val_per_line == null || val_per_line < 2;

                        for (TreeMap<Integer, List<QSpan>> c_vals : new TreeMap [] {g1_vals, g2_vals, n1_vals, id_vals}) {
                            AutoValue autoValue = findBestValue(lineTextBoxMap, lineLabelMap, labels, qSpan, c_vals, isPotentialTableHeader, true);
                            boolean hasOverlapWithLabels  = hasOvelapWLabels(autoValue, allLabels);
                            if (!hasOverlapWithLabels) {
                                bestAutoValue.merge(autoValue);
                            }
                        }
                        if (bestAutoValue.hValue == null){
                            AutoValue genericMatches = findBestValue(lineTextBoxMap, lineLabelMap, labels, qSpan, generic_vals, isPotentialTableHeader, true);
                            if( bestAutoValue.vValue.size() != 0){
                                if (genericMatches.vValue.size() != 0){
                                    ExtIntervalTextBox firstAutoValue = bestAutoValue.vValue.get(0);
                                    ExtIntervalTextBox firstGenericMatch = genericMatches.vValue.get(0);
                                    // generic match is higher, then bestAutoValue is not related to the key -- ignore it
                                    if (firstGenericMatch.getTextBox().getTop() < firstAutoValue.getTextBox().getTop()){
                                        bestAutoValue.hValue = null;
                                        bestAutoValue.h_score = 10000f;
                                    }
                                }
                            } else {
                                bestAutoValue = genericMatches;
                                if (bestAutoValue.hValue != null) {
                                    for (TreeMap<Integer, List<QSpan>> c_vals : new TreeMap[]{g1_vals, g2_vals, n1_vals, id_vals}) {
                                        QSpan tmpQspan = new QSpan(bestAutoValue.hValue);
                                        AutoValue v_vals = findBestVerticalValues(lineTextBoxMap, lineLabelMap, tmpQspan, c_vals, false);
                                        if (v_vals.vValue.size() > 0) {
                                            bestAutoValue.hValue = null;
                                            bestAutoValue.h_score = 10000f;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (singleFormValueInterval != null && Pattern.matches(ptr.replace(AUTO, ""), singleFormValueInterval.getExtInterval().getStr())){
                        bestAutoValue = new AutoValue();
                        bestAutoValue.h_score = 0;
                        bestAutoValue.hValue = singleFormValueInterval;
                    } else {
                        bestAutoValue = findBestValue(lineTextBoxMap, lineLabelMap, labels,
                                qSpan, candidateValues, true, false);
                    }
                }

                List<Interval> best = bestAutoValue.getBest();
                if (best.size() > 0) {
                    qSpan.setExtIntervalSimples(best);
                    foundValues.add(qSpan.getExtInterval());
                }

            } else {
                //TODO: extinterval has global positions for start and end of the line
                List<Interval> rowValues = findAllHorizentalMatches(content, dictSearch, qSpan.getExtInterval());
                if (searchVertical && rowValues.size() == 0) {
                    rowValues = findAllVerticalMatches(content, lineTextBoxMap, dictSearch, qSpan.getExtInterval());
                }

                if (rowValues.size() > 0) {

                    for (Interval eis : rowValues){
                        LineInfo extIntervalLineInfo = new LineInfo(content, eis);
                        eis.setEnd(extIntervalLineInfo.getLocalEnd());
                        eis.setStart(extIntervalLineInfo.getLocalStart());
                        eis.setLine(extIntervalLineInfo.getLineNumber());
                    }

                    qSpan.setExtIntervalSimples(rowValues);
                    LineInfo lineInfo = new LineInfo(content, qSpan.getExtInterval());
                    qSpan.setStart(lineInfo.getLocalStart());
                    qSpan.setEnd(lineInfo.getLocalEnd());
                    qSpan.setLine(lineInfo.getLineNumber());
                    foundValues.add(qSpan.getExtInterval());
                }
            }
        }

        // if a value is assigned to more than one key, pick the closest key
        Map<String, Integer> best_found_values = new HashMap<>();
        for (ExtInterval extInterval : foundValues){
                List<Interval> vals = extInterval.getExtIntervalSimples();
                if (vals == null) break;
                for (Interval interval : vals){
                    String key = extInterval.getStr() + "-" + interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
                    int dist = interval.getLine() - extInterval.getLine();
                    Integer d = best_found_values.get(key);
                    if (d == null || d>dist){
                        best_found_values.put(key, dist);
                    }
                }
            }

        for (ExtInterval extInterval : foundValues){
                List<Interval> vals = extInterval.getExtIntervalSimples();
                if (vals == null) break;
                ListIterator<Interval> iterator = vals.listIterator();
                while (iterator.hasNext()){
                    Interval interval = iterator.next();
                    String key = extInterval.getStr() + "-" + interval.getStart() + "-" + interval.getEnd() + "-" + interval.getLine();
                    int dist = interval.getLine() - extInterval.getLine();
                    Integer d = best_found_values.get(key);
                    if (d != null && d != dist){
                        iterator.remove();
                    }
                }
            }

        return foundValues;
    }

    private static class ExtIntervalLocal extends ExtInterval {
        private int local_start;
        private int local_end;

        public ExtIntervalLocal(ExtInterval e){
            this.setStart(e.getStart());
            this.setEnd(e.getEnd());
            this.setLine(e.getLine());
            this.setDict_name(e.getDict_name());
            this.setDict_id(e.getDict_id());
            this.setCategory(e.getCategory());
            this.setLine(e.getLine());
            this.setStr(e.getStr());
        }

        public int getLocal_start() {
            return local_start;
        }

        public void setLocal_start(int local_start) {
            this.local_start = local_start;
        }

        public int getLocal_end() {
            return local_end;
        }

        public void setLocal_end(int local_end) {
            this.local_end = local_end;
        }
    }

    private TreeMap<Integer, List<QSpan>> findPatterns(String content,
                                                       Pattern regex,
                                                       Map<Integer, BaseTextBox> lineTextBoxMap,
                                                       boolean ignorePrefixBoxes) {

        TreeMap<Integer, List<QSpan>> values = new TreeMap<>();
        if (lineTextBoxMap == null || lineTextBoxMap.size() == 0) return values;
        Matcher matcher = regex.matcher(content);
        int group = matcher.groupCount() >= 1 ? 1 : 0;
        while (matcher.find()){
            int s = matcher.start(group);
            int e = matcher.end(group);
            if (s <0 || e <0) continue;
            String str = matcher.group(group);

            ExtInterval extInterval = new ExtInterval();
            extInterval.setStr(str);
            extInterval.setStart(s);
            extInterval.setEnd(e);

            LineInfo lineInfo = new LineInfo(content, extInterval);
            BaseTextBox textBox = findAssociatedTextBox(lineTextBoxMap, str, lineInfo,  ignorePrefixBoxes);
            if (textBox == null) {
                logger.debug("{} wasn't matched to any textbox", str);
                continue;
            }
            textBox.setLine(lineInfo.getLineNumber());
            List<QSpan> list = values.get(lineInfo.getLineNumber());
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.getLineNumber(), list);
            }

            ExtInterval ext = new ExtInterval();
            ext.setStart(lineInfo.getLocalStart());
            ext.setEnd(lineInfo.getLocalEnd());
            ext.setStr(str);
            ext.setLine(lineInfo.getLineNumber());
            QSpan qSpan = new QSpan(new ExtIntervalTextBox(ext, textBox));
            list.add(qSpan);
        }

        return values;
    }

    private void groupVerticalValues(TreeMap<Integer, List<QSpan>> values,
                                     Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                     float heightMult){
        if (values.size() == 0) return;

        int last_line = values.lastEntry().getKey();
        Iterator<Map.Entry<Integer, List<QSpan>>> mapIter = values.entrySet().iterator();
        // find associates
        while (mapIter.hasNext()) {
            Map.Entry<Integer, List<QSpan>> entry = mapIter.next();
            List<QSpan> etbList1 = entry.getValue();
            int line1 = entry.getKey();
            ListIterator<QSpan> iter1 = etbList1.listIterator();
            while (iter1.hasNext()){
                QSpan etb1 = iter1.next();
                BaseTextBox tb1 = etb1.getTextBox();
                if (tb1 == null){
                    iter1.remove();
                    continue;
                }

                /*
                if (lineLabelMap != null) {
                    List<ExtIntervalTextBox> lineTbs = lineLabelMap.get(line1);
                    if (lineTbs != null){
                        boolean valueIsKey = false;
                        for (ExtIntervalTextBox etb : lineTbs){
                            if (etb.getTextBox() == null) continue;
                            float ho =  getHorizentalOverlap(etb.getTextBox(), tb1);
                            if (ho >0){
                                iter1.remove();
                                valueIsKey = true;
                                break;
                            }
                        }
                        if (valueIsKey) continue;
                    }
                }
                 */

                float l1 = tb1.getLeft();
                float r1 = tb1.getRight();
                float b1 = tb1.getBase();

                float char_width = (r1 - l1) / etb1.getExtInterval().getStr().length();
                boolean column_scan_ended = false;

                for (int line2 = line1+1; line2 <= last_line; line2++){
                    if (column_scan_ended) break;

                    // check if the values have overlap with another header - the case where we have stacked table headers
                    if (lineLabelMap != null) {
                        List<ExtIntervalTextBox> lineTbs = lineLabelMap.get(line2);
                        if (lineTbs != null){
                            boolean valueIsKey = false;
                            for (ExtIntervalTextBox etb : lineTbs){
                                if (etb.getTextBox() == null) continue;
                                BaseTextBox miniTb = new BaseTextBox();
                                miniTb.setLeft(etb.getTextBox().getChilds().get(0).getLeft());
                                miniTb.setRight(etb.getTextBox().getChilds().get(etb.getTextBox().getChilds().size()-1).getRight());

                                boolean ho = headerAlignedWithCell(miniTb, tb1, char_width);
                                if (ho){
                                    valueIsKey = true;
                                    break;
                                }
                            }
                            if (valueIsKey) break;
                        }
                    }


                    List<QSpan> etbList2 = values.get(line2);
                    if (etbList2 == null) continue;

                    ListIterator<QSpan> iter2 = etbList2.listIterator();
                    while (iter2.hasNext()) {
                        QSpan etb2 = iter2.next();
                        BaseTextBox tb2 = etb2.getTextBox();
                        if (tb2 == null) {
                            iter2.remove();
                            continue;
                        }

                        if (lineLabelMap != null) {
                            List<ExtIntervalTextBox> lineTbs = lineLabelMap.get(line2);
                            if (lineTbs != null){
                                boolean valueIsKey = false;
                                for (ExtIntervalTextBox etb : lineTbs){
                                    if (etb.getTextBox() == null) continue;
                                    float ho = getHorizentalOverlap(etb.getTextBox(), tb2);
                                    if (ho >0){
                                        valueIsKey = true;
                                        break;
                                    }
                                }
                                if (valueIsKey) break;
                            }
                        }

                        boolean isAligned = headerAlignedWithCell(tb1, tb2, char_width);

                        if (isAligned){
                            float t2 = tb2.getTop();
                            // check if the vertical values are not well spaces
                            int numv = etb1.getExtIntervalTextBoxes().size();
                            float dist_from_prev = numv == 0 ? t2 - b1 :
                                    tb2.getTop() - etb1.getExtIntervalTextBoxes().get(numv-1).getTextBox().getBase();
                            if (dist_from_prev < 0) {
                                logger.debug("Distance is negative!!!");
                                continue;
                            }
                            float h2 = tb2.getBase() - t2;
                            if (dist_from_prev > heightMult * h2) break;
                            etb1.add(new ExtIntervalTextBox(etb2.getExtInterval(), etb2.getTextBox()));
                            break;
                        }
                    }
                }
            }
        }

        // remove redundant enteris

        HashSet<String> uniqueIntervals = new HashSet<>();
        for (Map.Entry<Integer, List<QSpan>> e : values.entrySet()){
            List<QSpan> etbList = e.getValue();
            for (QSpan mainEtb : etbList){
                List<ExtIntervalTextBox> eitbList = mainEtb.getExtIntervalTextBoxes();
                if (eitbList.size() < 2) continue;
                // [1] and beyond are the associated vertical boxes
                for (int i=1; i < eitbList.size(); i++) {
                    ExtInterval etb = eitbList.get(i).getExtInterval();
                    String key = etb.getLine() + "_" + etb.getStart() + "_" + etb.getEnd();
                    uniqueIntervals.add(key);
                }
            }
        }

        for (Map.Entry<Integer, List<QSpan>> e : values.entrySet()){
            List<QSpan> etbList = e.getValue();
            ListIterator<QSpan> iter = etbList.listIterator();
            while(iter.hasNext()){
                ExtInterval etb = iter.next().getExtInterval();
                String key = etb.getLine() + "_" + etb.getStart() + "_" + etb.getEnd();
                if (uniqueIntervals.contains(key)){
                    iter.remove();
                }
            }
        }

        mapIter = values.entrySet().iterator();
        // find associates
        while (mapIter.hasNext()) {
            Map.Entry<Integer, List<QSpan>> entry = mapIter.next();
            List<QSpan> etbList = entry.getValue();
            if (etbList == null || etbList.size() == 0) {
                mapIter.remove();
            }
        }

        //debug
        /*
        for (Map.Entry<Integer, List<QSpan>> e : values.entrySet()){
            List<QSpan> qspans = e.getValue();
            for (QSpan qSpan : qspans){
                StringBuilder groups = new StringBuilder();
                groups.append("Line " + qSpan.getExtInterval().getLine() +": ");
                for (ExtIntervalTextBox ee : qSpan.getExtIntervalTextBoxes()){
                    groups.append(ee.getExtInterval().getStr()).append("\t");
                }
                logger.info(groups.toString().trim());
            }
        }

         */
    }

    private TreeMap<Integer, List<QSpan>> groupVerticalValues2(TreeMap<Integer, List<QSpan>> values,
                                                               Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                                               float heightMult){
        TreeMap<Integer, List<QSpan>> groupedValues = new TreeMap<>();
        if (values.size() == 0) return groupedValues;


        List<QSpan> allQSpans = new ArrayList<>();

        for (Map.Entry<Integer, List<QSpan>> e : values.entrySet()){
            for (QSpan qSpan : e.getValue()) {
                qSpan.process(null);
                qSpan.setLine(e.getKey());
                allQSpans.add(qSpan);
            }
        }

        HashSet<Integer> merged = new HashSet<>();
        //align left
        for (int i=0; i<allQSpans.size(); i++){
            if (merged.contains(i)) continue;
            QSpan qSpan1 = allQSpans.get(i);
            double l1 = qSpan1.getLeft();
            double c1 = .5*qSpan1.getRight() + .5*qSpan1.getLeft();
            double r1 = qSpan1.getRight();
            int match_method = -1; // 0 for left align, 1 for center align, 1 for right align
            float dist_from_prev = -1;
            for (int j=i+1; j<allQSpans.size(); j++){
                if (merged.contains(j)) continue;
                QSpan qSpan2 = allQSpans.get(j);
                double l2 = qSpan2.getLeft();  //0
                double c2 = .5*qSpan2.getRight() + .5*qSpan2.getLeft();  //1
                double r2 = qSpan2.getRight();   //2

                if (match_method == -1) {
                    if (Math.abs(l1 - l2) < 5) {
                        match_method = 0;
                    } else if (Math.abs(r1 - r2) < 5) {
                        match_method = 2;
                    } else if (Math.abs(c1 - c2) < 5) {
                        match_method = 1;
                    }
                }
                if (match_method == -1) continue;
                if (match_method == 0){
                    if (Math.abs(l1 - l2) >= 5) continue;
                }
                if (match_method == 1){
                    if (Math.abs(c1 - c2) >= 5) continue;
                }
                if (match_method == 2){
                    if (Math.abs(r1 - r2) >= 5) continue;
                }


                float curr_dist_from_prev = qSpan2.getBase() - qSpan1.getBase();

                if (curr_dist_from_prev <= 0 ) continue;

                if (dist_from_prev > 0) {
                    float r = curr_dist_from_prev / dist_from_prev;
                    if ( r > 2.5 || r < .4) continue;
                    dist_from_prev = (qSpan1.getExtIntervalTextBoxes().size() * dist_from_prev + curr_dist_from_prev)
                            / (qSpan1.getExtIntervalTextBoxes().size() + 1);
                } else {
                    dist_from_prev = curr_dist_from_prev;
                }

                float h2 = qSpan2.getBase() - qSpan2.getTop();
                if (dist_from_prev > heightMult * h2) break;

                qSpan1.add(qSpan2.getExtIntervalTextBoxes().get(0));
                merged.add(j);
                qSpan1.process(null);
                l1 = qSpan1.getLeft();
                c1 = .5*qSpan1.getRight() + .5*qSpan1.getLeft();
                r1 = qSpan1.getRight();

            }
        }

        for (int i=0; i<allQSpans.size(); i++) {
            if (merged.contains(i)) continue;
            QSpan qSpan = allQSpans.get(i);
            Integer line = qSpan.getLine();
            List<QSpan> list = groupedValues.get(line);
            if (list == null){
                list = new ArrayList<>();
                groupedValues.put(line, list);
            }
            list.add(qSpan);
        }

        //debug
        /*
        for (Map.Entry<Integer, List<QSpan>> e : groupedValues.entrySet()){
            List<QSpan> qspans = e.getValue();
            for (QSpan qSpan : qspans){
                StringBuilder groups = new StringBuilder();
                groups.append("Line " + qSpan.getExtInterval().getLine() +": ");
                for (ExtIntervalTextBox ee : qSpan.getExtIntervalTextBoxes()){
                    groups.append(ee.getExtInterval().getStr()).append("\t");
                }
                logger.info(groups.toString().trim());
            }
        }

         */
        return groupedValues;
    }

    private TreeMap<Integer, List<QSpan>> groupHorizantalValues(TreeMap<Integer, List<QSpan>> values,
                                                               Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                                               float heightMult){
        TreeMap<Integer, List<QSpan>> groupedValues = new TreeMap<>();
        if (values.size() == 0) return groupedValues;


        List<QSpan> allQSpans = new ArrayList<>();

        for (Map.Entry<Integer, List<QSpan>> e : values.entrySet()){
            for (QSpan qSpan : e.getValue()) {
                qSpan.process(null);
                qSpan.setLine(e.getKey());
                allQSpans.add(qSpan);
            }
        }

        HashSet<Integer> merged = new HashSet<>();
        //align left
        for (int i=0; i<allQSpans.size(); i++){
            if (merged.contains(i)) continue;
            QSpan qSpan1 = allQSpans.get(i);
            BaseTextBox b1 = qSpan1.getTextBox();
            if (b1 == null) continue;

            for (int j=i+1; j<allQSpans.size(); j++){
                if (merged.contains(j)) continue;
                QSpan qSpan2 = allQSpans.get(j);
                BaseTextBox b2 = qSpan2.getTextBox();
                if (b2 == null) continue;

                float overlap = getVerticalOverlap(b1, b2);
                if (overlap < .4) continue;

                qSpan1.add(qSpan2.getExtIntervalTextBoxes().get(0));
                merged.add(j);
                qSpan1.process(null);
            }
        }

        for (int i=0; i<allQSpans.size(); i++) {
            if (merged.contains(i)) continue;
            QSpan qSpan = allQSpans.get(i);
            Integer line = qSpan.getLine();
            List<QSpan> list = groupedValues.get(line);
            if (list == null){
                list = new ArrayList<>();
                groupedValues.put(line, list);
            }
            list.add(qSpan);
        }

        return groupedValues;
    }
    private boolean headerAlignedWithCell(BaseTextBox tb1,
                                          BaseTextBox tb2,
                                          float char_width){

        float l1 = tb1.getLeft();
        float r1 = tb1.getRight();
        float c1 = (r1 + l1 ) / 2;

        float l2 = tb2.getLeft();
        float r2 = tb2.getRight();

        float c2 = (r2 + l2 ) / 2;
        float ld = Math.abs(l1-l2);
        float rd = Math.abs(r1-r2);
        float cd = Math.abs(c1-c2);
        if (ld < char_width || rd < char_width || cd < char_width) return true;
        return false;
    }

    private static class AutoValue{
        float h_score = 100000f;
        float v_score = 100000f;
        ExtIntervalTextBox hValue = null;
        List<ExtIntervalTextBox> vValue = new ArrayList<>();
        public AutoValue(){

        }

        public void merge(AutoValue autoValue){
            if (autoValue.hValue != null) {
                if (autoValue.h_score < h_score) {
                    hValue = autoValue.hValue;
                    h_score = autoValue.h_score;
                }
            }
            if (autoValue.vValue.size() >0) {
                if (autoValue.v_score < v_score) {
                    vValue = autoValue.vValue;
                    v_score = autoValue.v_score;
                }
            }
        }

        public List<Interval> getBest(){
            List<Interval> list = new ArrayList<>();
            if (hValue != null && vValue.size() >0){
                ExtInterval h = hValue.getExtInterval();
                ExtInterval v = vValue.get(0).getExtInterval();
                if (h.getStart() == v.getStart() && h.getLine() == v.getLine()){
                    for (ExtIntervalTextBox etb : vValue) {
                        list.add(etb.getExtInterval());
                    }
                } else {
                    String h_str = h.getStr();
                    String v_str = v.getStr();

                    Matcher mh = Digits.matcher(h_str);
                    Matcher vh = Digits.matcher(v_str);
                    boolean hHasDigit = mh.find();
                    boolean vHasDigit = vh.find();

                    if (hHasDigit && !vHasDigit) {
                        list.add(hValue.getExtInterval());
                    } else if (!hHasDigit && vHasDigit) {
                        for (ExtIntervalTextBox etb : vValue) {
                            list.add(etb.getExtInterval());
                        }
                    }
                }
            }
            if (list.size() == 0){
                if (v_score < h_score && v_score < 200f) {
                    for (ExtIntervalTextBox etb : vValue) {
                        list.add(etb.getExtInterval());
                //        for (Interval itv : etb.getIntervals()){
                //            list.add(itv);
                //        }
                //        list.add(etb.getExtInterval());
                    }
                } else if (hValue != null){
                    list.add(hValue.getExtInterval());
                }
            }

            return list;
        }
    }

    private AutoValue findBestVerticalValues(Map<Integer, BaseTextBox> lineTextBoxMap,
                                             Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                             QSpan labelSpan,
                                             TreeMap<Integer, List<QSpan>> candidateValues,
                                             boolean isAuto){
        AutoValue verticalValues = new AutoValue();
        if (candidateValues == null || candidateValues.size() == 0) return verticalValues;

        String header_str = lineTextBoxMap.get(labelSpan.getLine()).getLine_str();

        int lastLine = candidateValues.lastEntry().getKey();
        ExtIntervalTextBox lastMatched = labelSpan.getExtIntervalTextBoxes().get(0);
        float header_h = labelSpan.getTextBox().getBase() - labelSpan.getTextBox().getTop();
        int max_key_value_distance_in_lines =  20;

        BaseTextBox header_first = lastMatched.getTextBox();
        BaseTextBox header_last = lastMatched.getTextBox();


   //     BaseTextBox header_first = lastMatched.getTextBox().getChilds().get(0);
   //     BaseTextBox header_last = lastMatched.getTextBox().getChilds().get(extIntervalTextBox.getTextBox().getChilds().size()-1);

        float average_char_width = 1.5f * (header_last.getRight() - header_first.getLeft()) / labelSpan.getExtInterval().getStr().length();
        for (int keyLine = labelSpan.getLine()+1 ; keyLine <= lastLine; keyLine++) {

            /*
            if (lineLabelMap != null) {
                float r1 = lastMatched.getTextBox().getChilds().get(lastMatched.getTextBox().getChilds().size()-1).getRight();
                float l1 = lastMatched.getTextBox().getChilds().get(0).getLeft();

                float char_width = (r1 - l1) / lastMatched.getExtInterval().getStr().length();

                List<ExtIntervalTextBox> lineTbs = lineLabelMap.get(keyLine);
                if (lineTbs != null){
                    boolean valueIsKey = false;
                    for (ExtIntervalTextBox etb : lineTbs){
                        if (etb.getTextBox() == null) continue;
                        TextBox miniTb = new TextBox();
                        miniTb.setLeft(etb.getTextBox().getChilds().get(0).getLeft());
                        miniTb.setRight(etb.getTextBox().getChilds().get(etb.getTextBox().getChilds().size()-1).getRight());

                        boolean ho = headerAlignedWithCell(miniTb, lastMatched.getTextBox(), char_width);
                        if (ho){
                            valueIsKey = true;
                            break;
                        }
                    }
                    if (valueIsKey) break;
                }
            }

             */

            List<QSpan> lineValues = candidateValues.get(keyLine);
            if (lineValues == null) continue;

            if (isAuto){
                // we only compare row/header alignment for AUTO mode
                boolean isAligned = compareRows(header_str, lineTextBoxMap.get(keyLine).getLine_str());
                if (!isAligned) continue;
            }

            List<ExtIntervalTextBox> line_items = new ArrayList<>();

            for (int i=0; i< lineValues.size(); i++) {
                QSpan qSpan = lineValues.get(i);
                BaseTextBox candidate_vtb = qSpan.getTextBox();
                if (candidate_vtb == null) {
                    logger.warn("NO textbox {}", qSpan.getExtInterval().getStr());
                    continue;
                }

                List<BaseTextBox> vtbs = candidate_vtb.getChilds();
                BaseTextBox first = vtbs.get(0);
                BaseTextBox last = vtbs.get(vtbs.size()-1);

                float dvtc = vtbs.get(0).getTop() - lastMatched.getTextBox().getBase();
                if (dvtc > max_key_value_distance_in_lines * header_h) break;

                float d_left = Math.abs(first.getLeft() - header_first.getLeft());
                float d_center = .5f * Math.abs(last.getRight() - header_last.getRight() + first.getLeft() - header_first.getLeft());
                float d_right = Math.abs(last.getRight() - header_last.getRight());

                float vOverlap = getHorizentalOverlap(labelSpan.getTextBox(), candidate_vtb, false);

                if (vOverlap > .2) { //TODO: .95 ?
                    if (verticalValues.vValue.size() == 0) {
                        float min_horizental_d = Math.min(Math.min(d_left, d_center), d_right);
                        float s = (float) Math.sqrt(min_horizental_d * min_horizental_d + dvtc * dvtc);
                        if (s < verticalValues.v_score ){
                            verticalValues.v_score = s;
                            max_key_value_distance_in_lines =  15;
                            line_items.addAll(qSpan.getExtIntervalTextBoxes());
                        }
                    } else {
                        boolean isValidVerticalValue = (d_left < average_char_width  || d_center < average_char_width || d_right < average_char_width);
                        float local_vOverlap = getHorizentalOverlap(lastMatched.getTextBox(), candidate_vtb, false);
                        if (isValidVerticalValue || local_vOverlap > .9) {
                            line_items.addAll(qSpan.getExtIntervalTextBoxes());
                        }
                    }
                }
            }

            if (line_items.size() == 0) continue;
            verticalValues.vValue.addAll(line_items);

            /*
            ExtIntervalTextBox bestInterval = line_items.get(0);

            if (line_items.size() > 1 ) {
                float d = 100000f;
                BaseTextBox origHeader = extIntervalTextBox.getTextBox().getChilds().get(0);
                float header_center = (origHeader.getRight() + origHeader.getLeft()) / 2;
                for (ExtIntervalTextBox esm : line_items) {
                    float center = (esm.getTextBox().getRight() + esm.getTextBox().getLeft()) / 2;
                    float dist = Math.abs(center - header_center);
                    if (dist < d) {
                        bestInterval = esm;
                        d = dist;
                    }
                }
            }

    //        verticalValues.vValue.add(bestInterval);
            float header_width = extIntervalTextBox.getTextBox().getRight() - extIntervalTextBox.getTextBox().getLeft();

            List<BaseTextBox> e_childs = bestInterval.getTextBox().getChilds();
            float w = e_childs.get(e_childs.size()-1).getRight() - e_childs.get(0).getLeft();
            if (w > header_width) continue;
            verticalValues.vValue.add(bestInterval);

             */
            /*
            for (ExtIntervalTextBox etb : bestInterval.associates){
                List<BaseTextBox> e_childs = etb.getTextBox().getChilds();
                float w = e_childs.get(e_childs.size()-1).getRight() - e_childs.get(0).getLeft();
                if (w > header_width) continue;
                verticalValues.vValue.add(etb);
            }

             */
            break;
        }

        return verticalValues;
    }
    private AutoValue findBestValue(Map<Integer, BaseTextBox> lineTextBoxMap,
                                    Map<Integer, List<ExtIntervalTextBox>> lineLabelMap,
                                    Map<String, Collection<QSpan>> labels,
                                    QSpan labelSpan,
                                    TreeMap<Integer, List<QSpan>> candidateValues,
                                    boolean isPotentialTableHeader,
                                    boolean isAuto)
    {
        // check for simple form values
        AutoValue autoValue = new AutoValue();
        if (candidateValues == null || candidateValues.size() == 0) return autoValue;

        if (labelSpan.getSpanType() != null && labelSpan.getSpanType() == VERTICAL_MANY) {
            AutoValue vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                    labelSpan, candidateValues, isAuto);
    //        if (autoValue.vValue.size() == 0){
    //            TextBox.extendToNeighbours(lineTextBoxMap, labels, labelSpan);
    //            vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
    //                    labelSpan, candidateValues, isAuto);
    //        }
            return vertical_1;
        }

        HashMap<ExtIntervalTextBox, Double> nearSingleMatches = new HashMap<>();
        for (Map.Entry<Integer, List<QSpan>> e : candidateValues.entrySet()) {
            List<QSpan> qSpans = e.getValue();
            for (QSpan qSpan : qSpans) {
                List<ExtIntervalTextBox> eitbs = qSpan.getExtIntervalTextBoxes();
                for (ExtIntervalTextBox eitb : eitbs) {
                    BaseTextBox btb = eitb.getTextBox();
                    if (btb == null) continue;
                    boolean isValidCandidate = false;
                    float vOverlap = getVerticalOverlap(labelSpan.getTextBox(), btb);
                    if (vOverlap > .3) {
                        float d = eitb.getTextBox().getLeft() - labelSpan.getTextBox().getChilds().get(labelSpan.getTextBox().getChilds().size() - 1).getRight();
                        String cur_line_str = eitb.getTextBox().getLine_str();
                        if (cur_line_str.length() > eitb.getExtInterval().getStart() && eitb.getExtInterval().getStart() > labelSpan.getEnd()) {
                            String gap = cur_line_str.substring(labelSpan.getEnd(), eitb.getExtInterval().getStart());
                            //remove (some text here) pattern
                            gap = gap.replaceAll("\\([^\\)]+\\)", "").trim();
                            gap = gap.replaceAll("[^\\p{L}\\p{N}]", "");
                            if (gap.length() == 0) {
                                isValidCandidate = true;
                            }
                        }
                    }
              //      else {
              //          float hOverlap = getHorizentalOverlap(labelSpan.getTextBox(), btb);
              //          if (hOverlap > 0) {
              //              isValidCandidate = true;
              //          }
              //      }
                    if (isValidCandidate) {
                        for (Map.Entry<BaseTextBox, Double> n : labelSpan.getNeighbors().entrySet()) {
                            float ho = getHorizentalOverlap(n.getKey(), eitb.getTextBox());
                            float vo = getVerticalOverlap(n.getKey(), eitb.getTextBox());
                            if (ho >= .99 && vo >= .99) {
                                // check if it is among the neighbors
                                nearSingleMatches.put(eitb, n.getValue());
                            }
                        }
                    }

                }
            }
        }

        if (nearSingleMatches.size() > 0) {
            Map<ExtIntervalTextBox, Double> sorted = MapSort.sortByValue(nearSingleMatches);
            autoValue.hValue = sorted.entrySet().iterator().next().getKey();
            autoValue.h_score = 0;
        }

        /*
        if (labelSpan.getSpanType() != null && labelSpan.getSpanType() == HORIZENTAL_ONE) {
            return autoValue;
        }

         */


        if (isPotentialTableHeader) {
            AutoValue vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                    labelSpan, candidateValues, isAuto);
            if (vertical_1.vValue.size() == 0){
                TextBox.extendToNeighbours(lineTextBoxMap, labels, labelSpan);
                vertical_1 = findBestVerticalValues(lineTextBoxMap, lineLabelMap,
                        labelSpan, candidateValues, isAuto);
            }
            autoValue.merge(vertical_1);
        }

        return autoValue;
    }


    private boolean compareRows(String header_str,
                                String row_str){
        int l1 = header_str.length();
        int l2 = row_str.length();

        float matched = 0;
        float total = 0;

        for (int i=0; i< Math.min(l1, l2) -1; i++){
            boolean l1_is_black = true;
            boolean l2_is_black = true;
            if (header_str.charAt(i) == ' ' && header_str.charAt(i+1) == ' '){
                l1_is_black = false;
            }
            if (row_str.charAt(i) == ' ' && row_str.charAt(i+1) == ' '){
                l2_is_black = false;
            }
            if ( l1_is_black == l2_is_black) {
                matched+=1;
            }
            total+=1;
        }

        float r = matched/total;

        if (r < .45) {
            logger.debug("{}  \n\t {} \n\t {}", r, header_str, row_str);
            return false;
        }
        return true;

    }

    private boolean isConsecutiveCell(Map<Integer, BaseTextBox> lineTextBoxMap,
                                      ExtIntervalTextBox above,
                                      ExtIntervalTextBox below,
                                      float height_mult,
                                      boolean isSecondOrBeyond){
        float lastMatchH = above.getTextBox().getBase() - above.getTextBox().getTop();
        if (isSecondOrBeyond) {
            float currH = below.getTextBox().getBase() - below.getTextBox().getTop();

            float lastMatchW = above.getTextBox().getRight() - above.getTextBox().getLeft();
            float currW = below.getTextBox().getRight() - below.getTextBox().getLeft();

            float r1 = lastMatchH > currH ? currH / lastMatchH : lastMatchH / currH;
            float r2 = lastMatchW > currW ? currW / lastMatchW : lastMatchW / currW;

            if (r1 < .9 || r2 < .9) {

                int num_overlapped_inbetween = countBetweenOverlappingBoxes(lineTextBoxMap, above, below);
                if (num_overlapped_inbetween > 1) return false;
            }
        } else {
            float dist_from_previous = below.getTextBox().getTop() - above.getTextBox().getBase();
            if (dist_from_previous > height_mult * lastMatchH) return false;
        }
        return true;
    }

    private static class StartEnd {
        public float start;
        public float end;
        public float dis2Next;
        public StartEnd(float s, float e, float d) {
            start = s;
            end = e;
            dis2Next = d;
        }
    }

    private boolean whiteBordersOVerlap(List<StartEnd> startEnds1,
                                        List<StartEnd> startEnds2){

        if (startEnds1.size() < 3) return true;
        int numOverlap = 0;
        for (StartEnd se1 : startEnds1){
            TextBox tb1 = new TextBox();
            tb1.setLeft(se1.start);
            tb1.setRight(se1.end);

            for (StartEnd se2 : startEnds2){
                TextBox tb2 = new TextBox();
                tb2.setLeft(se2.start);
                tb2.setRight(se2.end);
                float overlap = getHorizentalOverlap(tb1, tb2, false);
                if (overlap >0) {
                    numOverlap++;
                    break;
                }
            }
        }
        if (( numOverlap + 4) > startEnds2.size()) return true;
        return false;
    }

    private float getArea(BaseTextBox textBox){
        return (textBox.getBase() - textBox.getTop()) * (textBox.getRight() - textBox.getLeft());
    }


    private int countBetweenOverlappingBoxes(Map<Integer, BaseTextBox> lineTextBoxMap,
                                             ExtIntervalTextBox etb1,
                                             ExtIntervalTextBox etb2){
        if (etb1 == null || etb2 == null) return 0;
        BaseTextBox tb1 = etb1.getTextBox();
        BaseTextBox tb2 = etb2.getTextBox();
        int l1 = etb1.getExtInterval().getLine();
        int l2 = etb2.getExtInterval().getLine();
        float area_top = Math.min(tb1.getTop(), tb2.getTop());
        float area_base = Math.max(tb1.getBase(), tb2.getBase());
        float area_left = Math.min(tb1.getLeft(), tb2.getLeft());
        float area_right = Math.max(tb1.getRight(), tb2.getRight());

        BaseTextBox covered_area = new BaseTextBox(area_top, area_base, area_left, area_right, "");

        int num_partial_covered = 0;
        for (int i=l1+1; i< l2; i++){   // find the number of partial-overlapping boxes
            BaseTextBox line_tb = lineTextBoxMap.get(i);
            if (line_tb == null) continue;
            List<BaseTextBox> childs = line_tb.getChilds();
            for (BaseTextBox bt : childs){
                float overlap = getHorizentalOverlap(bt, covered_area);
                if (overlap > .1){
                    num_partial_covered++;
                }
            }
        }
        return num_partial_covered;
    }

    private List<StartEnd> getWhiteBorders(List<BaseTextBox> textBoxes){

        float num = textBoxes.size();

        float total_box_len = 0;
        float total_char = 0;

        List<StartEnd> allBorders = new ArrayList<>();
        if (num == 0) return allBorders;
        allBorders.add(new StartEnd(0,textBoxes.get(0).getLeft(), textBoxes.get(0).getRight() - textBoxes.get(0).getLeft()));
        for (int i=0; i<num; i++) {
            BaseTextBox bt1 = textBoxes.get(i);
            total_box_len += (bt1.getRight() - bt1.getLeft());
            total_char += bt1.getStr().length();
            if (i != num - 1) {
                BaseTextBox nextBox = textBoxes.get(i + 1);
                float dist2Next = nextBox.getLeft() - bt1.getRight();
                StartEnd startEnd = new StartEnd(bt1.getRight(),nextBox.getLeft(), dist2Next);
                allBorders.add(startEnd);
            }
        }
        float avg_char1 = total_box_len/total_char;
        ListIterator<StartEnd> iter = allBorders.listIterator();
        while (iter.hasNext()){
            StartEnd se = iter.next();
            if (se.dis2Next < avg_char1 * 1.25){
                iter.remove();
            }
        }

        return allBorders;
    }


    private boolean addShiftedValues(String content,
                                     int start_search_shift,
                                     Pattern match_pattern,
                                     int group,
                                     Analyzer analyzer,
                                     Pattern gap_pattern,
                                     List<Interval> matches)
    {

        String string_to_search = content.substring(start_search_shift);
        Matcher m = match_pattern.matcher(string_to_search);

        if (!m.find()) return false;

        int start_of_match = m.start();

        boolean match_is_valid = validateFoundValue(content, start_search_shift,
                start_of_match + start_search_shift, analyzer,
                gap_pattern);

        if (!match_is_valid) return false;

        // start and end should be shifted to match with the position of the match in
        // content
        int start_match = m.start(group);
        int end_match = m.end(group);
        Interval ext = new Interval(start_match + start_search_shift, end_match + start_search_shift);
        String extractionStr = string_to_search.substring(start_match, end_match);
        ext.setStr(extractionStr);

        matches.add(ext);
        return true;

    }

    private boolean validateFoundValue(String content,
                                       int start,
                                       int end,
                                       Analyzer analyzer,
                                       Pattern gapPattern){
        if (gapPattern == null) return true;

        String gap_between = content.substring(start, end);
        if (gap_between.length() == 0) return true;
        //first tokenize the gap
        if (analyzer != null) {
            gap_between = tokenize(analyzer, gap_between);
            if (gap_between.isEmpty()) return true;
        }

        Matcher gapmatcher = gapPattern.matcher(gap_between);
        if (!gapmatcher.find()) return false;
        return true;
    }

    private List<Interval> findAllHorizentalMatches(String content,
                                                    DictSearch dictSearch,
                                                    Interval labelInterval)
    {
        List<Interval> results = new ArrayList<>();
        Dictionary dictionary = dictSearch.getDictionary();
        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();
        // Find the lowest priority analyzer to tokenize the gaps
        // list of DctSearhFld is the same for all fields so we get the first one

        int start_search_shift = labelInterval.getEnd();
        int group = (dictionary.getGroups() == null || dictionary.getGroups().length == 0 )  ? 0 : dictionary.getGroups()[0];
        Pattern pattern = dictionary.getPattern();
        //field matching. One value for field
        boolean canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                pattern, group, analyzer, padding_between_key_value, results);

        //Now find more than one value
        while (canContinueSearchForValue && padding_between_values != null) {
            start_search_shift = results.get(results.size() - 1).getEnd();
            canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                    pattern, group, null, padding_between_values, results);
        }

        return results;
    }

    private List<Interval> findAllVerticalMatches(String content,
                                                  Map<Integer, BaseTextBox> lineTextBoxMap,
                                                  DictSearch dictSearch,
                                                  Interval labelInterval)
    {
        ArrayList<Interval> results = new ArrayList<>();
        Dictionary dictionary = dictSearch.getDictionary();
        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();

        //Best match has closest distance to center of label in X axis

        int startLineLabelInterval = getFirstNewLineBefore(content, labelInterval.getStart());

        int first_index_after_label = startNextToken(content, labelInterval.getEnd());
        int last_index_before_label = endPrevToken(content, labelInterval.getStart());

        // Start of header label
        int offsetStartLabel = last_index_before_label  - startLineLabelInterval;
        if (offsetStartLabel < 0) offsetStartLabel = 0;

        int offsetEndLabel   = first_index_after_label  - startLineLabelInterval;

        int end_interval = content.indexOf('\n', labelInterval.getEnd());

        if (end_interval < 0) return results; // no more lines to process

        int local_start_label = labelInterval.getStart() - startLineLabelInterval;
        int local_end_label = labelInterval.getEnd() - startLineLabelInterval;
        int header_length = local_end_label - local_start_label;
        if (header_length < 4) header_length = 4;  // minimum length for english word

        String [] lines = content.substring(end_interval).split("\n");
        int position_in_lines_array = end_interval;
        List<String> verticalGap = new ArrayList<>();

        for (int lc=0; lc < lines.length; lc++){
            String line = lines[lc];
            int line_length = line.length();
            if (line_length <= offsetStartLabel) {
                position_in_lines_array += line_length + 1;
                continue;
            }
            int start_local_interval = offsetStartLabel + position_in_lines_array;
            final int end_local_interval = Math.min(offsetEndLabel, line_length) + position_in_lines_array;

            // This is experimental
            // We look under a very wide range, the end of previous header all the way to the beginning of the next header
            // If table cells are longer than table headers this can cause problems.
            // let's move the start_local_interval forward if we find a wide white area. wide white area (aka spaces) means columns!
            Matcher white_space_matcher = LONG_SPACE.matcher(line);

            Map<Integer, Integer> index_2_space_width = new HashMap<>();
            while (white_space_matcher.find()) {
                if (white_space_matcher.start() > local_end_label) break;
                int distance_from_label_start = white_space_matcher.end() - local_start_label;

                index_2_space_width.put(white_space_matcher.end(), Math.abs(distance_from_label_start));
            }

            //account for the case when header starts at the beginning of the row
            if (local_start_label == 0) {
                Matcher beginning_white_space_matcher = START_SPACE.matcher(line);
                if (beginning_white_space_matcher.find()){
                    int d = beginning_white_space_matcher.end();
                    index_2_space_width.put(d, d);
                } else {
                    index_2_space_width.put(0, 0);
                }
            }

            if (index_2_space_width.size() > 0){
                // find the closest non-wide-space string as potential table value
                Map<Integer, Integer> index_2_space_width_sorted = MapSort.sortByValue(index_2_space_width);
                // end space gap is start of the cell value
                int end_space = index_2_space_width_sorted.entrySet().iterator().next().getKey();
                int cell_distance_from_header = Math.abs(local_start_label - end_space);
                if (cell_distance_from_header < 4 * header_length)
                {
                    start_local_interval = end_space + position_in_lines_array;
                } else {
                    position_in_lines_array += line_length + 1;
                    continue;
                }
            }

            position_in_lines_array += line_length + 1;

            if (start_local_interval >= end_local_interval) continue;
            String string2Search4Value = content.substring(start_local_interval, end_local_interval);
              // 1 is the length for \n

            Interval foundValue = null;

            int group = dictionary.getGroups() != null ? 1 : 0;
            Pattern pattern = dictionary.getPattern();
            boolean stopSearch = false;
            if (verticalGap.size() >0 && pattern.pattern().contains("\\n")){
                // if we allow multiline match
                String multiLineGap = String.join("\n", verticalGap);
                Matcher m = pattern.matcher(multiLineGap);
                if (m.find()) {
                    int start = m.start(group);
                    int end = m.end(group);
                    if (start >= 0 && end > start) {
                        // for multiline - we only capture the last line match
                    //    LineInfo lineInfo = getLineInfo(content, start + start_local_interval);
                        // we don't have a way of capturing bounding box of a multiline match!
                        foundValue = new Interval(start + start_local_interval,  end + end_local_interval);
                        foundValue.setStr(multiLineGap.substring(start, end));
                        stopSearch = true;
                    }
                    verticalGap.clear();
                }
            }
            if (!stopSearch) {
                Matcher m = pattern.matcher(string2Search4Value);
                if (m.find()) {
                    int start = m.start(group);
                    int end = m.end(group);
                    foundValue = new Interval(start + start_local_interval, end + start_local_interval);
                    String string_to_search = content.substring(start_local_interval);
                    String extractionStr = string_to_search.substring(start, end);
                    foundValue.setStr(extractionStr);
                }
            }

            if (foundValue == null) {
                verticalGap.add(string2Search4Value);
                continue;
            }

            if (results.size() == 0) {
                // this is field look up, one value for field
                if (padding_between_key_value == null){
                    results.add(foundValue);
                    if (padding_between_values == null) break;
                    verticalGap.clear();
                } else {
                    String vertical_gap = String.join("", verticalGap);
                    Matcher match_on_gap = padding_between_key_value.matcher(vertical_gap);
                    if (vertical_gap.isEmpty() || match_on_gap.find()) {
                        results.add(foundValue);
                        if (padding_between_values == null) break;
                        verticalGap.clear();
                    }
                }
            } else {
                String vertical_gap = String.join("", verticalGap);
                Matcher match_on_gap = padding_between_values.matcher(vertical_gap);
                if (vertical_gap.isEmpty() || match_on_gap.find()) {
                    results.add(foundValue);
                    verticalGap.clear();
                } else {
                    break;
                }
            }
        }

        return results;

    }

    protected int startNextToken(String str, int start){
        // chec if this is the last token in line
        int next_new_line = str.substring(start).indexOf("\n");
        if (next_new_line == -1){
            next_new_line = str.substring(start).length();
        }

        Matcher m = TOKEN.matcher(str.substring(start));
        if (m.find()){
            int start_next_token = m.start();
            if (start_next_token < next_new_line) {
                return start + start_next_token;
            }
            //else {
                // this is the last column/header in the line so the items below it can shift to the right as much as possible
          //      return start + 10000; // no line can be longer than 50000 ???
        //    }
        }
        return start;
    }

    protected int endPrevToken(String str, int start){
        // there is no way to search backward
        // so we create a substring just before the start and find the last match
        // 70 is long enough to look, back??
        int charBeforeStart = start;
        int beginning_of_line = getFirstNewLineBefore(str, start);
        if (beginning_of_line >= charBeforeStart) return charBeforeStart;

    //    int startString = Math.max(0, charBeforeStart - 70);
    //    if (charBeforeStart <= startString) return startString;
    //    String substringBeforeStart = str.substring(startString, charBeforeStart);
        String substringBeforeStart = str.substring(beginning_of_line, charBeforeStart);
        Matcher m = TOKEN.matcher(substringBeforeStart);
        int endPreviousToken = beginning_of_line;
        while (m.find()){
            endPreviousToken = m.end() + beginning_of_line;
        }
        return endPreviousToken;
    }

    // This is a very loose logic for assesing if the row is a header
    protected boolean lineIsTableRow(String lineStr){
        //logic to determine if a line of text is a table row
        //   xx xxxX[         ]yyyY[          ]     zzZ
        String trimmed_str = lineStr.trim();
        String[] number_large_gaps = trimmed_str.split("\\S {4,}");
        if (number_large_gaps.length - 1 > 0) return true;

        Matcher m1 = WORD_PTR.matcher(trimmed_str);
        int num_words = 0;
        while (m1.find()){
            num_words++;
        }
        if (num_words < 4) return true;
        Matcher m2 = SPC_BET_WORDS_PTR.matcher(trimmed_str);

        int num_space = 0;
        while (m2.find()){
            num_space++;
        }

        // dsds dsds dsds dsdsd dsdsd
        // aaa ffd a       hdj orii eof rere    --> not a tabke
        // fdfdfd       ffdfdfd fdfd fdfd      fdd ffd   --> could be a table
        if ( (num_words -1 ) ==  num_space ) return false;

        return true;
    }

    protected int getFirstNewLineBefore(String str, int index) {
        int index_start_line = str.substring(0, index).lastIndexOf('\n') + 1;
        if (index_start_line < 0) return 0;
        return index_start_line;
    }

    public String convertValues2titleTable(List<ExtInterval> values) {
        if (values == null) return null;
        Collections.sort(values, Comparator.comparingInt(ExtInterval::getStart));
        String result = "";
        LinkedHashSet<String> rows = new LinkedHashSet<>();
        for (ExtInterval ext : values) {

            StringBuilder sb = new StringBuilder();
            sb.append("<tr>");
            sb.append("<td>").append(ext.getCategory()).append("</td>");
            for (Interval extvStr : ext.getExtIntervalSimples()) {
                String customData = extvStr.getStr();
                if (customData == null) continue;
                sb.append("<td>").append(customData).append("</td>");
            }
            sb.append("</tr>");
            String row2add = sb.toString();
            rows.add(row2add);
        }

        if (!rows.isEmpty()) {
            if (!result.startsWith("<table ")) {
                result = "";
            }
            result += "<table width=\"100%\">" + String.join("", rows) + "</table>";
        } else {
            if (!result.startsWith("<table ")) {
                result = "";
            }
        }

        return result;
    }
}
