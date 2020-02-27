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

    public void extract(QTDocument qtDocument,
                        List<QTSearchable> extractDictionaries,
                        boolean vertical_overlap,
                        String context) {
        long start = System.currentTimeMillis();
        String content = qtDocument.getTitle();
        Map<String, List<ExtInterval>> labels = findLabels(extractDictionaries, content);
        long took = System.currentTimeMillis() - start;
        logger.info("Found all labels in {}ms", took);

        ArrayList<ExtIntervalSimple> numbers = new ArrayList<>();
        ArrayList<ExtIntervalSimple> dates = new ArrayList<>();

        for (DictSearch<QTMatch> dictSearch : extractDictionaries) {
            if (dictSearch.getDictionary().getValType() == DATETIME && dates.isEmpty()) {
                getDatetimeValues(content, context, dates);
            } else if (dictSearch.getDictionary().getValType() == DOUBLE && numbers.isEmpty()) {
                getValues(content, context, numbers);
            }
        }
        // Number and Date extractiosn are indepedant of the dictionary

        long took1 = System.currentTimeMillis() - start - took;
        logger.info("Found all values in {}ms", took1);


        Map<String, ArrayList<ExtIntervalSimple>> labelValues = new HashMap<>();
        Map<String, Dictionary> name2Dictionary = new HashMap<>();

        for (DictSearch<QTMatch> dictSearch : extractDictionaries) {
            QTField.QTFieldType valueType = dictSearch.getDictionary().getValType();
            if (valueType == null) valueType = NONE;
            String dicName = dictSearch.getDictionary().getName();
            switch (valueType) {
                case DOUBLE:
                    labelValues.put(dicName, numbers);
                    break;
                case DATETIME:
                    labelValues.put(dicName, dates);
                    break;
                case KEYWORD:
                    ArrayList<ExtIntervalSimple> values = new ArrayList<>();
                    Pattern regex = dictSearch.getDictionary().getPattern();
                    int[] groups = dictSearch.getDictionary().getGroups();
                    getPatternValues(content, context, regex, groups, values);
                    labelValues.put(dicName, values);
                    break;
                case NONE:
                    //no associated values. So add all the labels
                    if (qtDocument.getValues() == null) qtDocument.setValues(new ArrayList<>());
                    for (List<ExtInterval> lbl : labels.values()) {
                        qtDocument.getValues().addAll(lbl);
                        for (ExtInterval label : lbl) {
                            qtDocument.addEntity(label.getKeyGroup(), label.getKey());
                        }
                    }

                    continue;
            }

            if (labelValues.size() == 0) continue;
            name2Dictionary.put(dicName, dictSearch.getDictionary());
        }

        for (Map.Entry<String, List<ExtInterval>> labelEntry : labels.entrySet()){
            String dicname = labelEntry.getKey();

            //get the dictionary
            Dictionary dictionary = name2Dictionary.get(dicname);
            if (dictionary == null) {
                logger.error("Dictionary not found {}", dicname);
                continue;
            }

            ArrayList<ExtIntervalSimple> dictValueList = labelValues.get(dicname);
            if (dictValueList == null || dictValueList.size() == 0){
                logger.info("No value for the dictionary {}", dicname);
                continue;
            }
            List<ExtInterval> dictLabelList = labelEntry.getValue();

            Pattern padding_between_values = dictionary.getSkip_between_values();
            Pattern padding_between_key_value = dictionary.getSkip_between_key_and_value();

            for (ExtInterval labelInterval : dictLabelList) {
                ArrayList<ExtIntervalSimple> rowValues = new ArrayList<>();
                Interval keyInterval = labelInterval;
                for (ExtIntervalSimple valueInterval : dictValueList) {

                    String horizental_gap = getHorizentalGap(keyInterval, valueInterval, content);
                    if (horizental_gap == null) continue;

                    horizental_gap = horizental_gap.replaceAll(" +", " ");

                    Pattern pattern_to_run_on_gap = rowValues.size() == 0 ? padding_between_key_value : padding_between_values;

                    Matcher matcher = pattern_to_run_on_gap.matcher(horizental_gap);

                    if (matcher.find()) {
                        rowValues.add(valueInterval);
                        keyInterval = valueInterval;
                    } else if (vertical_overlap) {
                        String vertical_gap = getVerticalGep(keyInterval, valueInterval, content);
                        if (vertical_gap != null) {
                            if (vertical_gap.isEmpty() || pattern_to_run_on_gap.matcher(vertical_gap).find()) {
                                rowValues.add(valueInterval);
                                keyInterval = valueInterval;
                            } else if (rowValues.size() > 0) {
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

    protected String getVerticalGep(Interval interval1, Interval interval2, String str) {

        StringBuilder sb = new StringBuilder();
        int offsetStartInterval1 = getOffsetFromLineStart(str, interval1.getStart());
        int offsetStartInterval2 = getOffsetFromLineStart(str, interval2.getStart());

        int length1 = interval1.getEnd() - interval1.getStart();
        int length2 = interval2.getEnd() - interval2.getStart();

        int offsetEndInterval1 = offsetStartInterval1 + length1;
        int offsetEndInterval2 = offsetStartInterval2 + length2;

        //find indices of the vertical column for the gap
        int startGapIndex = Math.min(offsetStartInterval1, offsetStartInterval2);
        int endGapIndex = Math.max(offsetEndInterval1, offsetEndInterval2);

        // if there is no overlap then return null
        // allow vetically stacked blocks not to be exactly aligned
        if ((endGapIndex - startGapIndex) >= (length1 + length2 + 4)) return null;

        String[] linesBetween = str.substring(interval1.getStart(), interval2.getEnd()).split("\n");

        for (int i = 1; i < linesBetween.length - 1; i++) {
            String line = linesBetween[i];
            int endidx = Math.min(endGapIndex, line.length());
            if (endidx > startGapIndex) {
                String gap = line.substring(startGapIndex, endidx);
                sb.append(gap).append(" ").append("\n");
            }
        }
        return sb.toString();
    }

    protected int getOffsetFromLineStart(String str, int index) {
        return index - str.substring(0, index).lastIndexOf('\n') - 1;
    }
}
