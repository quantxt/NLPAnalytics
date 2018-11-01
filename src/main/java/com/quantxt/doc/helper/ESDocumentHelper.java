package com.quantxt.doc.helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.ESDocumentInfo;
import com.quantxt.doc.JADocumentInfo;
import com.quantxt.doc.QTDocument;
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

    private static final String POS_FILE_PATH = "/es/es-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/es/stoplist.txt";
    private static final String VERB_FILE_PATH = "/es/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(Arrays.asList("él", "ella" , "Ella", "Él"));

    private static Pattern NounPhrase = Pattern.compile("N+S*N+|N+A*");
    private static Pattern VerbPhrase = Pattern.compile("RV+|V+");

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

    @Override
    public List<ExtInterval> getNounAndVerbPhrases(String str, String[] parts) {
        QTDocument doc = new ESDocumentInfo("", str, this);
        return getNounAndVerbPhrases(doc, parts);
    }

    //https://github.com/slavpetrov/universal-pos-tags/blob/master/es-eagles.map
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(QTDocument doc, String [] parts) {

        String tokenized_title = doc.getTitle().trim();

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
            int s = m.start();
            int e = m.end();

            List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s , e));

            ExtInterval eit = StringUtil.findSpan(tokenized_title, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 1" + String.join(" ", tokenList) + "' in: " + tokenized_title);
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
            ExtInterval eit = StringUtil.findSpan(tokenized_title, tokenList);
            if (eit == null) {
                logger.error("NOT FOUND 2" + String.join(" ", tokenList) + "' in: " + tokenized_title);
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

    public static void main(String[] args) throws Exception
    {
        ESDocumentHelper helper = new ESDocumentHelper();
        String file = "data.txt";
        JsonParser parser = new JsonParser();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                JsonObject json = parser.parse(line).getAsJsonObject();
                String body = json.get("body").getAsString();
                String [] sents = helper.getSentences(body);
                String ttl = json.get("title").getAsString();
                logger.info(ttl);
                for (String str : sents) {
                    List<String> parts = helper.tokenize(str);
                    ESDocumentInfo sDoc = new ESDocumentInfo("", str, helper);
                    List<ExtInterval> tagged = helper.getNounAndVerbPhrases(sDoc, parts.toArray(new String[parts.size()]));
                }
            }
        }
    }

}
