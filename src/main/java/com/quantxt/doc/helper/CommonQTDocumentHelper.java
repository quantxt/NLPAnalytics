package com.quantxt.doc.helper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.doc.QTDocument.DOCTYPE;
import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.trie.Emit;
import com.quantxt.trie.Trie;
import com.quantxt.util.StringUtil;
import com.quantxt.util.TrieUtil;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

/**
 * Created by dejani on 1/24/18.
 */
public abstract class CommonQTDocumentHelper implements QTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(CommonQTDocumentHelper.class);

    protected static final String DEFAULT_NLP_MODEL_DIR = "nlp_model_dir";

    private SentenceDetectorME sentenceDetector = null;
    private POSTaggerME posModel = null;
    private CharArraySet stopwords;
    private Set<String> pronouns;
    private Trie verbTree;

    protected Analyzer analyzer;
    protected Analyzer tokenizer;

    public CommonQTDocumentHelper(InputStream contextFile, String sentencesFilePath,
                                  String posFilePath, String stoplistFilePath,
                                  Set<String> pronouns) {
        try {
            init(sentencesFilePath, posFilePath, stoplistFilePath, pronouns);
            initVerbTree(contextFile);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error on init Document helper!", e);
        }
    }

    public CommonQTDocumentHelper(String sentencesFilePath, String posFilePath,
                                  String stoplistFilePath, String verbFilePath,
                                  Set<String> pronouns) {
        try {
            init(sentencesFilePath, posFilePath, stoplistFilePath, pronouns);
            initVerbTree(verbFilePath);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error on init Common Document helper!", e);
        }
    }

    @Override
    public boolean isStopWord(String p) {
        return stopwords.contains(p);
    }

    abstract void preInit();

    private void init(String sentencesFilePath, String posFilePath,
            String stoplistFilePath, Set<String> pronouns) throws Exception {

        preInit();
        this.pronouns = new HashSet<>(pronouns);

        String modelBaseDir = getModelBaseDir();
        if (modelBaseDir == null) {
            String error = DEFAULT_NLP_MODEL_DIR + " is not set!";
            logger.error(error);
            throw new IllegalStateException(error);
        }

        // Sentences
        if (!StringUtil.isEmpty(sentencesFilePath)) {
            try (FileInputStream fis = new FileInputStream(modelBaseDir + sentencesFilePath)) {
                SentenceModel sentenceModel = new SentenceModel(fis);
                sentenceDetector = new SentenceDetectorME(sentenceModel);
            }
        }

        // POS
        if (!StringUtil.isEmpty(posFilePath)) {
            try (FileInputStream fis = new FileInputStream(modelBaseDir + posFilePath)) {
                POSModel model = new POSModel(fis);
                posModel = new POSTaggerME(model);
            }
        }

        // Stoplist
        if (!StringUtil.isEmpty(stoplistFilePath)) {
            stopwords = new CharArraySet(800, true);
            try (FileInputStream fis = new FileInputStream(modelBaseDir + stoplistFilePath)) {
                List<String> sl = IOUtils.readLines(fis, "UTF-8");
                for (String s : sl){
                    stopwords.add(s);
                }
            } catch (IOException e) {
                logger.error("Error on reading stoplist with message {}", e.getMessage());
            }
        }
        logger.info("Models initiliazed");
    }

    private void initVerbTree(String verbFilePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(getModelBaseDir() + verbFilePath)) {
            byte[] verbArr = IOUtils.toByteArray(fis);
            initVerbTree(verbArr);
        } catch (Exception e) {
            logger.error("Error on initialize verbTree with for verbFilePath: {} with message: {}",
                    verbFilePath, e.getMessage());
        }
    }

    private void initVerbTree(InputStream contextFile) throws Exception {
        byte[] verbArr = IOUtils.toByteArray(contextFile);
        initVerbTree(verbArr);
    }

    private void initVerbTree(byte[] verbArr) throws IOException {
        this.verbTree = TrieUtil.buildVerbTree(verbArr, (str) -> {
            return tokenize(str);
        });
    }

    @Override
    public Trie getVerbTree() {
        return verbTree;
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
        } catch (Exception e){
            logger.error("Analyzer: " + e.getMessage());
        }
        return tokens;
    }

    public ArrayList<String> stemmer(String str) {
        ArrayList<String> postEdit = new ArrayList<>();

        try {
            TokenStream stream  = analyzer.tokenStream(null, new StringReader(str));
            stream = new StopFilter(stream, stopwords);
            CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String term = charTermAttribute.toString();
                postEdit.add(term);
            }
            stream.close();
        } catch (Exception e){
            logger.error("Error Analyzer tokenStream for input String {}", str, e);
        }

        return postEdit;
    }

    @Override
    public String normalize(String string) {
        ArrayList<String> postEdit = stemmer(string);
        return String.join(" ", postEdit);
    }

    //sentence detc is NOT thread safe  :-/
    public String[] getSentences(String text) {
        return sentenceDetector.sentDetect(text);
    }

    //pos tagger is NOT thread safe  :-/
    @Override
    public String[] getPosTags(String [] text) {
        String [] tags;
        synchronized (posModel)	{
            tags = posModel.tag(text);
        }
        return tags;
    }

    public DOCTYPE getVerbType(String verbPhs) {

        List<String> tokens = tokenize(verbPhs);
        if (tokens.size() == 0) return null;

        Collection<Emit> emits = getVerbTree().parseText(String.join(" ", tokens));
        for (Emit e : emits) {
            DOCTYPE vType = (DOCTYPE) e.getCustomeData();
            if (vType == DOCTYPE.Aux) {
                if (emits.size() == 1) return null;
                continue;
            }
            return vType;
        }
        return null;
    }

    @Override
    public Set<String> getPronouns() {
        return pronouns;
    }

    @Override
    public boolean isSentence(String str, List<String> tokens) {
        int numTokens = tokens.size();
        if (numTokens < 6 || numTokens > 80) {
            return false;
        }
        return true;
    }

    @Override
    public CharArraySet getStopwords() {
        return stopwords;
    }

    public String getModelBaseDir() {
        return System.getenv(DEFAULT_NLP_MODEL_DIR);
    }

}
