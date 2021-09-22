package com.quantxt.doc.helper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.Dictionary.ExtractionType;
import com.quantxt.types.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

/**
 * Created by dejani on 1/24/18.
 */

public class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    private static String AUTO = "__auto__";

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

    final private static Pattern FormValue = Pattern.compile(" *[;:] *((?:[^\\s;:]+ )*[^:;\\s]+)(?=$|\n| {2,})");
    final private static Pattern GenericToken = Pattern.compile("(?:^ *|  |\n *)((?:\\S+ )*\\S+)(?=$|\n| {2,})");

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

    @Override
    public List<ExtInterval> extract(final String content,
                                     List<DictSearch> extractDictionaries,
                                     boolean canSearchVertical) {
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
        //This should cover only DATE, DOUBLE and KEYWORD type labels
        Map<Integer, List<ExtIntervalSimpleMatcher>> formValues = new HashMap<>();
        Map<Integer, List<ExtIntervalSimpleMatcher>> genericValues = new HashMap<>();

        if (autoDetect) {
            String content_copy = getKeyValues(content, formValues);
            getGenericValues(content_copy, genericValues);
        }

        for (Map.Entry<String, Collection<ExtInterval>> labelEntry : labels.entrySet()){
            String dictId = labelEntry.getKey();

            long start_dictionary_match = System.currentTimeMillis();
            //get the dictionary

            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            Collection<ExtInterval> dictLabelList = labelEntry.getValue();

            for (ExtInterval labelInterval : dictLabelList) {

                if (dictSearch.getDictionary().getPattern().pattern().toLowerCase().equals(AUTO)) {
                    boolean isLastLineToken = setLocalPosition(content, labelInterval);
                    // check if we have already handled it via mapping
                    String str = labelInterval.getStr();
                    Matcher p = FormValue.matcher(str);
                    if (!p.find()) {
                        findBestValue(labelInterval, formValues, genericValues, isLastLineToken);
                    } else {
                        foundValues.add(labelInterval);
                    }
                } else {
                    ArrayList<ExtIntervalSimple> rowValues = findAllHorizentalMatches(content, dictSearch, labelInterval);
                    if (canSearchVertical && rowValues.size() == 0) {
                        rowValues = findAllVerticalMatches(content, dictSearch, labelInterval);
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

    private String getKeyValues(String content,
                                Map<Integer, List<ExtIntervalSimpleMatcher>> formValues){
        Matcher formValueMatcher = FormValue.matcher(content);
        StringBuilder content_copy = new StringBuilder(content);
        while (formValueMatcher.find()){
            int s = formValueMatcher.start(1);
            int e = formValueMatcher.end(1);
            String str = formValueMatcher.group(1);
            ExtIntervalSimple extInterval = new ExtIntervalSimple();
            LineInfo lineInfo = getLineInfo(content, s);
            int offset = s - lineInfo.localStart;

            extInterval.setStart(lineInfo.localStart);
            extInterval.setEnd(e - offset);
            extInterval.setLine(lineInfo.lineNumber);
            extInterval.setStr(str);
            List<ExtIntervalSimpleMatcher> list = formValues.get(lineInfo.lineNumber);
            if (list == null){
                list = new ArrayList<>();
                formValues.put(lineInfo.lineNumber, list);
            }

            list.add(new ExtIntervalSimpleMatcher(extInterval, formValueMatcher.start() - offset,
                    formValueMatcher.end() - offset));
            for (int i=s; i<e; i++){
                content_copy.setCharAt(i, ' ');
            }
        }
        return content_copy.toString();
    }

    private static class ExtIntervalSimpleMatcher {
        public ExtIntervalSimple extIntervalSimple;
        public int start;
        public int end;
        public ExtIntervalSimpleMatcher(ExtIntervalSimple e, int s, int en){
            this.extIntervalSimple =e;
            this.start = s;
            this.end = en;
        }
        public int getStart(){
            return start;
        }
    }
    private void getGenericValues(String content,
                                  Map<Integer, List<ExtIntervalSimpleMatcher>> values) {
        Matcher genericValueMatcher = GenericToken.matcher(content);
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
            List<ExtIntervalSimpleMatcher> list = values.get(lineInfo.lineNumber);
            if (list == null){
                list = new ArrayList<>();
                values.put(lineInfo.lineNumber, list);
            }
            list.add(new ExtIntervalSimpleMatcher(extInterval, genericValueMatcher.start() - offset ,
                    genericValueMatcher.end() - offset));
        }
    }

    private void findBestValue(ExtInterval labelInterval,
                               Map<Integer, List<ExtIntervalSimpleMatcher>> formValues,
                               Map<Integer, List<ExtIntervalSimpleMatcher>> genericValues,
                               boolean isLastInLine
                               ){
        int keyLine = labelInterval.getLine();
        int keyStart = labelInterval.getStart();
        int keyEnd = labelInterval.getEnd();

        // check values on the line or one line below or above
        List<ExtIntervalSimpleMatcher> valuesOnSameLine = formValues.get(keyLine);
        List<ExtIntervalSimpleMatcher> valuesOnLineBefore = formValues.get(keyLine-1);
        List<ExtIntervalSimpleMatcher> valuesOnLineAfter = formValues.get(keyLine+1);
        if (valuesOnSameLine != null) {
            for (ExtIntervalSimpleMatcher em : valuesOnSameLine) {
                if (em.start - keyEnd < 2 && keyEnd <= em.start){
                    ArrayList<ExtIntervalSimple> list = new ArrayList<>();
                    list.add(em.extIntervalSimple);
                    labelInterval.setExtIntervalSimples(list);
                    return;
                }

            }
        }

        // so we look up to 4 lines below the header

        List<ExtIntervalSimpleMatcher> header = genericValues.get(keyLine);
        if (header == null) return;
        Collections.sort(header, Comparator.comparingInt(ExtIntervalSimpleMatcher::getStart));
        int first_index_after = keyEnd;
        int last_index_before = keyStart;
        for (ExtIntervalSimpleMatcher eem : header) {
            ExtIntervalSimple ee = eem.extIntervalSimple;
            if (ee.getEnd() < keyStart) {
                last_index_before = ee.getEnd();
            }
            if (ee.getStart() > keyEnd) {
                first_index_after = ee.getStart();
                break;
            }
        }
        // search up to 2 lines below, if not found search up to two lines above
        for (int l=1; l < 3; l++) {
            List<ExtIntervalSimpleMatcher> genericValuesOnLineAfter = genericValues.get(keyLine+l);
            if (genericValuesOnLineAfter == null) continue;

            for (ExtIntervalSimpleMatcher em : genericValuesOnLineAfter) {
                ExtIntervalSimple e = em.extIntervalSimple;
                boolean keyValueMatched = checkTableKeyValueAssoc(first_index_after, last_index_before, labelInterval, e, isLastInLine);
                if (keyValueMatched) {
                    ArrayList<ExtIntervalSimple> list = new ArrayList<>();
                    list.add(e);
                    labelInterval.setExtIntervalSimples(list);
                    return;
                }
            }
        }

        // check up to two lines above the header -- for forms where values are return above a line

        for (int l=1; l < 3; l++) {
            List<ExtIntervalSimpleMatcher> genericValuesOnLineAfter = genericValues.get(keyLine-l);
            if (genericValuesOnLineAfter == null) continue;

            for (ExtIntervalSimpleMatcher em : genericValuesOnLineAfter) {
                ExtIntervalSimple e = em.extIntervalSimple;
                boolean keyValueMatched = checkTableKeyValueAssoc(first_index_after, last_index_before, labelInterval, e, isLastInLine);
                if (keyValueMatched) {
                    ArrayList<ExtIntervalSimple> list = new ArrayList<>();
                    list.add(e);
                    labelInterval.setExtIntervalSimples(list);
                    return;
                }
            }
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

    private boolean checkTableKeyValueAssoc(int first_index_after_label1,
                                            int last_index_before_label1,
                                            Interval labelInterval1, // header
                                            Interval labelInterval2, // value
                                            boolean isLastInLine){

        int local_start_label2 = labelInterval2.getStart();
        int local_end_label2 = labelInterval2.getEnd();

        if ((isLastInLine || local_end_label2 <= first_index_after_label1 ) && local_start_label2 >= last_index_before_label1){
            if ( (labelInterval2.getStart() - labelInterval1.getEnd() ) > 20) { // table header and cell should not be too far from each other
                return false;
            }
            if ( (labelInterval1.getStart() - labelInterval2.getEnd() ) > 20) { // table header and cell should not be too far from each other
                return false;
            }
            return true;
        }

        return false;
    }

    private ArrayList<ExtIntervalSimple> findAllVerticalMatches(String content,
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
