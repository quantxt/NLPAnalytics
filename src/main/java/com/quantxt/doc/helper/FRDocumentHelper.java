package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtIntervalSimple;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantxt.helper.types.QTField.QTFieldType.NOUN;
import static com.quantxt.helper.types.QTField.QTFieldType.VERB;
import static com.quantxt.util.NLPUtil.findAllSpans;

/**
 * Created by matin on 5/28/18.
 */

public class FRDocumentHelper extends CommonQTDocumentHelper {
    private static Logger logger = LoggerFactory.getLogger(FRDocumentHelper.class);

    private static Pattern NounPhrase = Pattern.compile("(N([LPN]*N|N*A+|N*))|AN+");
    private static Pattern VerbPhrase = Pattern.compile("V+");

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String POS_FILE_PATH = "/fr/fr-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/fr/stoplist.txt";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("il", "elle", "Elle", "Il"));

    public FRDocumentHelper() {
        super(SENTENCES_FILE_PATH, STOPLIST_FILE_PATH, PRONOUNS);
    }

    public void loadNERModel(){
        try {
            this.loadPosModel(POS_FILE_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error on loading Pos Model!", e);
        }
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
        String tokenized = str.replaceAll("(\\s|^)([ZzTtJjCcNnSsLlDd]|([Mm]a)|([Qq]u))'", "$1$2' ");
        tokenized = tokenized.replaceAll("([\",?\\>\\<\\’;<>\\\\%\\#`\\{\\}\\:\\]\\[\\(\\)\\”\\“])" , " $1 ");
        tokenized = tokenized.replaceAll("([^\\.]+)(\\.+)\\s*$", "$1 $2").trim();
        String [] parts = tokenized.split("\\s+");
        return Arrays.asList(parts);
    }

    protected boolean isTagDC(String tag){
        return tag.equals("CS") || tag.startsWith("S") /*|| tag.startsWith("D")*/;
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
        ExtIntervalSimple [] tokenSpans = findAllSpans(orig_str, tokens);

        for (String t : taags) {
            allTags.append(t.substring(0, 1));
        }

        List<ExtIntervalSimple> intervals = new ArrayList<>();
        Matcher m = NounPhrase.matcher(allTags.toString());

        while (m.find()){
            String match = m.group();
            int s = m.start();
            int e = m.end() - 1;
            if (match.contains("P") && !taags[s].equals(taags[e])){
                String tagStr = String.join("_", Arrays.copyOfRange(taags, s , e+1));
                if (!tagStr.contains("_P_")) continue;
            }
            ExtIntervalSimple eit = new ExtIntervalSimple(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            eit.setType(NOUN);
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setCustomData(str);
            eit.setStringValue(str);
            intervals.add(eit);
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtIntervalSimple eit = new ExtIntervalSimple(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            eit.setType(VERB);
            String str = orig_str.substring(eit.getStart(), eit.getEnd());
            eit.setCustomData(str);
            eit.setStringValue(str);
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
