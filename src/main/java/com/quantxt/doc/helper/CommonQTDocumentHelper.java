package com.quantxt.doc.helper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.doc.QTDocument;
import com.quantxt.types.*;
import com.quantxt.nlp.entity.QTValueNumber;
import com.quantxt.types.Dictionary;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import static com.quantxt.types.QTField.*;

import static com.quantxt.util.NLPUtil.isEmpty;

/**
 * Created by dejani on 1/24/18.
 */

public abstract class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    public enum QTPosTags {NOUNN, VERBB, INTJ, X, ADV, AUX, ADP, ADJ, CCONJ, PROPN, PRON, SYM, NUM, PUNCT}

    protected static final String DEFAULT_NLP_MODEL_DIR = "nlp_model_dir";

    private static Character NewLine = '\n';
    private static Pattern WORD_PTR = Pattern.compile("\\S+");
    private static Pattern TOKEN = Pattern.compile("[A-Za-z0-9]+[^ ]*");
    private static Pattern SPC_BET_WORDS_PTR = Pattern.compile("\\S(?= \\S)");

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

    private SentenceDetectorME sentenceDetector = null;
    private POSTaggerME posModel = null;
    private CharArraySet stopwords;

    protected Analyzer analyzer;
    protected Analyzer tokenizer;

    public CommonQTDocumentHelper() {
    }

    abstract void preInit();

    public abstract void loadNERModel();

    public abstract CommonQTDocumentHelper init();

    protected void loadPosModel(String posFilePath) throws Exception {
        String modelBaseDir = getModelBaseDir();
        if (modelBaseDir == null) {
            String error = DEFAULT_NLP_MODEL_DIR + " is not set!";
            logger.error(error);
            throw new IllegalStateException(error);
        }
        // POS
        if (!isEmpty(posFilePath)) {
            try (FileInputStream fis = new FileInputStream(modelBaseDir + posFilePath)) {
                POSModel model = new POSModel(fis);
                posModel = new POSTaggerME(model);
            }
        }
    }

    protected void init(String sentencesFilePath,
                        String stoplistFilePath,
                        Set<String> pronouns) throws IOException {


        preInit();

        String modelBaseDir = getModelBaseDir();
        if (modelBaseDir == null) {
            String error = DEFAULT_NLP_MODEL_DIR + " is not set!";
            logger.error(error);
            throw new IllegalStateException(error);
        }

        // Sentences
        if (!isEmpty(sentencesFilePath)) {
            try (FileInputStream fis = new FileInputStream(modelBaseDir + sentencesFilePath)) {
                SentenceModel sentenceModel = new SentenceModel(fis);
                sentenceDetector = new SentenceDetectorME(sentenceModel);
            }
        }

        // Stoplist
        if (!isEmpty(stoplistFilePath)) {
            stopwords = new CharArraySet(800, true);
            try {
                List<String> sl = Files.readAllLines(Paths.get(modelBaseDir + stoplistFilePath));
                for (String s : sl) {
                    stopwords.add(s);
                }
            } catch (IOException e) {
                logger.error("Error on reading stoplist with message {}", e.getMessage());
            }
        }
    }

    @Override
    public List<String> tokenize(String str) {
        List<String> tokens = new ArrayList<>();
        try {
            TokenStream result = tokenizer.tokenStream(null, str);
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
        //    workingLine = workingLine.replaceAll("^([{\\p{L}\\p{N}]+[\\.\\&]*[{\\p{L}\\p{N}]+[\\.]*)" , "");
        return String.join(" ", normParts);
    }

    public String normalize(String workingLine) {
        return normBasic(workingLine).toLowerCase();
    }

    //sentence detc is NOT thread safe  :-/
    public String[] getSentences(String text) {
        synchronized (sentenceDetector) {
            String[] sentences = sentenceDetector.sentDetect(text);
            ArrayList<String> sentence_arr = new ArrayList<>();
            for (String str : sentences) {
                String[] bullet_points = str.split(UTF8_BULLETS);
                for (String b : bullet_points) {
                    b = b.trim();
                    if (b.isEmpty()) continue;
                    sentence_arr.add(b);
                }
            }
            return sentence_arr.toArray(new String[sentence_arr.size()]);
        }
    }

    //pos tagger is NOT thread safe  :-/
    @Override
    public String[] getPosTags(String[] text) {
        synchronized (posModel) {
            return posModel.tag(text);
        }
    }

    @Override
    public boolean isSentence(String str, List<String> tokens) {
        int numTokens = tokens.size();
        //TODO: this is too high.. pass a parameter
        // this is equal to size of almost one page of content
        if (numTokens < 6 || numTokens > 500) {
            return false;
        }
        return true;
    }

    @Override
    public Set<String> getStopwords() {
        Iterator iter = stopwords.iterator();
        HashSet<String> set = new HashSet<>();
        while (iter.hasNext()) {
            Object obj = iter.next();
            set.add(obj.toString());
        }
        return set;
    }

    public static String getModelBaseDir() {
        return System.getenv(DEFAULT_NLP_MODEL_DIR);
    }

    @Override
    public String getValues(String str, String context, List<ExtIntervalSimple> valueInterval) {
        return QTValueNumber.detect(str, context, valueInterval);
    }

    private Map<String, List<ExtInterval>> findLabels(List<DictSearch> extractDictionaries,
                                                      String content,
                                                      int slop) {
        Map<String, List<ExtInterval>> labels = new HashMap<>();
        for (DictSearch dictSearch : extractDictionaries) {
            String dict_id = dictSearch.getDictionary().getId();
            Dictionary.ExtractionType extractionType = dictSearch.getDictionary().getValType();
            Collection<ExtInterval> qtMatches = dictSearch.search(content, slop);
            ArrayList<ExtInterval> dicLabels = new ArrayList<>();
            for (ExtInterval qtMatch : qtMatches) {
                ExtInterval extInterval = new ExtInterval();
                extInterval.setDict_name(qtMatch.getDict_name());
                extInterval.setDict_id(qtMatch.getDict_id());
                extInterval.setCategory(qtMatch.getCategory());
                extInterval.setStr(qtMatch.getStr());
                extInterval.setStart(qtMatch.getStart());
                extInterval.setEnd(qtMatch.getEnd());
                extInterval.setType(extractionType);
                dicLabels.add(extInterval);
            }
            labels.put(dict_id, dicLabels);
        }
        return labels;
    }

    @Override
    public void extract(QTDocument qtDocument,
                        List<DictSearch> extractDictionaries,
                        boolean canSearchVertical,
                        String context) {
        long start = System.currentTimeMillis();
        final String content = qtDocument.getTitle();
        Map<String, List<ExtInterval>> labels = findLabels(extractDictionaries, content, 0);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        Map<String, DictSearch> valueNeededDictionaryMap = new HashMap<>();

        for (DictSearch qtSearchable : extractDictionaries) {
            Dictionary.ExtractionType extractionType = qtSearchable.getDictionary().getValType();
            String dicId = qtSearchable.getDictionary().getId();
            if ( extractionType != null ) {
                valueNeededDictionaryMap.put(dicId, qtSearchable);
            } else {
                if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                List<ExtInterval> labelExtIntervalList = labels.get(dicId);
                if (labelExtIntervalList == null || labelExtIntervalList.isEmpty()) continue;
                qtDocument.getValues().addAll(labelExtIntervalList);
                // These labels don't need a value,
                for (ExtInterval labelExtInterval : labelExtIntervalList){
                    int lableStart = labelExtInterval.getStart();
                    LineInfo lineInfo = getLineInfo(content, lableStart);
                    labelExtInterval.setStart(lineInfo.localStart);
                    labelExtInterval.setEnd(lineInfo.localStart + labelExtInterval.getEnd() - lableStart);
                    labelExtInterval.setLine(lineInfo.lineNumber);

                    qtDocument.addEntity(labelExtInterval.getDict_id(), labelExtInterval.getCategory());
                }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) return;

        //Searching for values that are associated with a label
        //This should cover only DATE, DOUBLE and KEYWORD type labels
        for (Map.Entry<String, List<ExtInterval>> labelEntry : labels.entrySet()){
            String dictId = labelEntry.getKey();

            long start_dictionary_match = System.currentTimeMillis();
            //get the dictionary

            DictSearch dictSearch = valueNeededDictionaryMap.get(dictId);
            if (dictSearch == null) continue;

            Dictionary dictionary = dictSearch.getDictionary();
            Dictionary.ExtractionType extractionType = dictionary.getValType();
            if (extractionType == null) continue;

            List<ExtInterval> dictLabelList = labelEntry.getValue();

            for (ExtInterval labelInterval : dictLabelList) {

                ArrayList<ExtIntervalSimple> rowValues = findAllHorizentalMatches(content, dictSearch, labelInterval);
                if (canSearchVertical && rowValues.size() == 0){
                    rowValues = findAllVerticalMatches(content, dictSearch, labelInterval);
                }

                if (rowValues.size() == 0) continue;

                for (ExtIntervalSimple eis : rowValues){
                    int gstart = eis.getStart();
                    LineInfo extIntervalLineInfo = getLineInfo(content, gstart);
                    eis.setEnd(extIntervalLineInfo.localStart + eis.getEnd() - eis.getStart());
                    eis.setStart(extIntervalLineInfo.localStart);
                    eis.setLine(extIntervalLineInfo.lineNumber);
                }

                labelInterval.setExtIntervalSimples(rowValues);
                int lableStart = labelInterval.getStart();
                LineInfo lineInfo = getLineInfo(content, lableStart);
                labelInterval.setStart(lineInfo.localStart);
                labelInterval.setEnd(lineInfo.localStart + labelInterval.getEnd() - lableStart);
                labelInterval.setLine(lineInfo.lineNumber);

                if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                qtDocument.getValues().add(labelInterval);
                qtDocument.addEntity(labelInterval.getDict_id(), labelInterval.getCategory());
            }

            long took_dictionary_match = System.currentTimeMillis() - start_dictionary_match;
            if (took_dictionary_match > 1000){
                logger.warn("Matching on [{} - {} - {} - {}] took {}ms", dictionary.getName(), dictionary.getValType()
                        , dictionary.getSkip_between_key_and_value(), dictionary.getSkip_between_values(), took );
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
        ext.setType(DataType.STRING);
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
        if (gapPattern == null) return false;
        String gap_between = content.substring(start, end);
        if (gap_between.length() == 0) return false;
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
        // list of DctSearhFld is teh same for all fields so we get the first one

        int start_search_shift = labelInterval.getEnd();
        Dictionary.ExtractionType extractionType = dictionary.getValType();

        switch (extractionType) {

            case DATETIME:
                int end_search_shift = (int) Math.max(200, .1 * content.length());
                end_search_shift += start_search_shift;
                if (end_search_shift > content.length()){
                    end_search_shift = content.length();
                }
                List<ExtIntervalSimple> dateExtIntervalSimples = QTValueNumber.detectDates(content.substring(start_search_shift, end_search_shift));
                for (ExtIntervalSimple eis : dateExtIntervalSimples){
                    int s = eis.getStart();
                    int e = eis.getEnd();
                    eis.setStart(s + start_search_shift);
                    eis.setEnd(e + start_search_shift);
                    results.add(eis);
                }
                break;
            case REGEX:
                int group = (dictionary.getGroups() == null || dictionary.getGroups().length ==0 )  ? 0 : dictionary.getGroups()[0];
                Pattern pattern = dictionary.getPattern();
                boolean canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                        pattern, group, analyzer, padding_between_key_value, results);

                while (canContinueSearchForValue) {
                    start_search_shift = results.get(results.size() - 1).getEnd();
                    canContinueSearchForValue = addShiftedValues(content, start_search_shift,
                            pattern, group, null, padding_between_values, results);
                }
                break;
            case NUMBER:
                String str_2_search = content.substring(start_search_shift);
                ExtIntervalSimple numeric = QTValueNumber.findFirstNumeric(str_2_search, start_search_shift);
                if (numeric == null) return results;

                if ( (numeric.getStart() - start_search_shift) == 0) { // number should not be attached to the label
                    start_search_shift = numeric.getEnd();
                    str_2_search = content.substring(start_search_shift);
                    numeric = QTValueNumber.findFirstNumeric(str_2_search, start_search_shift);
                    if (numeric == null) return results;
                }

                canContinueSearchForValue = validateFoundValue(content, start_search_shift,
                        numeric.getStart() , analyzer, padding_between_key_value);
                while (canContinueSearchForValue) {

                    results.add(numeric);
                    start_search_shift = results.get(results.size() - 1).getEnd();
                    //let's find following values
                    str_2_search = content.substring(start_search_shift);
                    numeric = QTValueNumber.findFirstNumeric(str_2_search, start_search_shift);
                    if (numeric == null) break;
                    canContinueSearchForValue = validateFoundValue(content, start_search_shift,
                            numeric.getStart(), null, padding_between_values);
                }
                break;
        }

        return results;

    }

    private ArrayList<ExtIntervalSimple> findAllVerticalMatches(String content,
                                                                DictSearch dictSearch,
                                                                Interval labelInterval)
    {
        ArrayList<ExtIntervalSimple> results = new ArrayList<>();
        Dictionary dictionary = dictSearch.getDictionary();
        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();
        Dictionary.ExtractionType extractionType = dictionary.getValType();

        //Best match has closest distance to center of label in X axis

        int startLineLabelInterval = getFirstNewLineBefore(content, labelInterval.getStart());

        int first_index_after_label = startNextToken(content, labelInterval.getEnd());
        int last_index_before_label = endPrevToken(content, labelInterval.getStart());

        int offsetStartLabel = last_index_before_label  - startLineLabelInterval;
        if (offsetStartLabel < 0) offsetStartLabel = 0;

        int offsetEndLabel   = first_index_after_label  - startLineLabelInterval;

        int end_interval = content.indexOf('\n', labelInterval.getEnd());

        if (end_interval < 0) return results; // no more lines to process

        String [] lines = content.substring(end_interval).split("\n");
        int position_in_lines_array = end_interval;
        List<String> verticalGap = new ArrayList<>();
        for (String line : lines){
            int line_length = line.length();
            if (line_length < offsetStartLabel) {
                position_in_lines_array += line_length + 1;
                continue;
            }
            int start_local_interval = position_in_lines_array+offsetStartLabel;
            int end_local_interval = Math.min(offsetEndLabel, line_length) + position_in_lines_array;

            position_in_lines_array += line_length + 1;  // 1 is the length for \n

            String string2Search4Value = content.substring(start_local_interval, end_local_interval);

            ExtIntervalSimple foundValue = null;
            switch (extractionType) {
                case DATETIME:
                    List<ExtIntervalSimple> dateExtIntervalSimples = QTValueNumber.detectDates(string2Search4Value);
                    if (dateExtIntervalSimples.size() >0){
                        foundValue = dateExtIntervalSimples.get(0);
                        int s = foundValue.getStart();
                        int e = foundValue.getEnd();
                        foundValue.setStart(s + start_local_interval);
                        foundValue.setEnd(e + start_local_interval);
                    }
                    break;
                case REGEX:
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
                                foundValue = new ExtIntervalSimple(start + start_local_interval, end + start_local_interval);
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
                    break;
                case NUMBER:
                    foundValue = QTValueNumber.findFirstNumeric(string2Search4Value, start_local_interval);
                    break;
            }

            if (foundValue == null) {
                //trip the search string so we get rid of padding spaces
                verticalGap.add(string2Search4Value.trim() +"\n");
                continue;
            }

            Pattern pattern_to_try_on_gap = padding_between_key_value;
            if (results.size() > 0) {
                pattern_to_try_on_gap = padding_between_values;
            }

            if (pattern_to_try_on_gap == null){
                break;
            }

            String vertical_gap = String.join("", verticalGap);
            Matcher match_on_gap = pattern_to_try_on_gap.matcher(vertical_gap);
            if (vertical_gap.isEmpty() || match_on_gap.find()) {
                results.add(foundValue);
                verticalGap = new ArrayList<>();
            } else if (results.size() > 1){
                break;
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

        for (int i = 0; i < start; i++) {
            if (str.charAt(i) == NewLine) {
                mostRecentNewLineIndex = i;
                lineNumber++;
            }
        }

        // return 0-based line number
        return new LineInfo(lineNumber, start - mostRecentNewLineIndex);
    }

    protected int startNextToken(String str, int start){
        Matcher m = TOKEN.matcher(str.substring(start));
        if (m.find()){
            return start + m.start();
        }
        return start;
    }

    protected int endPrevToken(String str, int start){
        // there is no way to search backward
        // so we create a substring just before the start and find the last match
        // 70 is long enough to look, back??
        int charBeforeStart = start - 1;
        int startString = Math.max(0, charBeforeStart - 70);
        if (charBeforeStart <= startString) return startString;
        String substringBeforeStart = str.substring(startString, charBeforeStart);
        Matcher m = TOKEN.matcher(substringBeforeStart);
        int endPreviousToken = startString;
        while (m.find()){
            endPreviousToken = m.end() + startString;
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

    public String extractHtmlExcerptForDocument(QTDocument qtDocument){
        StringBuilder sb = new StringBuilder();
        ArrayList<ExtInterval> values = qtDocument.getValues();
        if (values == null || values.size() == 0) return sb.toString();

        for (ExtInterval extInterval : values){
            String excerpt = extractHtmlExcerpt(qtDocument.getTitle(), extInterval);
            sb.append("<p>").append(excerpt).append("</p>");
        }
        return sb.toString();
    }

    private String getExcerpt(String[] lines,
                              Interval interval)
    {
        int horizentalBleed = 20;
        int verticalBleed = 2;

        int start = interval.getStart();
        int end = interval.getEnd();
        if (end < start ) return "";
        int lineNumber = interval.getLine();
        StringBuilder sb = new StringBuilder();


        int left = Math.max(0, start - horizentalBleed);
        int right = end + horizentalBleed;
        int top = Math.max(0, lineNumber - verticalBleed);
        int bottom = Math.min(lines.length, lineNumber + verticalBleed);
        bottom = Math.min(bottom, lines.length);

        for (int l=top; l<bottom; l++) {
            String line_str = lines[l];
            int line_length = line_str.length();
            if (l == lineNumber) {
                sb.append(line_str, left, start)
                        .append("<b>")
                        .append(interval.getStr())
                        .append("</b>");
                int post_fix_start = Math.min(end, line_length);
                int post_fix_end   = Math.min(line_length, right);
                if (post_fix_end > post_fix_start){
                    sb.append(line_str, post_fix_start, post_fix_end);
                }
                sb.append("<br>");
            } else {
                int end_chop = Math.min(line_length, right);
                if (left >= end_chop) continue;
                sb.append(line_str, left, Math.min(line_length, right))
                        .append("<br>");
            }
        }

        return "<p>" + sb.toString() +"</p>";
    }

    public String extractHtmlExcerpt(String content,
                                     ExtInterval extInterval)
    {
        String [] lines = content.split("\n");
        ArrayList<String> block = new ArrayList<>();
        block.add(getExcerpt(lines, extInterval));

        if (extInterval.getExtIntervalSimples() != null){
            for (ExtIntervalSimple eis : extInterval.getExtIntervalSimples()){
                block.add(getExcerpt(lines, eis));
            }
        }

        String out_str =  String.join("", block);
        //remove empty lines
        out_str = out_str.replaceAll(" {2,}", "  ");
        return out_str;
    }
}
