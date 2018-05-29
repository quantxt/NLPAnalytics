package com.quantxt.doc.helper;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.quantxt.types.MapSort;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */

public class RUDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(RUDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    private static final String POS_FILE_PATH = "/ru/ru-pos-maxent.bin";

    private static final String STOPLIST_FILE_PATH = "/ru/stoplist.txt";
    private static final String VERB_FILE_PATH = "/ru/context.json";

    private static final Set<String> PRONOUNS = new HashSet<>(Arrays
            .asList("Он", "Его", "Ему", "онá", "oна", "oн", "eму", "eго"));

    private static Pattern NounPhrase = Pattern.compile("N([N]*N|N*A+|N*)|A+N+");
    private static Pattern VerbPhrase = Pattern.compile("V+");

    public RUDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH, STOPLIST_FILE_PATH,
                VERB_FILE_PATH, PRONOUNS);
    }

    public RUDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS);
    }

    @Override
    public void preInit() {
        //Analyzer
        analyzer = new RussianAnalyzer();
        //Tokenizer : TODO: This is not right for russian.. need to build a custome one
        tokenizer = new ClassicAnalyzer(CharArraySet.EMPTY_SET);
    }

    @Override
    public List<String> tokenize(String str) {
        String tokenized = str.replaceAll("([\",?\\>\\<\\'\\’\\:\\]\\[\\(\\)\\”\\“»«\\.])" , " $1 ");
        String [] parts = tokenized.split("\\s+");
        return Arrays.asList(parts);
    }

    @Override
    public String normalize(String workingLine) {
        workingLine = normBasic(workingLine);
        return workingLine.toLowerCase();
    }

    protected boolean isTagDC(String tag) {
        return tag.equals("C") || tag.equals("I") || tag.startsWith("S");
    }

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String orig, String[] parts) {
        String[] taags = getPosTags(parts);

//        for (int i = 0; i < parts.length; i++) {
//            logger.info(parts[i] + "_" + taags[i] + " ");
//        }

        StringBuilder allTags = new StringBuilder();

        for (String t : taags) {
            allTags.append(t.substring(0, 1));
        }

        HashMap<ExtInterval, Integer> intervals = new HashMap<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            //         String match = m.group();
            int s = m.start();
            int e = m.end();
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s, e));
            ExtInterval eit = StringUtil.findSpan(orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 1" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType("N");
                intervals.put(eit, s);
            }
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end();
            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s, e));
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

        for (Map.Entry<ExtInterval, Integer> e : intervalSorted.entrySet()) {
            ExtInterval eit = e.getKey();
            phrases.add(eit);
   //         logger.info(eit.getType() + " -> " + orig.substring(eit.getStart(), eit.getEnd()));
        }
        return phrases;

        /*
        String lowerCase_orig = orig.toLowerCase();
        int numTokens = parts.length;
        String [] taags = getPosTags(parts);
        if (taags.length != parts.length) {
            logger.error("Number of tags and parts is not the same: " + orig);
            return null;
        }
        List<String> tokenList= new ArrayList<>();
        List<ExtInterval> phrases = new ArrayList<>();
        String type = "X";
        for (int j = numTokens-1; j>=0; j--) {
            final String tag = taags[j];
            final String word = parts[j];
            if ( isTagDC(tag) ) {
                int nextIdx = j - 1;
                if (nextIdx < 0) continue;
                String nextTag = taags[nextIdx];
                if ((tokenList.size() != 0) ||
                        (type.equals("N") && nextTag.startsWith("N") ) ||
                        (type.equals("V") && nextTag.startsWith("V") ))
                {
                    tokenList.add(word);
                }
                continue;
            }
            if (tag.startsWith("N") || tag.startsWith("P-")){
                if (!type.equals("N") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 1 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }

                     tokenList.clear();
                }

                type = "N";

                tokenList.add(word);
            }  else if ( tag.startsWith("A")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            } else if (tag.startsWith("V")){
                if (!type.equals("V") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 2 '" + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "V";
                tokenList.add(word);
            } else if (tag.startsWith("Q") || tag.startsWith("R")){
                if (tokenList.size() != 0){
                    tokenList.add(word);
                }
            }  else {
                if (!type.equals("X") && tokenList.size() >0){
                    Collections.reverse(tokenList);
                    ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
                    if (eit == null) {
                        logger.error("NOT FOUND 3 " + String.join(" ", tokenList) + "' in: " + orig);
                    } else {
                        lowerCase_orig = lowerCase_orig.substring(0, eit.getStart());
                        eit.setType(type);
                        phrases.add(eit);
                    }
                    tokenList.clear();
                }
                type = "X";
            }
        }

        if (!type.equals("X") && tokenList.size() >0){
            Collections.reverse(tokenList);
            ExtInterval eit = StringUtil.findSpan(lowerCase_orig, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 4 '" + String.join(" ", tokenList) + "' in: " + orig);
            } else {
                eit.setType(type);
                phrases.add(eit);
            }
        }

        Collections.reverse(phrases);
        return phrases;
        */
    }
}
