package com.quantxt.doc.helper;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quantxt.doc.ENDocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.types.DateTimeTypeConverter;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.util.StringUtil;

/**
 * Created by dejani on 1/24/18.
 */

public class ENDocumentHelper extends CommonQTDocumentHelper {

    private static Logger logger = LoggerFactory.getLogger(ENDocumentHelper.class);

    private static final String SENTENCES_FILE_PATH = "/en/en-sent.bin";
    private static final String POS_FILE_PATH = "/en/en-pos-maxent.bin";
    private static final String STOPLIST_FILE_PATH = "/en/stoplist.txt";
    private static final String TOKENIZER_FILE_PATH = "/en/en-token.bin";
    private static final String VERB_FILE_PATH = "/en/context.json";
    private static final Set<String> PRONOUNS = new HashSet<>(
            Arrays.asList("he", "she", "He", "She"));

    private static Pattern NounPhrase = Pattern.compile("NJ+N|J+N+|N+");
    private static Pattern VerbPhrase = Pattern.compile("V+R+V+|V+");

    private Tokenizer tokenizer;

    public ENDocumentHelper() {
        super(SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, VERB_FILE_PATH, PRONOUNS, false);

    }

    public ENDocumentHelper(InputStream contextFile) {
        super(contextFile, SENTENCES_FILE_PATH, POS_FILE_PATH,
                STOPLIST_FILE_PATH, PRONOUNS, false);
    }


    protected boolean isTagDC(String tag) {
        return tag.equals("IN") || tag.equals("TO") || tag.equals("CC")
                || tag.equals("DT");
    }

    @Override
    public List<String> tokenize(String str) {
        String[] toks = tokenizer.tokenize(str.replaceAll("([”“])", " $1 "));
        return Arrays.asList(toks);
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
        try (FileInputStream fis = new FileInputStream(getModelBaseDir() + TOKENIZER_FILE_PATH)) {
            TokenizerModel tokenizermodel = new TokenizerModel(fis);
            tokenizer = new TokenizerME(tokenizermodel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //https://www.ling.upenn.edu/courses/Fall_2003/ling001/penn_treebank_pos.html
    @Override
    public List<ExtInterval> getNounAndVerbPhrases(final String orig_str,
                                                   String[] tokens) {
        String[] taags = getPosTags(tokens);
        StringBuilder allTags = new StringBuilder();
        ExtInterval [] tokenSpans = StringUtil.findAllSpans(orig_str, tokens);

        for (String t : taags) {
            allTags.append(t.substring(0, 1));
        }

        List<ExtInterval> intervals = new ArrayList<>();
        Matcher m = NounPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtInterval eit = new ExtInterval(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
    //        List<String> tokenList = Arrays.asList(Arrays.copyOfRange(parts, s, e));
    //        ExtInterval eit = StringUtil.findSpan(tokenized_title, tokenList);
    //        if (eit == null) {
    //            logger.error("NOT FOUND: '" + String.join(" ", tokenList) + "' in: " + orig_str);
    //        } else {
                eit.setType("N");
                intervals.add(eit);
    //        }
        }

        m = VerbPhrase.matcher(allTags.toString());
        while (m.find()) {
            int s = m.start();
            int e = m.end() - 1;
            ExtInterval eit = new ExtInterval(tokenSpans[s].getStart(), tokenSpans[e].getEnd());
            eit.setType("V");
            intervals.add(eit);
        }

        Collections.sort(intervals, new Comparator<ExtInterval>(){
            public int compare(ExtInterval p1, ExtInterval p2){
                Integer s1 = p1.getStart();
                Integer s2 = p2.getStart();
                return s1.compareTo(s2);
            }
        });

        return intervals;
    }

    public static void main(String[] args) throws Exception {
        File file = new File("o.txt");
        ENDocumentHelper helper = new ENDocumentHelper();
        //     ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        //       for (int i=0 ; i<10 ; i++) {
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        final Gson JODA_GSON = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter())
                .create();
        int n = 100;
        while ((line = bufferedReader.readLine()) != null) {
            if (n-- < 0) break;
            QTDocument p = JODA_GSON.fromJson(line, ENDocumentInfo.class);
            QTDocument parent = new ENDocumentInfo(p.getBody(), p.getTitle(), helper);
            parent.extractEntityMentions(null);
            //            List<String> toks = helper.tokenize(line);
            //         String [] parts = toks.toArray(new String[toks.size()]);
            //         executor.execute(new TagRun(helper, line, parts));
            //        String[] tags = helper.getPosTags(parts);
            //       helper.getNounAndVerbPhrases(line, parts);

            //            if (executor.getActiveCount() > 20){
            //                Thread.sleep(1000);
            //            }
            //        }
            //       logger.info(String.valueOf(i) + "...");


        }
        fileReader.close();
        //     executor.awaitTermination(120, TimeUnit.SECONDS);
        logger.info("Done");

    }

}
