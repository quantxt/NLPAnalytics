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
    private static String PARTIAL = "__PARTIAL__";

    private static int max_string_length_for_search = 30000;
    private static Character NewLine = '\n';
    private static Pattern WORD_PTR = Pattern.compile("\\S+");
    private static Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static Pattern SPC_BET_WORDS_PTR = Pattern.compile("\\S(?= \\S)");
    private static Pattern LONG_SPACE = Pattern.compile(" {5,}");
    private static Pattern START_SPACE = Pattern.compile("^ *");

    // bullets
    protected static String UTF8_BULLETS = "\\u2022|\\u2023|\\u25E6|\\u2043|\\u2219";
    protected static Pattern UTF8_TOKEN = Pattern.compile("^(?:[a-zA-Z]\\.){2,}|([\\p{L}\\p{N}]+[\\.\\&]{0,1}[\\p{L}\\p{N}])");

    final private static Pattern simple_form_val   = Pattern.compile("^[^\\p{L}\\p{N}:]*: {0,20}((?:[\\p{L}\\p{N}]\\S* )*\\S+)");

    final private static String end_pad   = "(?=$|\\s)";
    final private static String alpha_num =  "[\\p{L}\\p{N}]+";
    final private static String begin_pad = "(?:^|[:\\s])";

    final private static Pattern GenericDate1  = Pattern.compile(begin_pad + "((?:[1-9]|[01]\\d)[ -\\/](?:[1-9]|[0123]\\d)[ -\\/](?:19\\d{2}|20\\d{2}|\\d{2}))" + end_pad);  // mm dd yyyy
    final private static Pattern GenericDate2  = Pattern.compile(begin_pad + "/([12]\\d{3}[ -\\/](?:0[1-9]|1[0-2])[ -\\/](?:0[1-9]|[12]\\d|3[01]))/" + end_pad);  // YYYY-mm-dd
    final private static Pattern Numbers       = Pattern.compile(begin_pad + "((?:\\p{Sc} {0,6})?[+\\-]{0,1}[0-9]{1,3}(?:[\\.,]?[0-9]{3})*(?:[,\\.][0-9]{2})?%?)" + end_pad);  // mm dd yyyy
    final private static Pattern Digits =  Pattern.compile("\\p{N}");

    final private static Pattern AlphaNumerics = Pattern.compile(begin_pad + "((?:"+ alpha_num+"[ \\-_\\/\\)\\(\\.]){0,3}" + alpha_num + ")"  + end_pad);
    final private static Pattern [] AUTO_Patterns = new Pattern[] {GenericDate1, GenericDate2, Numbers, AlphaNumerics};

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

    private void  findLabelsHelper(DictSearch dictSearch,
                                   String content,
                                   int shift,
                                   int slop,
                                   Map<Integer, ExtIntervalTextBox> dicLabels){
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
            ExtIntervalTextBox extIntervalTextBox = new ExtIntervalTextBox(extInterval, null);
            dicLabels.put(start , extIntervalTextBox);
        }
    }

    private Map<String, Collection<ExtIntervalTextBox>> findLabels(List<DictSearch> extractDictionaries,
                                                                   String content,
                                                                   int slop) {
        int content_length = content.length();
        Map<String, Collection<ExtIntervalTextBox>> labels = new LinkedHashMap<>();

        for (DictSearch dictSearch : extractDictionaries) {
            String dict_id = dictSearch.getDictionary().getId();
            Map<Integer, ExtIntervalTextBox> dicLabels = new TreeMap<>();

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

    private TextBox findAssociatedTextBox(Map<Integer, TextBox> lineTextBoxMap,
                                          Interval interval,
                                          boolean extend_to_neighbors,
                                          boolean ignorePrefixBoxes)
    {

        int current_line = interval.getLine();

        TextBox line_text_boxes = lineTextBoxMap.get(current_line);
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
        List<BaseTextBox> components = new ArrayList<>();
        int first_box_idx = -1;
        int last_box_idx = 10000;
        for (int i = 0; i < childIdx2StrIdx.length; i++) {
            int current_start = childIdx2StrIdx[i][0];
            int current_end = childIdx2StrIdx[i][1];
            current_box = childs.get(i);
            boolean added = false;
            if (start_str >= current_start && start_str <= current_end) {
                surronding_box.setTop(current_box.getTop());
                surronding_box.setBase(current_box.getBase());
                surronding_box.setLeft(current_box.getLeft());
                components.add(current_box);
                added = true;
                if (first_box_idx == -1) {
                    first_box_idx = i;
                }
            }

            if (end_str >= current_start && end_str <= current_end) {
                surronding_box.setRight(current_box.getRight());
                if (!added) {
                    components.add(current_box);
                }
                last_box_idx = i;
                break;
            }
        }

        if (first_box_idx == -1) {
            logger.debug("Didn't find the associating texboxes {}", str);
            return null;
        }

        if (ignorePrefixBoxes) {

            // check if first box has token that is the beginining of the str
            String first_matched_box_str = childs.get(first_box_idx).getStr();
            if (!str.startsWith(first_matched_box_str)) {
                if (!first_matched_box_str.startsWith(str)) {
                    logger.debug("{} is a prefix", str);
                    return null;
                }
            }
            BaseTextBox textbox_before = first_box_idx > 0 ? childs.get(first_box_idx - 1) : null;

            if (textbox_before != null) {
                float gap_left = surronding_box.getLeft() - textbox_before.getRight();
                float w2 = (textbox_before.getRight() - textbox_before.getLeft()) / (textbox_before.getStr().length());
                if (gap_left < w2 * .8 ) {
                    String textbox_before_str = textbox_before.getStr();
                    if (textbox_before_str.length() != 0) {
                        String lastChar = textbox_before_str.substring(textbox_before_str.length() - 1);
                        if (lastChar.replaceAll("[A-Za-z0-9\"']", "").length() == 0) {
                            logger.debug("{} is a left-prefix", str);
                            return null;
                        }
                    }
                }
            }

            // This is English so if the phrase ends with colon (:) no need to look the right side
            BaseTextBox textbox_after = last_box_idx < childs.size()-1 ? childs.get(last_box_idx + 1) : null;
            BaseTextBox textbox_curr = childs.get(last_box_idx);
            if (textbox_after != null && !str.endsWith(":") && !textbox_curr.getStr().endsWith(":")) {
                float gap_right =  textbox_after.getLeft() - surronding_box.getRight();
                float w2 = (textbox_after.getRight() - textbox_after.getLeft()) / (textbox_after.getStr().length());
                if (gap_right < w2 * .8 ) {
                    String textbox_after_str = textbox_after.getStr();
                    if (textbox_after_str.length() > 0) {
                        String firstChar = textbox_after_str.substring(0, 1);
                        if (firstChar.replaceAll("[\\p{L}]", "").length() == 0) {
                            logger.warn("{} is a right-prefix", str);
                            return null;
                        }
                    }
                }
            }

        }

        surronding_box.setChilds(components);
        surronding_box.setLine_str(line_str);

        // adjust extended left and right
        if (extend_to_neighbors){
            int line_before = interval.getLine() - 1;
            int line_after = interval.getLine() + 1;

            // find all boxes that have overlap
            List<BaseTextBox> overlappingBoxes = new ArrayList<>();
            overlappingBoxes.addAll(components);
            for (int i = line_before; i <= line_after; i++) {
                TextBox line_boxes = lineTextBoxMap.get(i);
                if (line_boxes == null || line_boxes.getChilds().size() == 0) continue;
                for (BaseTextBox bt : line_boxes.getChilds()) {
                    TextBox temp_tb = new TextBox();
                    temp_tb.setLeft(bt.getLeft());
                    temp_tb.setRight(bt.getRight());
                    float horizentalOverlap = getHorizentalOverlap(temp_tb, surronding_box, false);
                    if (horizentalOverlap > 0 ) continue;
                    float verticalOverlap = getVerticalOverlap(bt, surronding_box);
                    if (verticalOverlap > -.1) {
                        String bt_str = bt.getStr();
                        if (tokenize(bt_str).size() == 0) continue;
                        overlappingBoxes.add(bt);
                    }
                }
            }

            overlappingBoxes.sort((o1, o2) -> Float.compare(o1.getLeft(), o2.getLeft()));
            //now we extend left and right of surronding_box

            int num_overlapping_boxes = overlappingBoxes.size();
            for (int i = 0; i < num_overlapping_boxes; i++) {
                BaseTextBox bt = overlappingBoxes.get(i);
                if (bt.getLeft() == surronding_box.getLeft()) { // this is our left most  box
                    surronding_box.setLeft(i==0 ? 0 : overlappingBoxes.get(i - 1).getRight());
                }
                if (bt.getRight() == surronding_box.getRight()) { // this is our right most  box
                    surronding_box.setRight(i==num_overlapping_boxes-1 ? 10000 : overlappingBoxes.get(i+1).getLeft());
                }
            }
        }
        return surronding_box;
    }

    private Map<Integer, TextBox> getLineTextBoxMap(List<BaseTextBox> rawTextBoxes){
        if (rawTextBoxes == null || rawTextBoxes.size() == 0) return new HashMap<>();
        List<TextBox> textBoxes = TextBox.process(rawTextBoxes);
        Map<Integer, TextBox> lineTextBoxes = new TreeMap<>();
        if (textBoxes == null) return lineTextBoxes;
        for (TextBox tb : textBoxes){
            int l = tb.getLine();
            lineTextBoxes.put(l, tb);
        }
        return lineTextBoxes;
    }

    private void combineStackedTextBoxes(Collection<ExtIntervalTextBox> extIntervalTextBoxes){
        for (ExtIntervalTextBox e1 : extIntervalTextBoxes){
            if (e1.textBox == null) continue;
            float e1_b = e1.textBox.getBase();
            float e1_t = e1.textBox.getTop();
            float h1 = e1_b - e1_t;
            for (ExtIntervalTextBox e2 : extIntervalTextBoxes){
                if (e2.textBox == null) continue;
                String category_2 = e2.interval.getCategory();
                if (!category_2.equals(PARTIAL)) continue;
                float e2_t = e2.textBox.getTop();
                float e2_b = e2.textBox.getBase();
                float h2 = e2_b - e2_t;
                float r = h1>h2 ? h2/h1 : h1/h2;
                if ( (e1_b + h1*3 ) > e2_t && (e2_t > e1_t) ){
                    float overlap = getHorizentalOverlap(e1.textBox, e2.textBox, true);
                    if (overlap >.7 && r > .7){
                        logger.debug("Combining {} and {}", e1.interval.getStr(), e2.interval.getStr());
                        // find the union - only the extend that is common
                        e1.textBox.setRight(Math.min(e1.textBox.getRight(), e2.textBox.getRight()));
                        e1.textBox.setLeft(Math.max(e1.textBox.getLeft(), e2.textBox.getLeft()));
                        e1.textBox.setBase(e2.textBox.getBase());
                        e1.interval.setStr(e1.interval.getStr() + " " + e2.interval.getStr());
                        e1.interval.setCategory(e1.interval.getDict_name());
                        e2.textBox = null;
                    }
                }
            }
        }
    }
    @Override
    public List<ExtInterval> extract(final String content,
                                     List<DictSearch> extractDictionaries,
                                     List<BaseTextBox> textBoxes,
                                     boolean searchVertical) {
        long start = System.currentTimeMillis();
        Map<String, Collection<ExtIntervalTextBox>> labels = findLabels(extractDictionaries, content, 0);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        ArrayList<ExtInterval> foundValues = new ArrayList<>();
        Set<String> patterns_needed = new HashSet<>();

        Map<Integer, TextBox> lineTextBoxMap = null;
        if (textBoxes != null){
            textBoxes.sort((p1, p2) -> Float.compare(p1.getBase(), p2.getBase()));
            lineTextBoxMap = getLineTextBoxMap(textBoxes);
        }

        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();
        for (DictSearch qtSearchable : extractDictionaries) {
            String dicId = qtSearchable.getDictionary().getId();
            Collection<ExtIntervalTextBox> extIntervalTextBoxes = labels.get(dicId);
            if (extIntervalTextBoxes == null) continue;

            Pattern ptr = qtSearchable.getDictionary().getPattern();
            String ptr_str = ptr == null ? "" : ptr.pattern();
            if (! ptr_str.isEmpty()) {
                if (ptr_str.startsWith(AUTO)){
                    patterns_needed.add(ptr_str);
                    for (ExtIntervalTextBox eitb : extIntervalTextBoxes){
                        LineInfo lineInfo = getLineInfo(content, eitb.interval.getStart());
                        eitb.interval.setEnd(lineInfo.localStart + eitb.interval.getEnd() - eitb.interval.getStart());
                        eitb.interval.setStart(lineInfo.localStart);
                        eitb.interval.setLine(lineInfo.lineNumber);
                        TextBox tb = findAssociatedTextBox(lineTextBoxMap, eitb.interval, true, true);
                        if (tb == null){
                            logger.debug("Didn't find tb got {}", eitb.interval.getStr());
                            continue;
                        }
                        eitb.textBox = tb;
                        tb.setLine(eitb.interval.getLine());
                    }
                    combineStackedTextBoxes(extIntervalTextBoxes);
                }
                valueNeededDictionaryMap.put(dicId, qtSearchable);
            } else {
                // These labels don't need a value,
                for (ExtIntervalTextBox eitb : extIntervalTextBoxes){
                    LineInfo lineInfo = getLineInfo(content, eitb.interval.getStart());
                    eitb.interval.setEnd(lineInfo.localStart + eitb.interval.getEnd() - eitb.interval.getStart());
                    eitb.interval.setStart(lineInfo.localStart);
                    eitb.interval.setLine(lineInfo.lineNumber);
                    foundValues.add(eitb.interval);
                }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) {
            return foundValues;
        }

        //Searching for values that are associated with a label

        Map<String, TreeMap<Integer, List<ExtIntervalTextBox>>> content_custom_matches = new HashMap<>();

        for (String p : patterns_needed){
            String ptr_string = p.replaceAll(AUTO, "");
            if (ptr_string.isEmpty()){
                for (Pattern aut_ptr : AUTO_Patterns){
                    TreeMap<Integer, List<ExtIntervalTextBox>> candidateValues = findPatterns(content, aut_ptr, lineTextBoxMap);
                    if (aut_ptr.equals(AlphaNumerics)){
                        for (Iterator<Map.Entry<Integer, List<ExtIntervalTextBox>>> e = candidateValues.entrySet().iterator(); e.hasNext();){
                            List<ExtIntervalTextBox> list = e.next().getValue();
                            ListIterator<ExtIntervalTextBox> iter = list.listIterator();
                            while (iter.hasNext()){
                                ExtIntervalTextBox etb = iter.next();
                                String str = etb.interval.getStr();
                                Matcher m = Digits.matcher(str);
                                if (!m.find()){
                                    iter.remove();
                                }
                            }
                            if (list.size() ==0){
                                e.remove();
                            }
                        }
                    }
                    groupVerticalValues(candidateValues, lineTextBoxMap);
                    content_custom_matches.put(aut_ptr.pattern(), candidateValues);
                }
            } else {
                Pattern pattern = Pattern.compile(ptr_string);
                TreeMap<Integer, List<ExtIntervalTextBox>> candidateValues = findPatterns(content, pattern, lineTextBoxMap);
                groupVerticalValues(candidateValues, lineTextBoxMap);
                content_custom_matches.put(p, candidateValues);
            }
        }

        // get average distance between lines
        String [] content_lines = content.split("\n");
        for (Map.Entry<String, Collection<ExtIntervalTextBox>> labelEntry : labels.entrySet()){
            String dictId = labelEntry.getKey();

            long start_dictionary_match = System.currentTimeMillis();
            //get the dictionary

            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            Collection<ExtIntervalTextBox> dictLabelList = labelEntry.getValue();

            String ptr = dictSearch.getDictionary().getPattern().pattern();
            boolean isAuto = ptr.equals(AUTO);
            TreeMap<Integer, List<ExtIntervalTextBox>> candidateValues = isAuto ? null : content_custom_matches.get(ptr);
            for (ExtIntervalTextBox extIntervalTextBox : dictLabelList) {
                String category = extIntervalTextBox.interval.getCategory();
                if (category.equals(PARTIAL)) continue;
                if (isAuto || (candidateValues != null && candidateValues.size() > 0)) {
                    if (extIntervalTextBox.textBox == null) continue;

                    String line_str = extIntervalTextBox.textBox.getLine_str();
                    String sub_str = line_str.substring(extIntervalTextBox.interval.getEnd());
                    Matcher m = simple_form_val.matcher(sub_str);
                    Interval singleFormValueInterval = null;
                    if (m.find()) {
                        int offset = extIntervalTextBox.interval.getEnd();
                        singleFormValueInterval = new Interval();
                        singleFormValueInterval.setStr(m.group(1));
                        singleFormValueInterval.setLine(extIntervalTextBox.interval.getLine());
                        singleFormValueInterval.setStart(offset + m.start(1));
                        singleFormValueInterval.setEnd(offset + m.end(1));
                    }

                    AutoValue bestAutoValue = new AutoValue();
                    if (isAuto) {
                        //simple_form_val
                        for (Pattern aut_ptr : AUTO_Patterns) {
                            String ptr_str = aut_ptr.pattern();
                //            if (singleFormValueInterval != null && Pattern.matches(ptr_str, singleFormValueInterval.getStr()) ) {
                //                bestAutoValue = new AutoValue();
                //                bestAutoValue.h_score = 0;
                //                bestAutoValue.hValue = singleFormValueInterval;
                 //               break;
                 //           } else {
                                TreeMap<Integer, List<ExtIntervalTextBox>> c_vals = content_custom_matches.get(ptr_str);
                                if (c_vals.size() > 0) {
                                    AutoValue autoValue = findBestValue(lineTextBoxMap, extIntervalTextBox, c_vals);
                                    bestAutoValue.merge(autoValue);
                                }
                 //           }
                        }
                    } else {
                        if (singleFormValueInterval != null && Pattern.matches(ptr.replace(AUTO, ""), singleFormValueInterval.getStr())){
                            bestAutoValue = new AutoValue();
                            bestAutoValue.h_score = 0;
                            bestAutoValue.hValue = singleFormValueInterval;
                        } else {
                            bestAutoValue = findBestValue(lineTextBoxMap, extIntervalTextBox, candidateValues);
                        }
                    }

                    List<Interval> best = bestAutoValue.getBest();
                    if (best.size() > 0) {
                        extIntervalTextBox.interval.setExtIntervalSimples(best);
                    }

                } else {
                    List<Interval> rowValues = findAllHorizentalMatches(content, dictSearch, extIntervalTextBox.interval);
                    if (searchVertical && rowValues.size() == 0) {
                        rowValues = findAllVerticalMatches(content, lineTextBoxMap, dictSearch, extIntervalTextBox.interval);
                    }
                    setFieldValues(content, extIntervalTextBox.interval, rowValues);
                }

                if (extIntervalTextBox.interval.getExtIntervalSimples() != null) {
                    foundValues.add(extIntervalTextBox.interval);
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

    private static class ExtIntervalTextBox {
        public ExtInterval interval;
        public TextBox textBox;
        List<ExtIntervalTextBox> associates = new ArrayList<>();
        public ExtIntervalTextBox(ExtInterval e, TextBox tb){
            this.interval = e;
            this.textBox = tb;
        }
    }

    private TreeMap<Integer, List<ExtIntervalTextBox>> findPatterns(String content,
                                                                    Pattern regex,
                                                                    Map<Integer, TextBox> lineTextBoxMap) {

        TreeMap<Integer, List<ExtIntervalTextBox>> values = new TreeMap<>();
        if (lineTextBoxMap.size() == 0) return values;
        Matcher matcher = regex.matcher(content);
        int group = matcher.groupCount() >= 1 ? 1 : 0;
        while (matcher.find()){
            int s = matcher.start(group);
            int e = matcher.end(group);
            if (s <0 || e <0) continue;
            String str = matcher.group(group);

            ExtInterval extInterval = new ExtInterval();
            LineInfo lineInfo = getLineInfo(content, s);
            int offset = s - lineInfo.localStart;
            extInterval.setStart(lineInfo.localStart);
            extInterval.setEnd(e - offset);
            extInterval.setLine(lineInfo.lineNumber);
            extInterval.setStr(str);
            TextBox textBox = findAssociatedTextBox(lineTextBoxMap, extInterval, false, false);
            if (textBox == null) {
                logger.warn("{} wasn't matched to any textbox", str);
                continue;
            }
            textBox.setLine(lineInfo.lineNumber);
            List<ExtIntervalTextBox> list = values.get(lineInfo.lineNumber);
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.lineNumber, list);
            }
            list.add(new ExtIntervalTextBox(extInterval, textBox));
        }

        for (Map.Entry<Integer, List<ExtIntervalTextBox>> e : values.entrySet()) {
            List<ExtIntervalTextBox> list = e.getValue();
            Collections.sort(list, (o1, o2) -> {
                Integer start1 = o1.interval.getStart();
                Integer start2 = o2.interval.getStart();
                return start1.compareTo(start2);
            });
        }
        return values;
    }

    private void groupVerticalValues(TreeMap<Integer, List<ExtIntervalTextBox>> values,
                                     Map<Integer, TextBox> lineTextBoxMap){
        if (values.size() == 0) return;

        int last_line = values.lastEntry().getKey();
        Iterator<Map.Entry<Integer, List<ExtIntervalTextBox>>> mapIter = values.entrySet().iterator();
        // find associates
        while (mapIter.hasNext()) {
            Map.Entry<Integer, List<ExtIntervalTextBox>> entry = mapIter.next();
            List<ExtIntervalTextBox> etbList1 = entry.getValue();
            int line1 = entry.getKey();
            ListIterator<ExtIntervalTextBox> iter1 = etbList1.listIterator();
            while (iter1.hasNext()){
                ExtIntervalTextBox etb1 = iter1.next();
                TextBox tb1 = etb1.textBox;
                if (tb1 == null){
                    iter1.remove();
                    continue;
                }
                float l1 = tb1.getLeft();
                float r1 = tb1.getRight();
                float c1 = (r1 + l1 ) / 2;
                float char_width = (r1 - l1) * 0.5f / etb1.interval.getStr().length();
                boolean column_scan_ended = false;
                for (int line2 = line1+1; line2 <= last_line; line2++){
                    if (column_scan_ended) break;
                    List<ExtIntervalTextBox> etbList2 = values.get(line2);
                    if (etbList2 == null) {
                        int numv = etb1.associates.size();
                        // if we find an overlapping value that is not the same pattern as previous one, we exit
            //            if (numv > 0){
                            TextBox lineTextBox = lineTextBoxMap.get(line2);
                            if (lineTextBox != null){
                                boolean tabelColumnEnds = false;
                                for (BaseTextBox btb : lineTextBox.getChilds()){
                                    TextBox fakeTb = new TextBox();
                                    fakeTb.setLeft(btb.getLeft());
                                    fakeTb.setRight(btb.getRight());
                                    float overlap = getHorizentalOverlap(fakeTb, tb1, false);
                                    if (overlap > 0) {
                                        tabelColumnEnds = true;
                                        break;
                                    }
                                }
                                if (tabelColumnEnds) break;
                            }
            //            }
                        continue;
                    }
                    ListIterator<ExtIntervalTextBox> iter2 = etbList2.listIterator();
                    while (iter2.hasNext()) {
                        ExtIntervalTextBox etb2 = iter2.next();
                        TextBox tb2 = etb2.textBox;
                        if (tb2 == null) {
                            iter2.remove();
                            continue;
                        }
                        float l2 = tb2.getLeft();
                        float r2 = tb2.getRight();
                        float c2 = (r2 + l2 ) / 2;
                        float ld = Math.abs(l1-l2);
                        float rd = Math.abs(r1-r2);
                        float cd = Math.abs(c1-c2);
                        if (ld < char_width || rd < char_width || cd < char_width){
                            // check if the vertical values are not well spaces
                            int numv = etb1.associates.size();
                            if (numv > 1){
                                float d1 = etb1.associates.get(numv-1).textBox.getTop() - etb1.associates.get(numv-2).textBox.getBase();
                                if (d1 < 0){
                                    logger.error("Distance is negative!!!");
                                } else {
                                    float d2 = etb2.textBox.getTop() - etb1.associates.get(numv-1).textBox.getBase();
                                    if (d2 / d1 > 2){
                                        column_scan_ended = true;
                                        break;
                                    }
                                }
                            }
                            etb1.associates.add(etb2);
                //            iter2.remove();
                            break;
                        }
                    }

                }
            }
        }

        mapIter = values.entrySet().iterator();
        // find associates
        while (mapIter.hasNext()) {
            Map.Entry<Integer, List<ExtIntervalTextBox>> entry = mapIter.next();
            List<ExtIntervalTextBox> etbList1 = entry.getValue();
            if (etbList1 == null || etbList1.size() == 0) {
                mapIter.remove();
            }
        }
    }

    private static class AutoValue{
        float h_score = 100000f;
        float v_score = 100000f;
        Interval hValue = null;
        List<Interval> vValue = new ArrayList<>();
        public AutoValue(){

        }

        public void merge(AutoValue autoValue){
            if (autoValue.h_score < h_score){
                hValue = autoValue.hValue;
                h_score = autoValue.h_score;
            }
            if (autoValue.v_score < v_score){
                vValue = autoValue.vValue;
                v_score = autoValue.v_score;
            }
        }

        public List<Interval> getBest(){
            List<Interval> list = new ArrayList<>();
            if (v_score < h_score && v_score< 200f){
                list.addAll(vValue);
            } else if (h_score < 200f){
                list.add(hValue);
            }
            return list;
        }
    }

    private AutoValue findBestValue(Map<Integer, TextBox> lineTextBoxMap,
                                    ExtIntervalTextBox extIntervalTextBox,
                                    TreeMap<Integer, List<ExtIntervalTextBox>> candidateValues)
    {
        // check for simple form values
        AutoValue autoValue = new AutoValue();

        List<ExtIntervalTextBox> allETBs = new ArrayList<>();
        for (Map.Entry<Integer, List<ExtIntervalTextBox>> e : candidateValues.entrySet()) {
            List<ExtIntervalTextBox> extIntervalTextBoxList = e.getValue();
            for (ExtIntervalTextBox etb : extIntervalTextBoxList) {
                TextBox candidate_vtb = etb.textBox;
                if (candidate_vtb == null) {
                    logger.debug("NO textbox {}", etb.interval.getStr());
                    continue;
                }
                BaseTextBox vtb = candidate_vtb.getChilds().get(0);
                float hOverlap = getVerticalOverlap(extIntervalTextBox.textBox, vtb);
                if (hOverlap > .3) {
                    allETBs.add(etb);
                }

                for (ExtIntervalTextBox etb_a : etb.associates) {
                    TextBox candidate_vtb_a = etb_a.textBox;
                    if (candidate_vtb_a == null) {
                        logger.warn("NO textbox {}", etb_a.interval.getStr());
                        continue;
                    }
                    BaseTextBox vtb_a = candidate_vtb_a.getChilds().get(0);
                    float hOverlap_a = getVerticalOverlap(extIntervalTextBox.textBox, vtb_a);
                    if (hOverlap_a > .3) {
                        allETBs.add(etb_a);
                    }
                }
            }
        }

        for (ExtIntervalTextBox etb : allETBs){
            float d = etb.textBox.getLeft() - extIntervalTextBox.textBox.getChilds().get(extIntervalTextBox.textBox.getChilds().size() -1).getRight();
            //             if (d < 0) continue;

            String cur_line_str = etb.textBox.getLine_str();
            if (cur_line_str.length() > etb.interval.getStart() && etb.interval.getStart() > extIntervalTextBox.interval.getEnd()) {
                String gap = cur_line_str.substring(extIntervalTextBox.interval.getEnd(), etb.interval.getStart());
                //remove (some text here) pattern
                gap = gap.replaceAll("\\([^\\)]+\\)", "").trim();
                gap = gap.replaceAll("[^\\p{L}\\p{N}]", "");
                if (gap.length() != 0) continue;

        //        autoValue.h_score = Math.max(0, d);
                autoValue.h_score = Math.max(0, d);
                autoValue.hValue = etb.interval;
                break;
            }
        }
        if (autoValue.hValue != null) return autoValue;
        // searching top to bottom - find the extend of the header

        int lastLine = candidateValues.lastEntry().getKey();
        ExtIntervalTextBox lastMatched = extIntervalTextBox;
        float header_h = extIntervalTextBox.textBox.getBase() - extIntervalTextBox.textBox.getTop();
        int max_key_value_distance_in_lines =  20;

        BaseTextBox header_first = lastMatched.textBox.getChilds().get(0);
        BaseTextBox header_last = lastMatched.textBox.getChilds().get(extIntervalTextBox.textBox.getChilds().size()-1);

        float average_char_width = 1.5f * (header_last.getRight() - header_first.getLeft()) / extIntervalTextBox.interval.getStr().length();
        AutoValue vertical_1 = new AutoValue();

        for (int keyLine = extIntervalTextBox.interval.getLine()+1 ; keyLine <= lastLine; keyLine++) {
            List<ExtIntervalTextBox> lineValues = candidateValues.get(keyLine);
            if (lineValues == null) continue;

            List<ExtIntervalTextBox> line_items = new ArrayList<>();

            for (int i=0; i< lineValues.size(); i++) {
                ExtIntervalTextBox em = lineValues.get(i);
                TextBox candidate_vtb = em.textBox;
                if (candidate_vtb == null) {
                    logger.warn("NO textbox {}", em.interval.getStr());
                    continue;
                }

                List<BaseTextBox> vtbs = candidate_vtb.getChilds();
                BaseTextBox first = vtbs.get(0);
                BaseTextBox last = vtbs.get(vtbs.size()-1);

                float dvtc = vtbs.get(0).getTop() - lastMatched.textBox.getBase();
                if (dvtc > max_key_value_distance_in_lines * header_h) break;

                float d_left = Math.abs(first.getLeft() - header_first.getLeft());
                float d_center = .5f * Math.abs(last.getRight() - header_last.getRight() + first.getLeft() - header_first.getLeft());
                float d_right = Math.abs(last.getRight() - header_last.getRight());

                float vOverlap = getHorizentalOverlap(extIntervalTextBox.textBox, candidate_vtb, false);

                if (vOverlap > .95) {
                    if (vertical_1.vValue.size() == 0) {
                        float min_horizental_d = Math.min(Math.min(d_left, d_center), d_right);
                        float s = (float) Math.sqrt(min_horizental_d * min_horizental_d + dvtc * dvtc);
                        if (s < vertical_1.v_score ){
                            vertical_1.v_score = s;
                            max_key_value_distance_in_lines =  15;
                            line_items.add(em);
                        }
                    } else {
                        boolean isValidVerticalValue = (d_left < average_char_width  || d_center < average_char_width || d_right < average_char_width);
                        float local_vOverlap = getHorizentalOverlap(lastMatched.textBox, candidate_vtb, false);
                        if (isValidVerticalValue || local_vOverlap > .9) {
                            line_items.add(em);
                        }
                    }
                }
            }

            if (line_items.size() == 0) continue;
            ExtIntervalTextBox bestInterval = line_items.get(0);

            if (line_items.size() > 1 ) {
                float d = 100000f;
                BaseTextBox origHeader = extIntervalTextBox.textBox.getChilds().get(0);
                float header_center = (origHeader.getRight() + origHeader.getLeft()) / 2;
                for (ExtIntervalTextBox esm : line_items) {
                    float center = (esm.textBox.getRight() + esm.textBox.getLeft()) / 2;
                    float dist = Math.abs(center - header_center);
                    if (dist < d) {
                        bestInterval = esm;
                        d = dist;
                    }
                }
            }

            vertical_1.vValue.add(bestInterval.interval);
            for (ExtIntervalTextBox etb : bestInterval.associates){
                vertical_1.vValue.add(etb.interval);
            }
            break;
        }

        autoValue.merge(vertical_1);
        return autoValue;
    }

    private boolean isConsecutiveCell(Map<Integer, TextBox> lineTextBoxMap,
                                      ExtIntervalTextBox above,
                                      ExtIntervalTextBox below,
                                      float height_mult,
                                      boolean isSecondOrBeyond){
        float lastMatchH = above.textBox.getBase() - above.textBox.getTop();
        if (isSecondOrBeyond) {
            float currH = below.textBox.getBase() - below.textBox.getTop();

            float lastMatchW = above.textBox.getRight() - above.textBox.getLeft();
            float currW = below.textBox.getRight() - below.textBox.getLeft();

            float r1 = lastMatchH > currH ? currH / lastMatchH : lastMatchH / currH;
            float r2 = lastMatchW > currW ? currW / lastMatchW : lastMatchW / currW;

            if (r1 < .9 || r2 < .9) {

                int num_overlapped_inbetween = countBetweenOverlappingBoxes(lineTextBoxMap, above, below);
                if (num_overlapped_inbetween > 1) return false;
            }
        } else {
            float dist_from_previous = below.textBox.getTop() - above.textBox.getBase();
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


    private int countBetweenOverlappingBoxes(Map<Integer, TextBox> lineTextBoxMap,
                                             ExtIntervalTextBox etb1,
                                             ExtIntervalTextBox etb2){
        if (etb1 == null || etb2 == null) return 0;
        TextBox tb1 = etb1.textBox;
        TextBox tb2 = etb2.textBox;
        int l1 = etb1.interval.getLine();
        int l2 = etb2.interval.getLine();
        float area_top = Math.min(tb1.getTop(), tb2.getTop());
        float area_base = Math.max(tb1.getBase(), tb2.getBase());
        float area_left = Math.min(tb1.getLeft(), tb2.getLeft());
        float area_right = Math.max(tb1.getRight(), tb2.getRight());

        BaseTextBox covered_area = new BaseTextBox(area_top, area_base, area_left, area_right, "");

        int num_partial_covered = 0;
        for (int i=l1+1; i< l2; i++){   // find the number of partial-overlapping boxes
            TextBox line_tb = lineTextBoxMap.get(i);
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

    private float getHorizentalOverlap(TextBox textBox1,
                                       TextBox textBox2,
                                       boolean useChilds){
        float l1 = useChilds ? textBox1.getChilds().get(0).getLeft() : textBox1.getLeft();
        float r1 = useChilds ? textBox1.getChilds().get(textBox1.getChilds().size() -1).getRight() : textBox1.getRight();

        float l2 = useChilds ? textBox2.getChilds().get(0).getLeft() : textBox2.getLeft();
        float r2 = useChilds ? textBox2.getChilds().get(textBox2.getChilds().size() -1).getRight() : textBox2.getRight();

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
        float t1 = textBox1.getTop();
        float b1 = textBox1.getBase();

        float t2 = textBox2.getTop();
        float b2 = textBox2.getBase();

        float h1 = b1 - t1;
        float h2 = b2 - t2;
        if (h2 == 0 || h1 == 0) return 0;

        float hh1 = b1 > b2 ? b2 - t1 : b1 - t2;

        return hh1 / Math.min(h1, h2);

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
