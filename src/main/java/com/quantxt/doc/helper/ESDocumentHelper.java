package com.quantxt.doc.helper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.types.MapSort;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */
public class ESDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ESDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    //TODO Check with Matin why RUDocumentInfo was initialized with EN sentences

    private static final String POS_FILE_PATH = "/es/es-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/es/stoplist.txt";
    private static final String VERB_FILE_PATH = "/es/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella" , "Ella", "Él"));

    private static Pattern NounPhrase = Pattern.compile("N([SN]*N|N*A+|N*)");
    private static Pattern VerbPhrase = Pattern.compile("R+V+|V+");

    public ESDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS);
    }

    public ESDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);

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
        tokenized = tokenized.replaceAll("([^\\.]+)(\\.+)\\s*$", "$1 $2");
        String [] parts = tokenized.split("\\s+");
        return Arrays.asList(parts);
    }

    protected boolean isTagDC(String tag){
        return tag.equals("CS") || tag.startsWith("S") ||
                tag.equals("CC") || tag.startsWith("D");
    }

    @Override
    public String normalize(String workingLine) {
        workingLine = normBasic(workingLine);
        return workingLine.toLowerCase();
    }

    //https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String [] parts) {

        String[] taags = getPosTags(parts);

 //       for (int i=0; i < parts.length; i++){
 //           logger.info(parts[i] +"_" + taags[i] + " ");
 //       }

        StringBuilder allTags = new StringBuilder();

        for (String t : taags){
            allTags.append(t.substring(0,1));
        }

        HashMap<ExtInterval, Integer> intervals = new HashMap<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()){
            String match = m.group();
            int s = m.start();
            int e = m.end();
            if (match.contains("P") && !taags[s].equals(taags[e-1])){
                String tagStr = String.join("_", Arrays.copyOfRange(taags, s , e));
                if (!tagStr.contains("_P_")) continue;
            }
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));
            ExtInterval eit = StringUtil.findSpan(orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 1" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType("N");
                intervals.put(eit, s);
            }
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()){
            int s = m.start();
            int e = m.end();
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));
            ExtInterval eit = StringUtil.findSpan(orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 2" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType("V");
                intervals.put(eit, s);
            }

        }

        List<ExtInterval> phrases = new ArrayList<>();
        Map<ExtInterval, Integer> intervalSorted = MapSort.sortByValue(intervals);

        for (Map.Entry<ExtInterval, Integer> e : intervalSorted.entrySet()){
            ExtInterval eit = e.getKey();
            phrases.add(eit);
    //        logger.info(eit.getType() + " -> " + orig.substring(eit.getStart(), eit.getEnd()));
        }
        return phrases;
    }

}
