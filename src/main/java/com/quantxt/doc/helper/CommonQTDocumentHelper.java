package com.quantxt.doc.helper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.helper.types.ExtIntervalSimple;
import com.quantxt.helper.types.QTField;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.interval.Interval;
import com.quantxt.nlp.entity.QTValueNumber;
import com.quantxt.nlp.search.QTSearchable;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocumentHelper;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import static com.quantxt.helper.types.QTField.*;

import static com.quantxt.helper.types.QTField.QTFieldType.*;
import static com.quantxt.doc.helper.CommonQTDocumentHelper.ValueSearchMode.*;
import static com.quantxt.util.NLPUtil.isEmpty;

/**
 * Created by dejani on 1/24/18.
 */

public abstract class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);
    final public static ObjectMapper objectMapper = new ObjectMapper();

    public enum QTPosTags {NOUNN, VERBB, INTJ, X, ADV, AUX, ADP, ADJ, CCONJ, PROPN, PRON, SYM, NUM, PUNCT}

    protected static final String DEFAULT_NLP_MODEL_DIR = "nlp_model_dir";

    public enum ValueSearchMode {HORIZENTAL, VERTICAL};
    //Text normalization rules

    private static String alnum = "0-9A-Za-zŠŽšžŸÀ-ÖØ-öø-ž" + "Ѐ-ӿԀ-ԧꙀ-ꙮ꙾-ꚗᴀ-ᵿ";

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

    protected static Pattern r_punct_strip = Pattern.compile("([^" + alnum + "])|([" + alnum + "]+[\\&\\.]+[" + alnum + "]*)");
    protected static String s_punct_strip = " ";

    //Unicode spaces
    protected static Pattern r_white = Pattern.compile("[               　 ]+");
    protected static String s_white = " ";

    private SentenceDetectorME sentenceDetector = null;
    private POSTaggerME posModel = null;
    private CharArraySet stopwords;
    private Set<String> pronouns;

    protected Analyzer analyzer;
    protected Analyzer tokenizer;

    public CommonQTDocumentHelper() {
    }

    @Override
    public boolean isStopWord(String p) {
        return stopwords.contains(p);
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
        this.pronouns = new HashSet<>(pronouns);

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

    public ArrayList<String> stemmer(String str) {
        ArrayList<String> postEdit = new ArrayList<>();

        try {
            TokenStream stream = analyzer.tokenStream(null, new StringReader(str));
            //    stream = new StopFilter(stream, stopwords);
            CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = charTermAttribute.toString();
                postEdit.add(term);
            }
            stream.close();
        } catch (Exception e) {
            logger.error("Error Analyzer tokenStream for input String {}", str, e);
        }

        return postEdit;
    }

    @Override
    public String removeStopWords(String str) {
        ArrayList<String> postEdit = new ArrayList<>();

        Analyzer wspaceAnalyzer = new WhitespaceAnalyzer();  // this constructor do nothing
        try {
            TokenStream stream = wspaceAnalyzer.tokenStream(null, new StringReader(str));
            stream = new StopFilter(stream, stopwords);
            CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = charTermAttribute.toString();
                postEdit.add(term);
            }
            stream.close();
        } catch (Exception e) {
            logger.error("Error Analyzer tokenStream for input String {}", str, e);
        }

        return String.join(" ", postEdit);
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

    @Override
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
    public Set<String> getPronouns() {
        return pronouns;
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

    @Override
    public String getDatetimeValues(String str, String context, List<ExtIntervalSimple> valueInterval) {
        List<ExtIntervalSimple> list =  QTValueNumber.detectDates(str);
        valueInterval.addAll(list);
        return str;
    }

    @Override
    public String getPatternValues(String str, String context, Pattern regex, int[] groups, List<ExtIntervalSimple> valueInterval) {
        return QTValueNumber.detectPattern(str, context, regex, groups, valueInterval);
    }

    @Override
    public QTDocument.DOCTYPE getVerbType(String verbPhs) {
        return null;
    }

    private Map<String, List<ExtInterval>> findLabels(List<QTSearchable> extractDictionaries,
                                                      String content) {
        Map<String, List<ExtInterval>> labels = new HashMap<>();
        for (DictSearch<QTMatch> dictSearch : extractDictionaries) {
            QTFieldType valueType = dictSearch.getDictionary().getValType();
            String dictname = dictSearch.getDictionary().getName();
            Collection<QTMatch> qtMatches = dictSearch.search(content);
            ArrayList<ExtInterval> dicLabels = new ArrayList<>();
            for (QTMatch qtMatch : qtMatches) {
                //Matching ignore whitespace but in input string whitespace is used to maintain position of the text when needed
                // be concious of the span of the matches and avoid cases when there is large gap between tokens of a match
                int index_of_line_start = getFirstNewLineBefore(content, qtMatch.getStart());
                int index_of_line_end = content.indexOf('\n', qtMatch.getEnd());
                if (index_of_line_end < 0) { // there is no end of line
                    index_of_line_end = content.length();
                }
                int lineLength = index_of_line_end - index_of_line_start;
                long length_of_white_space_in_match = content.substring(qtMatch.getStart(), qtMatch.getEnd()).chars().filter(ch -> ch == ' ').count();
                // a keyword's padding should not cover more than 20% of the line
                float r = (float)length_of_white_space_in_match / lineLength;
                if ( r > .2) continue;

                ExtInterval extInterval = new ExtInterval();
                extInterval.setKeyGroup(qtMatch.getGroup());
                extInterval.setKey(qtMatch.getCustomData());
                extInterval.setStart(qtMatch.getStart());
                extInterval.setEnd(qtMatch.getEnd());
                extInterval.setType(valueType);
                extInterval.setLine(getLineNumber(content,qtMatch.getStart()));
                dicLabels.add(extInterval);
            }
            labels.put(dictname, dicLabels);
        }
        return labels;
    }

    public void extract(QTDocument qtDocument,
                        List<QTSearchable> extractDictionaries,
                        boolean canSearchVertical,
                        String context) {
        long start = System.currentTimeMillis();
        final String content = qtDocument.getTitle();
        Map<String, List<ExtInterval>> labels = findLabels(extractDictionaries, content);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        Map<String, Dictionary> valueNeededDictionaryMap = new HashMap<>();

        List<ExtIntervalSimple> numbers = new ArrayList<>();
        for (DictSearch<QTMatch> dictSearch : extractDictionaries) {
            QTField.QTFieldType valueType = dictSearch.getDictionary().getValType();
            String dicName = dictSearch.getDictionary().getName();
            switch (valueType) {
                case DATETIME:
                case KEYWORD:
                    valueNeededDictionaryMap.put(dicName, dictSearch.getDictionary());
                    break;
                //search for numbers only once
                case DOUBLE:
                    if (numbers.isEmpty()) {
                        QTValueNumber.detect(content, context, numbers);
                    }
                    valueNeededDictionaryMap.put(dicName, dictSearch.getDictionary());
                    break;
                default: //This account for STRING, NONE Null
                    if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                    List<ExtInterval> labelExtIntervalList = labels.get(dicName);
                    if (labelExtIntervalList == null || labelExtIntervalList.isEmpty()) continue;
                    qtDocument.getValues().addAll(labelExtIntervalList);
                    // These labels don't need a value,
                    for (ExtInterval labelExtInterval : labelExtIntervalList){
                        qtDocument.addEntity(labelExtInterval.getKeyGroup(), labelExtInterval.getKey());
                    }
            }
        }

        if (valueNeededDictionaryMap.isEmpty()) return;

        //Searching for values that are associated with a label
        //This should cover only DATE, DOUBLE and KEYWORD type labels
        for (Map.Entry<String, List<ExtInterval>> labelEntry : labels.entrySet()){
            String dicname = labelEntry.getKey();

            //get the dictionary
            Dictionary dictionary = valueNeededDictionaryMap.get(dicname);
            if (dictionary == null) continue; // Label is not associated with a dictionary that extracts values

            QTFieldType dictionaryType = dictionary.getValType();
            boolean canHaveValue =  (dictionaryType == DOUBLE || dictionaryType == KEYWORD || dictionaryType == DATETIME);
            if (!canHaveValue) continue;


            List<ExtInterval> dictLabelList = labelEntry.getValue();
            int lookAhead = Math.max(100, (int) (content.length()  * .1));

            for (ExtInterval labelInterval : dictLabelList) {
                QTFieldType labelIntervalType = labelInterval.getType();

                // we only look for values that are *after* a label
                int startSearchForValues = labelInterval.getEnd();
                //find an optimum string for look up
                int endSearch =  Math.min(lookAhead + startSearchForValues, content.length());
                Queue<ExtIntervalSimple> dictValueQueue = new LinkedList<>();
                switch (labelIntervalType){
                    case KEYWORD:
                        int stopSearch = startSearchForValues + (int) (content.length()  * .1);
                        List<ExtIntervalSimple> regexExtIntervalSimples = QTValueNumber.detectFirstPattern(content, startSearchForValues, stopSearch, dictionary);
                        dictValueQueue.addAll(regexExtIntervalSimples);
                    break;
                    case DOUBLE:
                        for (ExtIntervalSimple numberInterval : numbers){
                            if (numberInterval.getStart() > startSearchForValues){
                                dictValueQueue.add(numberInterval);
                            }
                        }
                        break;
                    case DATETIME:
                        List<ExtIntervalSimple> dateExtIntervalSimples = QTValueNumber.detectDates(content.substring(startSearchForValues, endSearch));
                        for (ExtIntervalSimple eis : dateExtIntervalSimples){
                            int s = eis.getStart();
                            int e = eis.getEnd();
                            eis.setStart(s + startSearchForValues);
                            eis.setEnd(e + startSearchForValues);
                            dictValueQueue.add(eis);
                        }
                        break;
                }

                if (dictValueQueue.isEmpty()) continue;

                // we always try horizental matches first. this might change in future
                ValueSearchMode valueSearchMode = HORIZENTAL;

                HeaderCellBoundary headerCellBoundary = new HeaderCellBoundary();
                boolean continueSearch = true;

                ArrayList<ExtIntervalSimple> rowValues = new ArrayList<>();
                while (!dictValueQueue.isEmpty() && continueSearch) {
                    ExtIntervalSimple valueInterval = dictValueQueue.poll();

                    switch (valueSearchMode){

                        case HORIZENTAL :
                            continueSearch = validateAndAddHorizentalMatches(content, dictionary, labelInterval, valueInterval, rowValues);
                            if (!continueSearch && canSearchVertical && rowValues.size() == 0){
                                continueSearch = validateAndAddVerticalMatches(content, dictionary, labelInterval, valueInterval, headerCellBoundary, rowValues);
                                if (continueSearch) {
                                    valueSearchMode = VERTICAL;
                                }
                            }
                            break;
                        case VERTICAL:
                            if (canSearchVertical) {
                                continueSearch = validateAndAddVerticalMatches(content, dictionary, labelInterval, valueInterval, headerCellBoundary, rowValues);
                            }
                            break;
                    }

                    // lazy search for Regex(Keyword) type value
                    if (dictValueQueue.isEmpty() && valueInterval.getType() == KEYWORD && continueSearch) {
                        int stopSearch = valueInterval.getEnd() + (int) (content.length()  * .1);
                        if (stopSearch > content.length()){
                            stopSearch = content.length();
                        }
                        List<ExtIntervalSimple> extIntervalSimples = QTValueNumber.detectFirstPattern(content, valueInterval.getEnd(), stopSearch, dictionary);
                        dictValueQueue.addAll(extIntervalSimples);
                    }
                }
                if (rowValues.size() == 0) continue;

                ExtInterval extInterval = new ExtInterval();
                extInterval.setExtIntervalSimples(rowValues);
                extInterval.setKeyGroup(labelInterval.getKeyGroup());
                extInterval.setKey(labelInterval.getKey());
                extInterval.setStart(labelInterval.getStart());
                extInterval.setEnd(labelInterval.getEnd());
                if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                qtDocument.getValues().add(extInterval);
                qtDocument.addEntity(labelInterval.getKeyGroup(), labelInterval.getKey());
            }

        }
    }

    private boolean validateAndAddHorizentalMatches(String content,
                                                    Dictionary dictionary,
                                                    Interval labelInterval,
                                                    ExtIntervalSimple candidateValue,
                                                    ArrayList<ExtIntervalSimple> results)
    {
        int num_results = results.size();

        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();

        int start_gap = num_results == 0 ? labelInterval.getEnd() : results.get(num_results-1).getEnd();
        int end_gap = candidateValue.getStart();
        String horizental_gap = getHorizentalGap(start_gap, end_gap, content);

        Pattern pattern_to_run_on_gap = num_results == 0 ? padding_between_key_value : padding_between_values;

        Matcher matcher = pattern_to_run_on_gap.matcher(horizental_gap);
        if ( matcher.find() ){
            // so if it is horizental and we are finding mutiple vlaues, then key and values should be more or less in same distance apart
            //    second_last <---D1---> last <----D2----> current     GOOD
            //    second_last <-----------------D1----------------> last <----D2----> current     GOOD
            //    second_last <---D1---> last <------------------D2------------------> current     BAD!
            if (num_results > 0){
                int lastIndexOfSecondLastInt = num_results == 1? labelInterval.getEnd() : results.get(num_results-2).getEnd();
                int firstIndexOfLastInt = results.get(num_results-1).getStart();
                int lastIndexOfLastInt  = results.get(num_results-1).getEnd();
                int firstIndexOfCurrentInt = candidateValue.getStart();
                int d1 = firstIndexOfLastInt - lastIndexOfSecondLastInt;
                int d2 = firstIndexOfCurrentInt - lastIndexOfLastInt;
                //we only apply this when we have too much space
                if (d2 > d1) {
                    float r = (float) d2 / d1;  // let's keep it between .4 to 2.5 .. so the gap can not become more than 2.5 times larger
                    if (r > 2.5) return false;
                }
            }
            candidateValue.setLine(getLineNumber(content,candidateValue.getStart()));
            results.add(candidateValue);
            return true;
        }

        return false;
    }

    private boolean validateAndAddVerticalMatches(String content,
                                                  Dictionary dictionary,
                                                  Interval labelInterval,
                                                  ExtIntervalSimple candidateValue,
                                                  HeaderCellBoundary headerCellBoundary,
                                                  ArrayList<ExtIntervalSimple> results)
    {
        int num_results = results.size();

        Pattern padding_between_values = dictionary.getSkip_between_values();
        Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();
        int startPreviousInterval = labelInterval.getStart();
        int endPreviousInterval = labelInterval.getEnd();
        if (num_results > 0) {
            ExtIntervalSimple lastFoundInterval = results.get(num_results-1);
            startPreviousInterval = lastFoundInterval.getStart();
            endPreviousInterval = lastFoundInterval.getEnd();
        }

        String [] vertical_overlap_array = getVerticalGep(startPreviousInterval, endPreviousInterval,
                candidateValue.getStart(), candidateValue.getEnd(),
                content, headerCellBoundary);

        if (vertical_overlap_array != null) {

            Pattern pattern_to_run_on_gap = num_results == 0 ? padding_between_key_value : padding_between_values;

            String vertical_gap = String.join("", vertical_overlap_array);
            if (vertical_gap.isEmpty() || pattern_to_run_on_gap.matcher(vertical_gap).find()) {
                candidateValue.setLine(getLineNumber(content,candidateValue.getStart()));
                results.add(candidateValue);
                return true;
            } else {
                // we found a valid gap but the value is not meeting the match critria
                return false;
            }
        }
        return true;
    }

    private int getLineNumber(String str, int startInterval){
        String [] linesBetween = str.substring(0, startInterval).split("\n");
        // return 0-based line number
        return linesBetween.length;
    }

    protected String getHorizentalGap(int start, int end, String str) {
        if (start > end) return null;
        return str.substring(start, end);
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

    protected String [] getVerticalGep(int startHeaderInterval,
                                       int endHeaderInterval,
                                       int startCellInterval,
                                       int endCellInterval,
                                       String str,
                                       HeaderCellBoundary headerCellBoundary) {

        String linesBetweenStr = str.substring(startHeaderInterval, endCellInterval);
        if (linesBetweenStr.indexOf("\n") < 0) {// values are not vertical,  return null
            return null;
        }

        String[] linesBetween = linesBetweenStr.split("\n");
        // so, if the first line is regular text don't bother
        if (linesBetween.length == 0) return null;
        int beginningOfHeaderRow = str.lastIndexOf("\n", startHeaderInterval);
        if (beginningOfHeaderRow == -1){
            beginningOfHeaderRow = 0;
        }

        int endOfHeaderRow = str.indexOf("\n", endHeaderInterval);
        String headerRow = str.substring(beginningOfHeaderRow, endOfHeaderRow);
        if (!lineIsTableRow(headerRow)) return null;

        if (!headerCellBoundary.initialized ) {
            int startLineInterval1 = getFirstNewLineBefore(str, startHeaderInterval);
            int first_index_after_interval1 = startNextToken(str, endHeaderInterval);
            int last_index_before_interval1 = endPrevToken(str, startHeaderInterval);
            int padding_left_interval1 = (startHeaderInterval - last_index_before_interval1) / 2;
            int padding_right_interval1 = (first_index_after_interval1 - endHeaderInterval) / 2;

            int offsetStartOverlap = startHeaderInterval - startLineInterval1 - padding_left_interval1;
            int offsetEndOverlap = endHeaderInterval - startLineInterval1 + padding_right_interval1;
            headerCellBoundary.init(offsetStartOverlap, offsetEndOverlap);
        }

        int offsetStartInterval1 = headerCellBoundary.getOffsetStartOverlap();
        int offsetEndInterval1 = headerCellBoundary.getOffsetEndOverlap();

        int startLineInterval2 = getFirstNewLineBefore(str, startCellInterval);
        int offsetStartInterval2 = startCellInterval - startLineInterval2;
        int offsetEndInterval2 = endCellInterval - startLineInterval2;

        //check if the value is within the range set by boundarties of its key (verticalGapDetails)
        boolean overlapValid = offsetEndInterval2 <= offsetEndInterval1 && offsetStartInterval2 >= offsetStartInterval1;
        if (!overlapValid) return null;

        //find indices of the vertical column for the gap
        int startOverlapIndex = Math.max(offsetStartInterval1, offsetStartInterval2);
        int endOverlapIndex = Math.min(offsetEndInterval1, offsetEndInterval2);

        // Find white paddings before and after the key and value in order to find vertical overlap
        //   more_text       |          key1      |        more_text
        //  text_here      |      val1          |        even_more

        // if there is no overlap then return null
        // allow vetically stacked blocks not to be exactly aligned
        int overlap_length = endOverlapIndex - startOverlapIndex;
        if (overlap_length < 0 || offsetStartInterval2 > offsetEndInterval1 || offsetEndInterval2 < offsetStartInterval1) return null;

        String [] verticalOverlapArray = new String[linesBetween.length -1];
        Arrays.fill(verticalOverlapArray, "\n");
        for (int i = 1; i < linesBetween.length - 1; i++) {
            String line = linesBetween[i];

            int endidx = Math.min(endOverlapIndex, line.length());
            if (endidx > startOverlapIndex) {
                String gap = line.substring(startOverlapIndex, endidx);
                verticalOverlapArray[i-1] = gap;
            }
        }
        return verticalOverlapArray;
    }

    protected int getFirstNewLineBefore(String str, int index) {
        int index_start_line = str.substring(0, index).lastIndexOf('\n');
        if (index_start_line < 0) return 0;
        return index_start_line;
    }


    private class HeaderCellBoundary {
        private int offsetStartOverlap;
        private int offsetEndOverlap;
        boolean initialized;

        public HeaderCellBoundary(){
            initialized = false;
        }

        public void init(int s, int e) {
            offsetStartOverlap = s;
            offsetEndOverlap = e;
            initialized = true;
        }

        public int getOffsetStartOverlap() {
            return offsetStartOverlap;
        }

        public int getOffsetEndOverlap() {
            return offsetEndOverlap;
        }
    }
}
