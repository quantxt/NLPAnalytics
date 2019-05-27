package com.quantxt.doc.helper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.helper.types.ExtIntervalSimple;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.util.StringUtil;

import static com.quantxt.helper.types.QTField.QTFieldType.NOUN;
import static com.quantxt.helper.types.QTField.QTFieldType.VERB;

/**
 * Created by dejani on 1/24/18.
 */
public class ESDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ESDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";

    private static final String POS_FILE_PATH = "/es/es-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/es/stoplist.txt";
    private static final String VERB_FILE_PATH = "/es/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella" , "Ella", "Él"));

    private static Pattern NounPhrase = Pattern.compile("N+S*N+|N+A*");
    private static Pattern VerbPhrase = Pattern.compile("RV+|V+");

    public ESDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS, false);
    }

    public ESDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS, false);

    }

    @Override
    public void preInit(){
        //Analyzer
        analyzer = new SpanishAnalyzer();
        //Tokenizer
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);
    }

    @Override
    public List<String> tokenize(String str) {
        String tokenized = str.replaceAll("([\",?\\>\\<\\'\\’\\:\\]\\[\\(\\)\\”\\“])" , " $1 ");
        tokenized = tokenized.replaceAll("([^\\.]+)(\\.+)\\s*$", "$1 $2").trim();
        String [] parts = tokenized.split("\\s+");
        return Arrays.asList(parts);
    }

    protected boolean isTagDC(String tag){
        return tag.equals("DET");
    }

    @Override
    public String normalize(String workingLine) {
        workingLine = normBasic(workingLine);
        return workingLine.toLowerCase();
    }

    //https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
    @Override
    public List<ExtIntervalSimple> getNounAndVerbPhrases(final String orig_str,
                                                   String[] tokens) {

        String[] taags = getPosTags(tokens);
        StringBuilder allTags = new StringBuilder();
        ExtIntervalSimple [] tokenSpans = StringUtil.findAllSpans(orig_str, tokens);

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
            eit.setCustomData(str);
            eit.setStringValue(str);
            eit.setType(NOUN);
            intervals.add(eit);
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtIntervalSimple eit = new ExtIntervalSimple(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setCustomData(str);
            eit.setStringValue(str);
            eit.setType(VERB);
            intervals.add(eit);
        }

        Collections.sort(intervals, new Comparator<ExtIntervalSimple>(){
            public int compare(ExtIntervalSimple p1, ExtIntervalSimple p2){
                Integer s1 = p1.getStart();
                Integer s2 = p2.getStart();
                return s1.compareTo(s2);
            }
        });

        return intervals;
    }

}
