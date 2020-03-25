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
import static com.quantxt.util.NLPUtil.isEmpty;

/**
 * Created by dejani on 1/24/18.
 */

public abstract class CommonQTDocumentHelper implements QTDocumentHelper {

    final private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);
    final public static ObjectMapper objectMapper = new ObjectMapper();

    public enum QTPosTags {NOUNN, VERBB, INTJ, X, ADV, AUX, ADP, ADJ, CCONJ, PROPN, PRON, SYM, NUM, PUNCT}

    protected static final String DEFAULT_NLP_MODEL_DIR = "nlp_model_dir";
    //Text normalization rules

    private static Pattern CELL_PATTERN  = Pattern.compile("(( \\S+)+)($| {3,})");
    private static String alnum = "0-9A-Za-zŠŽšžŸÀ-ÖØ-öø-ž" + "Ѐ-ӿԀ-ԧꙀ-ꙮ꙾-ꚗᴀ-ᵿ";

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
        return QTValueNumber.detectDates(str, context, valueInterval);
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
                int index_of_line_start = getStartLine(content, qtMatch.getStart());
                int index_of_line_end = content.indexOf('\n', qtMatch.getEnd());
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
                dicLabels.add(extInterval);
            }
            labels.put(dictname, dicLabels);
        }
        return labels;
    }

    private boolean key_value_type_matched(QTFieldType type1, QTFieldType type2) {
        if (type1 == KEYWORD && type2 == KEYWORD) return true;
        if (type1 == NONE && type2 == NONE) return true;
        if (type1 == DATETIME && type2 == DATETIME) return true;
        if ((type1 == DOUBLE || type1 == PERCENT || type1 == MONEY || type1 == LONG) &&
                (type2 == DOUBLE || type2 == PERCENT || type2 == MONEY || type2 == LONG)) return true;
        return false;
    }

    private boolean allowVerticalLookup(ArrayList<ExtIntervalSimple> values,
                                        boolean lastLookUpWasHorizental){
        if (values.size() == 0) return true;
        if (lastLookUpWasHorizental) return false;
        return true;
    }

    private boolean allowHorizentalLookup(ArrayList<ExtIntervalSimple> values,
                                          boolean lastLookUpWasHorizental){
        if (values.size() == 0) return true;
        if (lastLookUpWasHorizental) return true;
        return false;
    }

    public void extract(QTDocument qtDocument,
                        List<QTSearchable> extractDictionaries,
                        boolean vertical_overlap,
                        String context) {
        long start = System.currentTimeMillis();
        final String content = qtDocument.getTitle();
        Map<String, List<ExtInterval>> labels = findLabels(extractDictionaries, content);
        long took = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took);

        Map<String, Dictionary> name2Dictionary = new HashMap<>();

        List<ExtIntervalSimple> numbers = new ArrayList<>();
        for (DictSearch<QTMatch> dictSearch : extractDictionaries) {
            QTField.QTFieldType valueType = dictSearch.getDictionary().getValType();
            String dicName = dictSearch.getDictionary().getName();
            switch (valueType) {
                case DATETIME:
                case KEYWORD:
                    name2Dictionary.put(dicName, dictSearch.getDictionary());
                    break;
                //search for numbers only once
                case DOUBLE:
                    QTValueNumber.detect(content, context, numbers);
                    name2Dictionary.put(dicName, dictSearch.getDictionary());
                    break;
                default: //This account for STRING, NONE Null
                    if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                    List<ExtInterval> lbl = labels.get(dicName);
                    if (lbl == null || lbl.isEmpty()) continue;
                    qtDocument.getValues().addAll(lbl);
                    for (ExtInterval label : lbl) {
                        qtDocument.addEntity(label.getKeyGroup(), label.getKey());
                    }
            }
        }

        long took1 = System.currentTimeMillis() - start;
        logger.debug("Found all labels in {}ms", took1);

        if (name2Dictionary.isEmpty()) return;

        for (Map.Entry<String, List<ExtInterval>> labelEntry : labels.entrySet()){
            String dicname = labelEntry.getKey();

            //get the dictionary
            Dictionary dictionary = name2Dictionary.get(dicname);
            if (dictionary == null) {
                logger.error("Dictionary not found {}", dicname);
                continue;
            }

            Pattern padding_between_values = dictionary.getSkip_between_values();
            Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();
            String header = null;

            List<ExtInterval> dictLabelList = labelEntry.getValue();
            int lookAhead = Math.max(100, (int) (content.length()  * .1));

            for (ExtInterval labelInterval : dictLabelList) {
                ArrayList<ExtIntervalSimple> rowValues = new ArrayList<>();
                Interval keyInterval = labelInterval;
                QTFieldType labelIntervalType = labelInterval.getType();
                //find an optimum string for look up
                int endSearch =  Math.min(lookAhead + keyInterval.getEnd(), content.length());
                List<ExtIntervalSimple> dictValueList = new ArrayList<>();
                switch (labelIntervalType){
                    case KEYWORD:
                        dictValueList = QTValueNumber.detectFirstPattern(content.substring(keyInterval.getEnd(), endSearch),
                                context, dictionary.getPattern(), dictionary.getGroups() != null);
                        for (ExtIntervalSimple extIntervalSimple : dictValueList){
                            int s = extIntervalSimple.getStart();
                            int e = extIntervalSimple.getEnd();
                            extIntervalSimple.setStart(s + keyInterval.getEnd());
                            extIntervalSimple.setEnd(e + keyInterval.getEnd());
                        }
                    break;
                    case DOUBLE:
                        dictValueList = numbers;
                        break;
                    case DATETIME:
                        getDatetimeValues(content.substring(keyInterval.getEnd(), endSearch), context, dictValueList);
                        for (ExtIntervalSimple extIntervalSimple : dictValueList){
                            int s = extIntervalSimple.getStart();
                            int e = extIntervalSimple.getEnd();
                            extIntervalSimple.setStart(s + keyInterval.getEnd());
                            extIntervalSimple.setEnd(e + keyInterval.getEnd());
                        }
                        break;
                }

                if (dictValueList.size() == 0) continue;

                boolean lastLookUpwasHorizental = false;

                for (ExtIntervalSimple valueInterval : dictValueList) {

                    String horizental_gap = getHorizentalGap(keyInterval, valueInterval, content);
                    if (horizental_gap == null) continue;
                    final int numValuesFound = rowValues.size();
                    horizental_gap = horizental_gap.replaceAll(" +", " ");

                    Pattern pattern_to_run_on_gap = numValuesFound == 0 ? padding_between_key_value : padding_between_values;

                    Matcher matcher = pattern_to_run_on_gap.matcher(horizental_gap);

                    if (matcher.find() && allowHorizentalLookup(rowValues, lastLookUpwasHorizental)) {
                        // so if it is horizental and we are finding mutiple vlaues, then key and values should be more or less in same distance apart
                        //    second_last <---D1---> last <----D2----> current     GOOD
                        //    second_last <-----------------D1----------------> last <----D2----> current     GOOD
                        //    second_last <---D1---> last <------------------D2------------------> current     BAD!
                        if (numValuesFound > 0){
                            int lastIndexOfSecondLastInt = numValuesFound == 1? labelInterval.getEnd() : rowValues.get(numValuesFound-2).getEnd();
                            int firstIndexOfLastInt = rowValues.get(numValuesFound-1).getStart();
                            int lastIndexOfLastInt  = rowValues.get(numValuesFound-1).getEnd();
                            int firstIndexOfCurrentInt = valueInterval.getStart();
                            int d1 = firstIndexOfLastInt - lastIndexOfSecondLastInt;
                            int d2 = firstIndexOfCurrentInt - lastIndexOfLastInt;
                            //we only apply this when we have too much space
                            if (d2 > d1) {
                                float r = (float) d2 / d1;  // let's keep it between .4 to 2.5 .. so the gap can not become more than 2.5 times larger
                                if (r > 2.5) continue;
                            }
                        }
                        rowValues.add(valueInterval);
                        keyInterval = valueInterval;
                        lastLookUpwasHorizental = true;

                    } else if (vertical_overlap &&  allowVerticalLookup(rowValues, lastLookUpwasHorizental)) {
                        String [] vertical_overlap_array = getVerticalGep(keyInterval, valueInterval, content);
                        if (vertical_overlap_array != null) {
         //                   if (numValuesFound == 0) { //first value
         //                       if (vertical_gap_array.length > 5) { //this is too far it can't be valid
         //                           break;
         //                       }
         //                   }
                            String vertical_gap = String.join("", vertical_overlap_array);
                            if (vertical_gap.isEmpty() || pattern_to_run_on_gap.matcher(vertical_gap).find()) {

                                /*
                                if (numValuesFound == 0){
                                    int st = getStartLine(content, keyInterval.getStart());
                                    int ed = content.indexOf('\n', st);
                                    header = content.substring(st, ed);
                                }

                                //check if header and row are compatible
                                int st = getStartLine(content, valueInterval.getStart());
                                int ed = content.indexOf('\n', st);
                                String row = content.substring(st, ed);
                                boolean rowIsATableRow = isTableRow(header, row);
                                if (!rowIsATableRow) continue;
                                */

                                rowValues.add(valueInterval);
                                keyInterval = valueInterval;
                                lastLookUpwasHorizental = false;
                            } else if (numValuesFound > 0) {
                                // so we found a valid vertical gap but it is not a valid value
                                break;
                            }
                        }
                    } else if (rowValues.size() > 0) {
                        break;
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

    protected String getHorizentalGap(Interval interval1, Interval interval2, String str) {
        if (interval1.getEnd() > interval2.getStart()) return null;
        return str.substring(interval1.getEnd(), interval2.getStart());
    }

    private int startNextToken(String str, int start){
        int startNextToken = start;
        while (startNextToken < str.length()){
            char c = str.charAt(startNextToken++);
            if (c == ' ') continue;
            break;
        }
        return startNextToken -1;
    }

    private int startPrevToken(String str, int start){
        int startNextToken = start;
        while (startNextToken >= 0){
            char c = str.charAt(startNextToken--);
            if (c == ' ') continue;
            break;
        }
        return startNextToken + 1;
    }

    protected boolean lineIsTableRow(String lineStr){
        //logic to determine if a line of text is a table row
        long num_spaces = lineStr.chars().filter(ch -> ch == ' ').count();
        // obvious condition 1: word1 word2 word3 .. pattern should not even be analyzed. It is not a table header or row
   //     String [] spaces = lineStr.split("\\s");
   //     String [] spaceBetweenTokens = lineStr.split("\\S\\s\\S");
        String noSpaceStr = lineStr.replace(" ", "");
        if ((float) noSpaceStr.length() > .7 * lineStr.trim().length()) return false;

        return true;
    }

    protected boolean isTableRow(String header, String row){

    //    if (header.length() < row.length()) return false;
        ArrayList<Interval> headers = new ArrayList<>();
        Matcher m = CELL_PATTERN.matcher(header);
        while (m.find()){
            headers.add(new Interval(m.start(2), m.end(2)));
        }

        ArrayList<Interval> values = new ArrayList<>();
        m = CELL_PATTERN.matcher(row);
        while (m.find()){
            values.add(new Interval(m.start(2), m.end(2)));
        }

        int badIntervals = 0;
        for (int i=0; i<values.size(); i++ ) {
            Interval interval1 = headers.get(i);
            if (interval1.getEnd() > header.length()) break;
            int first_index_after_interval1 = i==(headers.size()-1) ? header.length() : headers.get(i+1).getStart();
            int last_index_before_interval1 = i==(0) ? 0 : headers.get(i-1).getEnd();
            int padding_right_interval1 = (first_index_after_interval1 - interval1.getEnd()) / 2;
            int padding_left_interval1 = (interval1.getStart() - last_index_before_interval1) / 2;
            int offsetStartInterval1 = interval1.getStart() - padding_left_interval1;
            int offsetEndInterval1 = interval1.getEnd() + padding_right_interval1;

            boolean found = false;
            for (Interval interval2 : headers) {
                int offsetStartInterval2 = interval2.getStart();
                int offsetEndInterval2 = interval2.getEnd();

                //find indices of the vertical column for the gap

                int startOverlapIndex = Math.max(offsetStartInterval1, offsetStartInterval2);
                int endOverlapIndex = Math.min(offsetEndInterval1, offsetEndInterval2);

                // Find white paddings before and after the key and value in order to find vertical overlap
                //   more_text       |          key1      |        more_text
                //  text_here      |      val1          |        even_more

                // if there is no overlap then return null
                // allow vetically stacked blocks not to be exactly aligned
                int overlap_length = endOverlapIndex - startOverlapIndex;
                if (overlap_length > 0 && offsetStartInterval2 < offsetEndInterval1 && offsetEndInterval2 > offsetStartInterval1) {
                    found = true;
                    break;
                }
            }
            if (!found){
                badIntervals++;
            }
        }
    //    logger.info("Valids {} , {} ,{}", headers.size(), values.size(), validIntervals);
        return badIntervals == 0;

    }

    protected double alignmentRatio(String headerStr, String rowStr){
        double numCharsIn1 = 0;
        double numCharsIn2 = 0;
        double numCharsOverlap = 0;

        //find header cells
        ArrayList<Interval> headers = new ArrayList<>();
        Matcher m = CELL_PATTERN.matcher(headerStr);
        while (m.find()){
            headers.add(new Interval(m.start(), m.end()));
        }
        char [] header = headerStr.toCharArray();
        char [] row = rowStr.toCharArray();
        if (header.length < row.length) return 0;
        for (int i=0; i<row.length-1; i++){
            char h1 = header[i];
            char h2 = header[i+1];
            char r1 = row[i];
            char r2 = row[i+1];

            boolean c1IsValid = false;
            boolean c2IsValid = false;
            if (h1 != ' ' || (h1 == ' ' && h2 != ' ')){
                numCharsIn1 +=1;
                c1IsValid = true;
            }
            if (r1 != ' ' || (r1 == ' ' && r2 != ' ')){
                numCharsIn2 +=1;
                c2IsValid = true;
            }
            if (c1IsValid && c2IsValid){
                numCharsOverlap +=1;
            }
        }
        if (numCharsOverlap == 0) return 0;
        if (numCharsIn2 < 6 || numCharsIn1 < 6) return 0;
        return numCharsOverlap / Math.max(numCharsIn1, numCharsIn2);

    }

 //   protected String getOverlapBetweenVerticalIntervals(Interval interval1, Interval interval2){

 //   }

    protected String[] getVerticalGep(Interval interval1, Interval interval2, String str) {

        String linesBetweenStr = str.substring(interval1.getStart(), interval2.getEnd());
        if (linesBetweenStr.indexOf("\n") < 0) {// values are not vertical,  return null
            return null;
        }

        String[] linesBetween = linesBetweenStr.split("\n");
        // so, if the first line is regular text don't bother
        if (linesBetween.length == 0) return null;
        if (!lineIsTableRow(linesBetween[0])) return null;

        int startLineInterval1 = getStartLine(str, interval1.getStart());
        int startLineInterval2 = getStartLine(str, interval2.getStart());

        int first_index_after_interval1 = startNextToken(str, interval1.getEnd());
        int last_index_before_interval1 = startPrevToken(str, interval1.getStart()-1);

        int padding_right_interval1 = (first_index_after_interval1 - interval1.getEnd())/2;
        int padding_left_interval1 =  (interval1.getStart() - last_index_before_interval1)/2;

        int offsetStartInterval1 = interval1.getStart() - startLineInterval1 - padding_left_interval1;
        int offsetStartInterval2 = interval2.getStart() - startLineInterval2;

        int offsetEndInterval1 = interval1.getEnd() - startLineInterval1  + padding_right_interval1;
        int offsetEndInterval2 = interval2.getEnd() - startLineInterval2;

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
     //           double r = alignmentRatio(linesBetween[0], line);
     //           logger.info(linesBetween[0]);
     //           logger.info(line);
     //           logger.info("=======> r " + r);
            }
        }
        return verticalOverlapArray;
    }

    protected int getStartLine(String str, int index) {
        return str.substring(0, index).lastIndexOf('\n') + 1;
    }

}
