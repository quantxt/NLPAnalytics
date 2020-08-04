package com.quantxt.doc.helper;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.types.ExtIntervalSimple;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.quantxt.types.QTField.DataType.NOUN;
import static com.quantxt.types.QTField.DataType.VERB;
import static com.quantxt.util.NLPUtil.findAllSpans;

/**
 * Created by dejani on 1/24/18.
 */

public class ENDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ENDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    private static final String POS_FILE_PATH = "/en/en-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/en/stoplist.txt";

    private static final Set<String> PRONOUNS = new HashSet<>(
            Arrays.asList("he", "she", "He", "She"));

    private static Pattern NounPhrase = Pattern.compile("NJ+N|J+N+|N+");
    private static Pattern VerbPhrase = Pattern.compile("V+R+V+|V+");

    private TokenizerME openNlpTokenizer;
    private static final String OPENNLP_TOKENIZER_FILE_PATH = "/en/en-token.bin";

    public ENDocumentHelper() {
        analyzer = new EnglishAnalyzer();
        tokenizer = new EnglishAnalyzer();
    }

    @Override
    public ENDocumentHelper init(){
        try {
            init(SENTENCES_FILE_PATH, STOPLIST_FILE_PATH, PRONOUNS);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public void loadNERModel(){
        try {
            this.loadPosModel(POS_FILE_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error on loading Pos Model!", e);
        }
    }

    protected boolean isTagDC(String tag) {
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC")
                || tag.equals("DT");
    }

    @Override
    public String normalize(String workingLine) {
        workingLine = normBasic(workingLine);
        return workingLine.toLowerCase();
    }

    @Override
    public void preInit() {
        //Analyzer
        analyzer = new EnglishAnalyzer();
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);

    }

    private String [] tokenizeUsingOpenNLP(String str){
        if (openNlpTokenizer == null) {
            try (FileInputStream fis = new FileInputStream(getModelBaseDir() + OPENNLP_TOKENIZER_FILE_PATH)) {
                TokenizerModel tokenizermodel = new TokenizerModel(fis);
                openNlpTokenizer = new TokenizerME(tokenizermodel);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return openNlpTokenizer.tokenize(str);
    }
    //https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
    public List<ExtIntervalSimple> getNounAndVerbPhrases(final String orig_str) {

        String [] tokens = tokenizeUsingOpenNLP(orig_str);
        String[] taags = getPosTags(tokens);
        StringBuilder allTags = new StringBuilder();
        ExtIntervalSimple[] tokenSpans = findAllSpans(orig_str, tokens);

        for (String t : taags) {
            allTags.append(t.substring(0, 1));
        }

        List<ExtIntervalSimple> intervals = new ArrayList<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtIntervalSimple eit = new ExtIntervalSimple(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setStr(str);
            eit.setType(NOUN);
            intervals.add(eit);
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtIntervalSimple eit = new ExtIntervalSimple(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setStr(str);
            eit.setType(VERB);
            intervals.add(eit);
        }

        Collections.sort(intervals, new Comparator<ExtIntervalSimple>() {
            public int compare(ExtIntervalSimple p1, ExtIntervalSimple p2) {
                Integer s1 = p1.getStart();
                Integer s2 = p2.getStart();
                return s1.compareTo(s2);
            }
        });

        return intervals;
    }
}
