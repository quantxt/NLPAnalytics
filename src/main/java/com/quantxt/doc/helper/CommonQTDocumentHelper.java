package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.Dictionary.ExtractionType;
import com.quantxt.model.document.TextBox;
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

    private static String AUTO = "__auto__";
    private static String MULTI = "__multi__";

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

    final private static Pattern GenericToken = Pattern.compile("(?:^ *|[^\\p{Sc}\\p{L}\\d]{2,})((?:[\\p{Sc}À-ÿ\\p{L}\\d]+(?: ?[-_,.\\/\\\\] ?| )){0,4}[\\p{Sc}À-ÿ\\p{L}\\d]+)(?=$|\\n| {2,})");
    final private static String GenericSuffixRegexStr = "[^\\p{Sc}\\p{L}\\d]+$";

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
            ExtInterval extInterval = new ExtInterval();
            extInterval.setDict_name(qtMatch.getDict_name());
            String dic_id = qtMatch.getDict_id();
            extInterval.setDict_id(dic_id);
            extInterval.setCategory(qtMatch.getCategory());
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

    private boolean isWhiteSpace(String str, int s, int e){
        if (s == e) return true;
        if (s > e) return false;
        if (str.substring(s, e).replaceAll(GenericSuffixRegexStr, "").length() == 0) return true;
        return false;
    }

    private TextBox findAssociatedTextBox(TextBox line_text_boxes,
                                          Interval interval)
    {
        if (line_text_boxes == null) return null;

        String str = interval.getStr();
        String line_str = line_text_boxes.getLine_str();

        int str_start_1 = line_str.indexOf(str);
        if (str_start_1 < 0)  {
            logger.warn("{} was not found in line_str {}", str, line_str);
            return null;
        }
        int str_start_2 = line_str.lastIndexOf(str);
        if (str_start_1 == str_start_2){
            List<TextBox> associated_tbs = new ArrayList<>();
            int index = 0;
            for (TextBox cltb : line_text_boxes.getChilds()) {
                String ctb_str = cltb.getStr();
                String stripped_ctb_str = ctb_str.replaceAll(GenericSuffixRegexStr, "");
                int f = str.indexOf(stripped_ctb_str, index);
                if (f >= 0 && isWhiteSpace(str, index, f)){
                    associated_tbs.add(cltb);
                    index = f + ctb_str.length();
                    if (Math.abs(str.length() - index) < 2) {
                        float best_top = associated_tbs.get(0).getTop();
                        float best_bot = associated_tbs.get(0).getBase();
                        float best_left = associated_tbs.get(0).getLeft();
                        float best_right = associated_tbs.get(associated_tbs.size()-1).getRight();

                        TextBox surronding_bos = new TextBox(best_top, best_bot, best_left, best_right, str);
                        return surronding_bos;
                    }
                } else {
                    // check if it is partial match
                    associated_tbs = new ArrayList<>();
                    index = 0;
                    f = str.indexOf(stripped_ctb_str, index);
                    if (f >= 0 && isWhiteSpace(str, index, f)){
                        associated_tbs.add(cltb);
                        index = f + ctb_str.length();
                    }
                }
            }
        }
        // case 1: one word str and unique in line_str
        StringBuilder sb = new StringBuilder();

        //estimate spacing
        float start_first_box = line_text_boxes.getChilds().get(0).getLeft();
        float start_last_box = line_text_boxes.getChilds().get(line_text_boxes.getChilds().size()-1).getLeft();

        int index_first_box = line_text_boxes.getLine_str().indexOf(line_text_boxes.getChilds().get(0).getStr());
        int index_last_box = line_text_boxes.getLine_str().lastIndexOf(line_text_boxes.getChilds().get(line_text_boxes.getChilds().size()-1).getStr());

        float space_estimate = .5f * (start_first_box/index_first_box + start_last_box/index_last_box);

        float interval_box_start = interval.getStart() * space_estimate;
        float interval_box_end = interval.getEnd() * space_estimate;

        TextBox intervalSpan = new TextBox();
        intervalSpan.setLeft(interval_box_start);
        intervalSpan.setRight(interval_box_end);


        TreeMap<Float, Integer> bestStartBox = new TreeMap<>();
        List<TextBox> associated_tbs = new ArrayList<>();
        for (int i=0; i<line_text_boxes.getChilds().size(); i++ ) {
            TextBox cltb = line_text_boxes.getChilds().get(i);
            String cltb_str = cltb.getStr();
            String stripped_ctb_str = cltb_str.replaceAll(GenericSuffixRegexStr, "");
            if (str.indexOf(stripped_ctb_str) < 0) continue;
            float d = Math.abs(cltb.getLeft() - interval_box_start);
            bestStartBox.put(d, i);
        }
        if (bestStartBox.size() == 0) return null;
        int best_index = bestStartBox.entrySet().iterator().next().getValue();
        for (int i=best_index; i<line_text_boxes.getChilds().size(); i++ ) {
            TextBox cltb = line_text_boxes.getChilds().get(i);
            String cltb_str = cltb.getStr();
            String stripped_ctb_str = cltb_str.replaceAll(GenericSuffixRegexStr, "");
            if (str.indexOf(stripped_ctb_str) >= 0) {
                associated_tbs.add(cltb);
                sb.append(cltb_str);
                if (sb.toString().contains(str)) break;
            }
        }

        if (associated_tbs.size() == 0) {
            logger.warn("'{}' did not match to a textbox", interval.getStr());
            return null;
        }

        float best_top = associated_tbs.get(0).getTop();
        float best_bot = associated_tbs.get(0).getBase();
        float best_left = associated_tbs.get(0).getLeft();
        float best_right = associated_tbs.get(associated_tbs.size()-1).getRight();

        TextBox surronding_bos = new TextBox(best_top, best_bot, best_left, best_right, str);
        return surronding_bos;
    }


    private Map<Integer, TextBox> getLineTextBoxMap(List<TextBox> textBoxes){
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
                                     List<TextBox> textBoxes,
                                     boolean searchVertical) {
        long start = System.currentTimeMillis();
        Map<String, Collection<ExtInterval>> labels = findLabels(extractDictionaries, content, 0);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        boolean autoDetect = false;
        ArrayList<ExtInterval> foundValues = new ArrayList<>();
        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();
        for (DictSearch qtSearchable : extractDictionaries) {
            String dicId = qtSearchable.getDictionary().getId();
            Pattern ptr = qtSearchable.getDictionary().getPattern();
            String ptr_str = ptr == null ? "" : ptr.pattern();
            if (! ptr_str.isEmpty()) {
                if ( ptr_str.toLowerCase().equals(AUTO) ) {
                    autoDetect = true;
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

        //Searching for values that are associated with a label
        Map<Integer, List<ExtIntervalSimpleMatcher>> genericValues = new HashMap<>();
        Map<Integer, TextBox> lineTextBoxMap = getLineTextBoxMap(textBoxes);

        if (autoDetect) {
            getGenericValues(content, GenericToken, genericValues, lineTextBoxMap);
        }

        String [] content_lines = content.split("\n");
        for (Map.Entry<String, Collection<ExtInterval>> labelEntry : labels.entrySet()){
            String dictId = labelEntry.getKey();

            long start_dictionary_match = System.currentTimeMillis();
            //get the dictionary

            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            Collection<ExtInterval> dictLabelList = labelEntry.getValue();

            for (ExtInterval labelInterval : dictLabelList) {

                if (dictSearch.getDictionary().getPattern().pattern().toLowerCase().equals(AUTO)) {
                    String category = labelInterval.getCategory();
                    if (category.equals(DONT_CARE)) continue;

                    int lableStart = labelInterval.getStart();
                    LineInfo lineInfo = getLineInfo(content, lableStart);
                    labelInterval.setStart(lineInfo.localStart);
                    labelInterval.setEnd(lineInfo.localStart + labelInterval.getEnd() - lableStart);
                    labelInterval.setLine(lineInfo.lineNumber);

                    TextBox tb = findAssociatedTextBox(lineTextBoxMap.get(labelInterval.getLine()), labelInterval);
                    if (tb == null){
                        logger.warn("Didn't find tb got {}", labelInterval.getStr());
                        continue;
                    }

                    findBestValue(content_lines, labelInterval, tb, genericValues);
                } else {
                    ArrayList<ExtIntervalSimple> rowValues = findAllHorizentalMatches(content, dictSearch, labelInterval);
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
                                ArrayList<ExtIntervalSimple> values){
        if (values.size() == 0) return;

        for (ExtIntervalSimple eis : values){
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
        public ExtIntervalSimple extIntervalSimple;
        public TextBox textBox;
        public int start;
        public int end;
        public ExtIntervalSimpleMatcher(ExtIntervalSimple e, TextBox tb, int s, int en){
            this.extIntervalSimple = e;
            this.start = s;
            this.end = en;
            this.textBox = tb;
        }
    }

    private void getGenericValues(String content,
                                  Pattern regex,
                                  Map<Integer, List<ExtIntervalSimpleMatcher>> values,
                                  Map<Integer, TextBox> lineTextBoxMap) {
        Matcher genericValueMatcher = regex.matcher(content);
        while (genericValueMatcher.find()){
            int s = genericValueMatcher.start(1);
            int e = genericValueMatcher.end(1);
            if (s <0 || e <0) continue;
            String str = genericValueMatcher.group(1);

            ExtIntervalSimple extInterval = new ExtIntervalSimple();
            LineInfo lineInfo = getLineInfo(content, s);
            int offset = s - lineInfo.localStart;
            extInterval.setStart(lineInfo.localStart);
            extInterval.setEnd(e - offset);
            extInterval.setLine(lineInfo.lineNumber);
            extInterval.setStr(str);
            TextBox textBox = findAssociatedTextBox(lineTextBoxMap.get(extInterval.getLine()), extInterval);
            if (textBox == null) {
                logger.warn("{} wasn't matched to any textbox", str);
                continue;
            }
            List<ExtIntervalSimpleMatcher> list = values.get(lineInfo.lineNumber);
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.lineNumber, list);
            }
            list.add(new ExtIntervalSimpleMatcher(extInterval, textBox, genericValueMatcher.start() - offset ,
                    genericValueMatcher.end() - offset));
        }
    }

    private void findBestValue(String [] content_lines,
                               ExtInterval labelInterval,
                               TextBox tb,
                               Map<Integer, List<ExtIntervalSimpleMatcher>> genericValues)
    {
        // check for simple form values
        Map<Float, ExtIntervalSimpleMatcher> first_prioritie = new TreeMap<>();
        Map<Float, ExtIntervalSimpleMatcher> second_prioritie = new TreeMap<>();

        for (int keyLine = labelInterval.getLine() - 1; keyLine < labelInterval.getLine() + 2; keyLine++) {
            List<ExtIntervalSimpleMatcher> lineValues = genericValues.get(keyLine);
            if (lineValues == null) continue;
            TreeMap<Float, ExtIntervalSimpleMatcher> allSameLineValues = new TreeMap<>();
            for (ExtIntervalSimpleMatcher em : lineValues) {
                TextBox vtb = em.textBox;
                if (vtb == null) {
                    logger.warn("NO textbox {}", em.extIntervalSimple.getStr());
                    continue;
                }
                float hOverlap = getVerticalOverlap(tb, vtb);
                if (hOverlap > .3) {
                    float d = vtb.getLeft() - tb.getRight();
                    if (d < 0) continue;
                    float tb_w = tb.getRight() - tb.getLeft();
                    if (d > tb_w) continue;
                    float score = (vtb.getLeft() - tb.getRight());
                    first_prioritie.put(score, em);
                    // check gap

                    if (keyLine == labelInterval.getLine()){
                        if (content_lines[keyLine].length() > em.start && em.start > labelInterval.getEnd()) {
                            String gap = content_lines[keyLine].substring(labelInterval.getEnd(), em.start);
                            List<String> tokens = tokenize(gap);
                            if (tokens.size() != 0) continue;
                        }
                    }
                    allSameLineValues.put(d, em);
                }
            }
        }

        for (int keyLine = labelInterval.getLine()+1 ; keyLine < labelInterval.getLine() + 6; keyLine++) {
            List<ExtIntervalSimpleMatcher> lineValues = genericValues.get(keyLine);
            if (lineValues == null) continue;
            TreeMap<Float, ExtIntervalSimpleMatcher> allSameLineValues = new TreeMap<>();
            for (ExtIntervalSimpleMatcher em : lineValues) {
                TextBox vtb = em.textBox;
                if (vtb == null) {
                    logger.warn("NO textbox {}", em.extIntervalSimple.getStr());
                    continue;
                }
                float dhrz = vtb.getLeft() - tb.getLeft();
                if (dhrz < -10) continue;
                float vOverlap = getHorizentalOverlap(tb, vtb);
                if (vOverlap > .6) {
                    float dvtc = vtb.getTop() - tb.getBase();
                    if (dvtc < 0) continue;
                    allSameLineValues.put(dvtc, em);
                    float score = (float)Math.sqrt(dhrz*dhrz +  dvtc*dvtc);
                    first_prioritie.put(score, em);
                }
            }
        }

        if (first_prioritie.size() > 0){
            ArrayList<ExtIntervalSimple> list = new ArrayList<>();
            ExtIntervalSimpleMatcher em = first_prioritie.entrySet().iterator().next().getValue();
            list.add(em.extIntervalSimple);
            labelInterval.setExtIntervalSimples(list);
        }
    }

    private boolean addShiftedValues(String content,
                                     int start_search_shift,
                                     Pattern match_pattern,
                                     int group,
                                     Analyzer analyzer,
                                     Pattern gap_pattern,
                                     List<ExtIntervalSimple> matches)
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
        ExtIntervalSimple ext = new ExtIntervalSimple(start_match + start_search_shift, end_match + start_search_shift);
        ext.setType(QTField.DataType.STRING);
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

    private ArrayList<ExtIntervalSimple> findAllHorizentalMatches(String content,
                                                                  DictSearch dictSearch,
                                                                  Interval labelInterval)
    {
        ArrayList<ExtIntervalSimple> results = new ArrayList<>();
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

    private float getHorizentalOverlap(TextBox textBox1,
                                       TextBox textBox2){
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

    private float getVerticalOverlap(TextBox textBox1, TextBox textBox2)
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

    private ArrayList<ExtIntervalSimple> findAllVerticalMatches(String content,
                                                                Map<Integer, TextBox> lineTextBoxMap,
                                                                DictSearch dictSearch,
                                                                Interval labelInterval)
    {
        ArrayList<ExtIntervalSimple> results = new ArrayList<>();
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

            ExtIntervalSimple foundValue = null;

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
                        foundValue = new ExtIntervalSimple(start + start_local_interval,  end + end_local_interval);
                        foundValue.setType(QTField.DataType.KEYWORD);
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
                    foundValue = new ExtIntervalSimple(start + start_local_interval, end + start_local_interval);
                    foundValue.setType(QTField.DataType.KEYWORD);
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
            for (ExtIntervalSimple extvStr : ext.getExtIntervalSimples()) {
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
