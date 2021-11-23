package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.doc.helper.textbox.TextBox;
import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.Dictionary.ExtractionType;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.types.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

import static com.quantxt.model.DictItm.DONT_CARE;

/**
 * Created by dejani on 1/24/18.
 */

public class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    private static String AUTO = "__AUTO__";

    private static int max_string_length_for_search = 30000;
    private static Character NewLine = '\n';
    private static Pattern WORD_PTR = Pattern.compile("\\S+");
    private static Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static Pattern SPC_BET_WORDS_PTR = Pattern.compile("\\S(?= \\S)");
    private static Pattern LONG_SPACE = Pattern.compile(" {5,}");
    private static Pattern START_SPACE = Pattern.compile("^ *");

    // Single quotes to normalize
    protected static Pattern r_quote_norm = Pattern.compile("([`‘’])");
    protected static String s_quote_norm = "'";
    // Double quotes to normalize
    protected static Pattern r_quote_norm2 = Pattern.compile("([“”]|'')");
    protected static String s_quote_norm2 = " \" ";

    // bullets
    protected static String UTF8_BULLETS = "\\u2022|\\u2023|\\u25E6|\\u2043|\\u2219";
    protected static Pattern UTF8_TOKEN = Pattern.compile("^(?:[a-zA-Z]\\.){2,}|([\\p{L}\\p{N}]+[\\.\\&]{0,1}[\\p{L}\\p{N}])");

    // Dashes to normalize
    protected static String s_dash_norm = "–";
    protected static String s_dash_norm2 = "-";
    protected static String s_dash_norm3 = "--";

    //Unicode spaces
    protected static Pattern r_white = Pattern.compile("[               　 ]+");
    protected static String s_white = " ";

    final private static String begin_pad = "(?:^| {2,})";
    final private static String end_pad   = "(?=$|\\n| {2,})";

    final private static Pattern GenericDate1  = Pattern.compile(begin_pad + "(?:[1-9]|[01]\\d)[ -\\/](?:[1-9]|[0123]\\d)[ -\\/](?:[012]\\d|19\\d{2}|20\\d{2})" + end_pad);  // mm dd yyyy
    final private static Pattern GenericDate2  = Pattern.compile(begin_pad + "/([12]\\d{3}[ -\\/](?:0[1-9]|1[0-2])[ -\\/](?:0[1-9]|[12]\\d|3[01]))/" + end_pad);  // YYYY-mm-dd
    final private static Pattern Numbers       = Pattern.compile(begin_pad + "(\\p{Sc}? {0,6}[+-]?[0-9]{1,3}(?:,?[0-9]{3})*(?:\\.[0-9]{2})?)" + end_pad);  // mm dd yyyy
    final private static Pattern AlphaNumerics = Pattern.compile(begin_pad + "(\\d{6,}|\\d{5,}[ -]\\d{5,}|[A-Z]+\\d{3,}|\\d{3,}[A-Z][A-Z\\d]+)" + end_pad);  // mm dd yyyy

    final private static Pattern [] AUTO_Patterns = new Pattern[] {GenericDate1, GenericDate2, Numbers, AlphaNumerics};
//    final private static Pattern GenericToken = Pattern.compile("(?:^ *|[^\\p{Sc}\\p{L}\\d]{2,})((?:[\\p{Sc}À-ÿ\\p{L}\\d]+(?: ?[-_,.\\/\\\\@;+%] ?| )){0,4}[\\p{Sc}À-ÿ\\p{L}\\d]+)(?=$|\\n| {2,})");
//    final private static String GenericSuffixRegexStr = "[^\\p{Sc}\\p{L}\\d]+$";

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

    protected static String normBasic(String workingLine) {
        // New: Normalize quotes
        workingLine = r_quote_norm.matcher(workingLine).replaceAll(s_quote_norm);
        workingLine = r_quote_norm2.matcher(workingLine).replaceAll(s_quote_norm2);

        // New: Normalize dashes
        workingLine = workingLine.replace(s_dash_norm, s_dash_norm2);
        workingLine = workingLine.replace(s_dash_norm3, s_dash_norm2);

        // Normalize whitespace
        workingLine = r_white.matcher(workingLine).replaceAll(s_white).trim();

        String[] parts = workingLine.split("\\s+");
        ArrayList<String> normParts = new ArrayList<>();
        for (String p : parts) {
            Matcher m = UTF8_TOKEN.matcher(p);
            if (m.find()) {
                normParts.add(m.group());
            }
        }
        return String.join(" ", normParts);
    }

    public String normalize(String workingLine) {
        return normBasic(workingLine).toLowerCase();
    }

    private void  findLabelsHelper(DictSearch dictSearch,
                                   String content,
                                   int shift,
                                   int slop,
                                   Map<Integer, ExtInterval> dicLabels){
        if (content.isEmpty()) return;
        ExtractionType extractionType = dictSearch.getDictionary().getValType();
        Collection<ExtInterval> qtMatches = dictSearch.search(content, slop);
        for (ExtInterval qtMatch : qtMatches) {
            String category = qtMatch.getCategory();
            if (category.equals(DONT_CARE)) continue;
            ExtInterval extInterval = new ExtInterval();
            extInterval.setDict_name(qtMatch.getDict_name());
            String dic_id = qtMatch.getDict_id();
            extInterval.setDict_id(dic_id);
            extInterval.setCategory(category);
            String str = qtMatch.getStr();
            //check if this is in
            extInterval.setStr(str);
            int start = qtMatch.getStart() + shift;
            int end = qtMatch.getEnd() + shift;
            extInterval.setStart(start);
            extInterval.setEnd(end);
            extInterval.setType(extractionType);
            dicLabels.put(start ,extInterval);
        }
    }

    private Map<String, Collection<ExtInterval>> findLabels(List<DictSearch> extractDictionaries,
                                                            String content,
                                                            int slop) {
        int content_length = content.length();
        Map<String, Collection<ExtInterval>> labels = new LinkedHashMap<>();

        for (DictSearch dictSearch : extractDictionaries) {
            String dict_id = dictSearch.getDictionary().getId();
            Map<Integer, ExtInterval> dicLabels = new TreeMap<>();

            if (content_length > max_string_length_for_search){
                int cnt_idx = 0;
                while (cnt_idx < content_length){
                    String cnt_chunk = content.substring(cnt_idx, Math.min(cnt_idx + max_string_length_for_search, content_length));
                    findLabelsHelper(dictSearch, cnt_chunk, cnt_idx, slop, dicLabels);
                    cnt_idx += max_string_length_for_search - 1000;
                }
            } else {
                findLabelsHelper(dictSearch, content, 0, slop, dicLabels);
            }
            if (!dicLabels.isEmpty()) {
                labels.put(dict_id, dicLabels.values());
            }
        }
        return labels;
    }

    /*
    private boolean isWhiteSpace(String str, int s, int e){
        if (s == e) return true;
        if (s > e) return false;
        if (str.substring(s, e).replaceAll(GenericSuffixRegexStr, "").length() == 0) return true;
        return false;
    }

     */

    private TextBox findAssociatedTextBox(Map<Integer, TextBox>  lineTextBoxMap,
                                          Interval interval,
                                          boolean extend_to_neighbors)
    {

        int current_line = interval.getLine();

        TextBox  line_text_boxes = lineTextBoxMap.get(current_line);
        if (line_text_boxes == null) return null;

        String str = interval.getStr();
        String line_str = line_text_boxes.getLine_str();
        TextBox surronding_box = new TextBox();

        int str_start_1 = line_str.indexOf(str);
        if (str_start_1 < 0) {
            logger.warn("{} was not found in line_str {}", str, line_str);
            return null;
        }

        List<BaseTextBox> childs = line_text_boxes.getChilds();

        //align textbox lefts with string indices
        int index = 0;
        int[][] childIdx2StrIdx = new int[childs.size()][2];

        for (int i = 0; i < childs.size(); i++) {
            BaseTextBox baseTextBox = childs.get(i);
            String child_str = baseTextBox.getStr();
            index = line_str.indexOf(child_str, index);
            childIdx2StrIdx[i][0] = index;
            childIdx2StrIdx[i][1] = index + child_str.length();
            index += child_str.length();
        }

        int start_str = interval.getStart();
        int end_str = interval.getEnd();
        BaseTextBox current_box = null;
        for (int i = 0; i < childIdx2StrIdx.length; i++) {
            int current_start = childIdx2StrIdx[i][0];
            int current_end = childIdx2StrIdx[i][1];
            current_box = childs.get(i);

            if (start_str >= current_start && start_str <= current_end) {
                surronding_box.setTop(current_box.getTop());
                surronding_box.setBase(current_box.getBase());
                surronding_box.setLeft(current_box.getLeft());
            }
            if (end_str >= current_start && end_str <= current_end) {
                surronding_box.setRight(current_box.getRight());
                break;
            }
        }

        surronding_box.setChilds(new ArrayList<>());
        surronding_box.getChilds().add(current_box);


        // adjust extended left and right
        if (extend_to_neighbors){
            int line_before = interval.getLine() - 1;
            int line_after = interval.getLine() + 1;

            // find boxes that have more than 40% overlap and sort by distance
            float min_gap_right = 10000;
            float min_gap_left = 10000;
            for (int i = line_before; i <= line_after; i++){
                TextBox line_boxes = lineTextBoxMap.get(i);
                if (line_boxes == null || line_boxes.getChilds().size() == 0) continue;

                for (BaseTextBox bt : line_boxes.getChilds()) {
                    float verticalOverlap = getVerticalOverlap(bt, surronding_box);
                    if (verticalOverlap > .4) {
                        float bt_right = bt.getRight();
                        float bt_left = bt.getLeft();
                        float gap_right = bt_left - surronding_box.getRight();
                        if (gap_right > .5 && gap_right < min_gap_right){
                            min_gap_right = gap_right;
                        }
                        float gap_left = surronding_box.getLeft() - bt_right;
                        if (gap_left > .5 && gap_left < min_gap_left){
                            min_gap_left = gap_left;
                        }
                    }
                }
            }

            float surronding_left = min_gap_left < 10000f ? surronding_box.getLeft() - min_gap_left : 0;
            float surronding_right = min_gap_right < 10000f ? surronding_box.getRight() + min_gap_right : 10000;

            surronding_box.setLeft(surronding_left);
            surronding_box.setRight(surronding_right);
        }
        return surronding_box;
    }

    private Map<Integer, TextBox> getLineTextBoxMap(List<BaseTextBox> rawTextBoxes){
        if (rawTextBoxes == null || rawTextBoxes.size() == 0) return new HashMap<>();
        List<TextBox> textBoxes = TextBox.process(rawTextBoxes);
        Map<Integer, TextBox> lineTextBoxes = new HashMap<>();
        if (textBoxes == null) return lineTextBoxes;
        for (TextBox tb : textBoxes){
            int l = tb.getLine();
            lineTextBoxes.put(l, tb);
        }
        return lineTextBoxes;
    }

    @Override
    public List<ExtInterval> extract(final String content,
                                     List<DictSearch> extractDictionaries,
                                     List<BaseTextBox> textBoxes,
                                     boolean searchVertical) {
        long start = System.currentTimeMillis();
        Map<String, Collection<ExtInterval>> labels = findLabels(extractDictionaries, content, 0);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        ArrayList<ExtInterval> foundValues = new ArrayList<>();
        Set<String> patterns_needed = new HashSet<>();

        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();
        for (DictSearch qtSearchable : extractDictionaries) {
            String dicId = qtSearchable.getDictionary().getId();
            Pattern ptr = qtSearchable.getDictionary().getPattern();
            String ptr_str = ptr == null ? "" : ptr.pattern();
            if (! ptr_str.isEmpty()) {
                if (ptr_str.startsWith(AUTO)){
                    patterns_needed.add(ptr_str);
                }
                valueNeededDictionaryMap.put(dicId, qtSearchable);
            } else {
                Collection<ExtInterval> labelExtIntervalList = labels.get(dicId);
                if (labelExtIntervalList == null || labelExtIntervalList.isEmpty()) continue;
                // These labels don't need a value,
                for (ExtInterval labelExtInterval : labelExtIntervalList){
                    int lableStart = labelExtInterval.getStart();
                    LineInfo lineInfo = getLineInfo(content, lableStart);
                    labelExtInterval.setStart(lineInfo.localStart);
                    labelExtInterval.setEnd(lineInfo.localStart + labelExtInterval.getEnd() - lableStart);
                    labelExtInterval.setLine(lineInfo.lineNumber);
                    foundValues.add(labelExtInterval);
                }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) {
            return foundValues;
        }

        Map<Integer, TextBox> lineTextBoxMap = null;
        if (textBoxes != null){
            textBoxes.sort((p1, p2) -> Float.compare(p1.getBase(), p2.getBase()));
            lineTextBoxMap = getLineTextBoxMap(textBoxes);
        }
        //Searching for values that are associated with a label

        Map<String, TreeMap<Integer, List<ExtIntervalSimpleMatcher>>> content_custom_matches = new HashMap<>();

        for (String p : patterns_needed){
            String ptr_string = p.replaceAll(AUTO, "");
            if (ptr_string.isEmpty()){
                for (Pattern aut_ptr : AUTO_Patterns){
                    TreeMap<Integer, List<ExtIntervalSimpleMatcher>> candidateValues = findPatterns(content, aut_ptr, lineTextBoxMap);
                    content_custom_matches.put(aut_ptr.pattern(), candidateValues);
                }
            } else {
                Pattern pattern = Pattern.compile(ptr_string);
                TreeMap<Integer, List<ExtIntervalSimpleMatcher>> candidateValues = findPatterns(content, pattern, lineTextBoxMap);
                content_custom_matches.put(p, candidateValues);
            }
        }

        String [] content_lines = content.split("\n");
        for (Map.Entry<String, Collection<ExtInterval>> labelEntry : labels.entrySet()){
            String dictId = labelEntry.getKey();

            long start_dictionary_match = System.currentTimeMillis();
            //get the dictionary

            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            Collection<ExtInterval> dictLabelList = labelEntry.getValue();

            String ptr = dictSearch.getDictionary().getPattern().pattern();
            boolean isAuto = ptr.equals(AUTO);
            TreeMap<Integer, List<ExtIntervalSimpleMatcher>> candidateValues = isAuto ? null : content_custom_matches.get(ptr);

            for (ExtInterval labelInterval : dictLabelList) {

                if (isAuto || (candidateValues != null && candidateValues.size() > 0)) {
                    int lableStart = labelInterval.getStart();
                    LineInfo lineInfo = getLineInfo(content, lableStart);
                    labelInterval.setStart(lineInfo.localStart);
                    labelInterval.setEnd(lineInfo.localStart + labelInterval.getEnd() - lableStart);
                    labelInterval.setLine(lineInfo.lineNumber);

                    TextBox tb = findAssociatedTextBox(lineTextBoxMap, labelInterval, true);
                    if (tb == null){
                        logger.warn("Didn't find tb got {}", labelInterval.getStr());
                        continue;
                    }
                    tb.setLine(labelInterval.getLine());
                    if (isAuto){
                        for (Pattern aut_ptr : AUTO_Patterns){
                            String ptr_str = aut_ptr.pattern();
                            TreeMap<Integer, List<ExtIntervalSimpleMatcher>> c_vals = content_custom_matches.get(ptr_str);
                            if (c_vals.size() > 0) {
                                findBestValue(content_lines, labelInterval, tb, c_vals);
                                if (labelInterval.getExtIntervalSimples() != null) break;
                            }
                        }
                    } else {
                        findBestValue(content_lines, labelInterval, tb, candidateValues);
                    }
                } else {
                    List<Interval> rowValues = findAllHorizentalMatches(content, dictSearch, labelInterval);
                    if (searchVertical && rowValues.size() == 0) {
                        rowValues = findAllVerticalMatches(content, lineTextBoxMap, dictSearch, labelInterval);
                    }
                    setFieldValues(content, labelInterval, rowValues);
                }

                if (labelInterval.getExtIntervalSimples() != null) {
                    foundValues.add(labelInterval);
                }
            }

            long took_dictionary_match = System.currentTimeMillis() - start_dictionary_match;
            if (took_dictionary_match > 1000){
                logger.warn("Matching on [{} - {} - {} - {}] took {}ms", dictSearch.getDictionary().getName(), dictSearch.getDictionary().getValType()
                        , dictSearch.getDictionary().getSkip_between_key_and_value(), dictSearch.getDictionary().getSkip_between_values(), took );
            }
        }

        return foundValues;
    }

    private void setFieldValues(String content,
                                ExtInterval labelInterval,
                                List<Interval> values){
        if (values.size() == 0) return;

        for (Interval eis : values){
            int gstart = eis.getStart();
            LineInfo extIntervalLineInfo = getLineInfo(content, gstart);
            eis.setEnd(extIntervalLineInfo.localStart + eis.getEnd() - eis.getStart());
            eis.setStart(extIntervalLineInfo.localStart);
            eis.setLine(extIntervalLineInfo.lineNumber);
        }

        labelInterval.setExtIntervalSimples(values);
        setLocalPosition(content, labelInterval);

    }

    private boolean setLocalPosition(String content,
                                     ExtInterval labelInterval)
    {
        boolean isLastTokenInLine = false;
        int lableStart = labelInterval.getStart();
        LineInfo lineInfo = getLineInfo(content, lableStart);
        int e = content.indexOf("\n", labelInterval.getEnd());
        /// e == -1 : This is the last line of the content
        if (e >= labelInterval.getEnd() || e == -1) {
            if (e == labelInterval.getEnd()){
                isLastTokenInLine = true;
            } else {
                int end_of_line = e == -1 ?content.length() : e;
                String p = content.substring(labelInterval.getEnd(), end_of_line);
                if (p.replaceAll("[^\\w]+", "").trim().length() == 0){
                    isLastTokenInLine = true;
                }
            }
        }
        labelInterval.setStart(lineInfo.localStart);
        labelInterval.setEnd(lineInfo.localStart + labelInterval.getEnd() - lableStart);
        labelInterval.setLine(lineInfo.lineNumber);
        return isLastTokenInLine;
    }

    private static class ExtIntervalSimpleMatcher {
        public Interval extIntervalSimple;
        public TextBox textBox;
        public int start;
        public int end;
        public ExtIntervalSimpleMatcher(Interval e, TextBox tb, int s, int en){
            this.extIntervalSimple = e;
            this.start = s;
            this.end = en;
            this.textBox = tb;
        }

        public int getStart() {
            return start;
        }

        public void setStart(int start) {
            this.start = start;
        }
    }

    private TreeMap<Integer, List<ExtIntervalSimpleMatcher>> findPatterns(String content,
                                                                          Pattern regex,
                                                                          Map<Integer, TextBox> lineTextBoxMap) {

        TreeMap<Integer, List<ExtIntervalSimpleMatcher>> values = new TreeMap<>();
        if (lineTextBoxMap.size() == 0) return values;
        Matcher matcher = regex.matcher(content);
        int group = matcher.groupCount() >= 1 ? 1 : 0;
        while (matcher.find()){
            int s = matcher.start(group);
            int e = matcher.end(group);
            if (s <0 || e <0) continue;
            String str = matcher.group(group);

            Interval extInterval = new Interval();
            LineInfo lineInfo = getLineInfo(content, s);
            int offset = s - lineInfo.localStart;
            extInterval.setStart(lineInfo.localStart);
            extInterval.setEnd(e - offset);
            extInterval.setLine(lineInfo.lineNumber);
            extInterval.setStr(str);
            TextBox textBox = findAssociatedTextBox(lineTextBoxMap, extInterval, false);
            if (textBox == null) {
                logger.warn("{} wasn't matched to any textbox", str);
                continue;
            }
            textBox.setLine(lineInfo.lineNumber);
            List<ExtIntervalSimpleMatcher> list = values.get(lineInfo.lineNumber);
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.lineNumber, list);
            }
            list.add(new ExtIntervalSimpleMatcher(extInterval, textBox, matcher.start(group) - offset ,
                    matcher.end(group) - offset));
        }

        for (Map.Entry<Integer, List<ExtIntervalSimpleMatcher>> e : values.entrySet()) {
            List<ExtIntervalSimpleMatcher> list = e.getValue();
            Collections.sort(list, (o1, o2) -> {
                Integer start1 = o1.getStart();
                Integer start2 = o2.getStart();
                return start1.compareTo(start2);
            });
        }
        return values;
    }

    private void findBestValue(String [] content_lines,
                               ExtInterval labelInterval,
                               TextBox header,
                               TreeMap<Integer, List<ExtIntervalSimpleMatcher>> candidateValues)
    {
        // check for simple form values
        float h_score = 0f;
        float v_score = 0f;
        Interval hValue = null;
        List<Interval> vValue = new ArrayList<>();

        //search left to right
        int keyLine = labelInterval.getLine();
        List<ExtIntervalSimpleMatcher> lineValues = candidateValues.get(keyLine);
        if (lineValues != null) {
            for (ExtIntervalSimpleMatcher em : lineValues) {
                TextBox candidate_vtb = em.textBox;
                if (candidate_vtb == null) {
                    logger.warn("NO textbox {}", em.extIntervalSimple.getStr());
                    continue;
                }
                BaseTextBox vtb = candidate_vtb.getChilds().get(0);
                float hOverlap = getVerticalOverlap(header, vtb);
                if (hOverlap > .3) {
                    float d = vtb.getLeft() - header.getRight();
                    if (d < 0) continue;
                    float tb_w = header.getRight() - header.getLeft();
                    if (d > tb_w) continue;
                    // check gap

                    if (content_lines[keyLine].length() > em.start && em.start > labelInterval.getEnd()) {
                        String gap = content_lines[keyLine].substring(labelInterval.getEnd(), em.start);
                        List<String> tokens = tokenize(gap);
                        if (tokens.size() != 0) continue;
                        h_score = (vtb.getLeft() - header.getRight());
                        hValue = em.extIntervalSimple;
                        break;
                    }
                }
            }
        }
        // searching top to bottom - find the extend of the header

        int lastLine = candidateValues.lastEntry().getKey();
        BaseTextBox lastMatchedTextBox = header;
        float header_h = header.getBase() - header.getTop();
        float verticalOverLapRatio = .6f;
        int last_matched_vertical_cell = labelInterval.getLine()+1;

        int max_key_value_distance_in_lines =  15;

        for (keyLine = labelInterval.getLine()+1 ; keyLine <= lastLine; keyLine++) {
            lineValues = candidateValues.get(keyLine);
            if (lineValues == null) continue;
            if ((keyLine - last_matched_vertical_cell) > max_key_value_distance_in_lines) break;

            List<ExtIntervalSimpleMatcher> line_items = new ArrayList<>();
            for (int i=0; i< lineValues.size(); i++) {
                ExtIntervalSimpleMatcher em = lineValues.get(i);
                TextBox candidate_vtb = em.textBox;
                if (candidate_vtb == null) {
                    logger.warn("NO textbox {}", em.extIntervalSimple.getStr());
                    continue;
                }

                BaseTextBox vtb = candidate_vtb.getChilds().get(0);

                float dvtc = vtb.getTop() - lastMatchedTextBox.getBase();
                if (dvtc > max_key_value_distance_in_lines * header_h) break;

                float vtb_width = vtb.getRight() - vtb.getLeft();

                float d_left = Math.abs(vtb.getLeft() - header.getChilds().get(0).getLeft());
                float d_center = .5f * Math.abs(vtb.getRight() - header.getChilds().get(0).getRight() + vtb.getLeft() - header.getChilds().get(0).getLeft());
                float d_right = Math.abs(vtb.getRight() - header.getChilds().get(0).getRight());

                boolean isValidVerticalValue = (d_left < 10 || d_center < 10 || d_right < 10);

                float dhrz = vtb.getLeft() - header.getLeft();
        //        if (dhrz < -10) continue;

                float vOverlap = getHorizentalOverlap(header, vtb);

                if (isValidVerticalValue || vOverlap > verticalOverLapRatio) {

                    if (vValue.size() == 0) {
                        float min_horizental_d = Math.min(Math.min(d_left, d_center), d_right);
                        v_score = (float)Math.sqrt(min_horizental_d*min_horizental_d +  dvtc*dvtc);
                        verticalOverLapRatio = .9f;
                        lastMatchedTextBox = vtb;
                        line_items.add(em);
                    } else {
                        float w1 = lastMatchedTextBox.getRight() - lastMatchedTextBox.getLeft();
                        float lengthRatio = w1 / vtb_width;
                        if (lengthRatio > .5f || lengthRatio < 2f){
                            line_items.add(em);
                        }
                    }
                }
            }
            if (line_items.size() == 0){
                continue;
            } else if (line_items.size() == 1){
                Interval interval = line_items.get(0).extIntervalSimple;
                vValue.add(interval);
                last_matched_vertical_cell = interval.getLine();
            } else {
                //select the one closest to center of header
                ExtIntervalSimpleMatcher bestInterval = line_items.get(0);
                float d = 100000f;
                BaseTextBox origHeader = header.getChilds().get(0);
                float header_center = (origHeader.getRight() + origHeader.getLeft()) / 2;
                for (ExtIntervalSimpleMatcher esm : line_items){
                    float center = (esm.textBox.getRight() + esm.textBox.getLeft()) / 2;
                    float dist = Math.abs(center - header_center);
                    if (dist < d) {
                        bestInterval = esm;
                        d = dist;
                    }

                }
                vValue.add(bestInterval.extIntervalSimple);
                last_matched_vertical_cell = bestInterval.extIntervalSimple.getLine();
            }
        }

        if (v_score > h_score){
            if (vValue.size() > 0){
                labelInterval.setExtIntervalSimples(vValue);
            }
        } else if (hValue != null){
            List<Interval> list = new ArrayList<>();
            list.add(hValue);
            labelInterval.setExtIntervalSimples(list);
        }
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

    private float getHorizentalOverlap(BaseTextBox textBox1,
                                       BaseTextBox textBox2){
        float l1 = textBox1.getLeft();
        float r1 = textBox1.getRight();

        float l2 = textBox2.getLeft();
        float r2 = textBox2.getRight();

        float w1 = r1 - l1;
        float w2 = r2 - l2;


        if (w1 == 0 || w2 == 0) return 0;

        // h1 100% covers h2
        if (l2 >= l1 && r2 <= r1) {
            return 1;
        }

        // h2 100% covers h1
        if (l2 <= l1 && r2 >= r1) {
            return 1;
        }

        float overlap = 0;

        if (l1 <= r2 && l1 >= l2) {
            overlap = (r2 - l1 );
        }

        if (r1 <= r2 && r1 >= l2) {
            overlap = (r1 - l2);
        }
        return overlap / Math.min(w1, w2);
    }

    private float getVerticalOverlap(BaseTextBox textBox1, BaseTextBox textBox2)
    {
        // case 1:
        //     ---     |     ---
        //         --- | ---
        //         --- | ---
        //     ---     |     ---


        //                    2
        //     ---     |     ---
        //         --- |  1  ---
        //         --- | ---
        //     ---     | ---

        float t1 = textBox1.getTop();
        float b1 = textBox1.getBase();

        float t2 = textBox2.getTop();
        float b2 = textBox2.getBase();

        float h1 = b1 - t1;
        float h2 = b2 - t2;
        if (h2 == 0 || h1 == 0) return 0;

        // h1 100% covers h2
        if (t2 >= t1 && b2 <= b1) {
            return 1;
        }

        // h2 100% covers h1
        if (t2 <= t1 && b2 >= b1) {
            return 1;
        }

        // case 2:

        float overlap = 0;
        if (t2 <= t1 && b2 <= b1) {
            overlap = b2 - t1;
        }
        if (t2 >= t1 && b2 >= b1) {
            overlap = b1 - t2;
        }

        //       ---  |   ---
        //   ---      |       ---
        //       ---  |   ---
        //   ---      |       ---

        return overlap / Math.min(h1, h2);
    }

    private List<Interval> findAllVerticalMatches(String content,
                                                  Map<Integer, TextBox> lineTextBoxMap,
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
                        LineInfo lineInfo = getLineInfo(content, start + start_local_interval);
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
                //trip the search string so we get rid of padding spaces
            //    verticalGap.add(string2Search4Value.trim() +"\n");
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

    private static class LineInfo{
        public int lineNumber;
        public int localStart;
        public LineInfo(int lineNumber, int localStart){
            this.lineNumber = lineNumber;
            this.localStart = localStart;
        }
    }

    private LineInfo getLineInfo(String str, int start){
        int lineNumber = 0;
        int mostRecentNewLineIndex = 0;
        int str_length = str.length();

        for (int i = 0; i < start && i < str_length; i++) {
            if (str.charAt(i) == NewLine) {
                mostRecentNewLineIndex = i;
                lineNumber++;
            }
        }

        // return 0-based line number : add 1 to account for the newline character and move
        //the cursor to the next character
        return new LineInfo(lineNumber, start - mostRecentNewLineIndex -1);
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
