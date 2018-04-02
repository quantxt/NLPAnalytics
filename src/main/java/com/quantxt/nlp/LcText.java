package com.quantxt.nlp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.quantxt.helper.types.Extraction;
import com.quantxt.nlp.types.SOVAttributes;
import com.quantxt.trie.Emit;
import com.quantxt.types.BaseNameAlts;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.document.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;


/**
 * Created by matin on 3/28/18.
 */
public class LcText {

    final private static Logger logger = LoggerFactory.getLogger(LcText.class);

    private static String English  = "english";
    private static String Standard = "standard";
    private static String Keyword  = "keyword";
    private static Analyzer EnglishAnalyzer = new EnglishAnalyzer();
    private static Analyzer StandardAnalyzer = new StandardAnalyzer();
    private static Gson gson = new Gson();

    private IndexSearcher indexSearcher;
    private final Type type;
    HashMap<Long, Object> customeData = new HashMap<>();

    public LcText(Type type){
        this.type = type;
    }

    public <T> void loadCategorical(InputStream ins,
                                    Class<T> dataClass,
                                    Type customeType,
                                    boolean includeCategory)
    {

        try {
            Map<String, Analyzer> analyzerMap = new HashMap<>();
            analyzerMap.put(Standard, StandardAnalyzer);
            analyzerMap.put(English, EnglishAnalyzer);
            PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

            IndexWriterConfig iwc = new IndexWriterConfig(pfaw);
            Directory ramDirectory = new RAMDirectory();
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            IndexWriter writer = new IndexWriter(ramDirectory, iwc);

            byte[] contextArr = IOUtils.toByteArray(ins);
            String jsonString = new String(contextArr, "UTF-8");

            BaseNameAlts<T>[] array = gson.fromJson(jsonString, customeType);
            for (BaseNameAlts<T> bna : array) {
                String [] alts = bna.getAlts();
                String name = bna.getName();
                Document doc = includeCategory ? createDocument(name, alts):
                        createDocument(null, alts);
                long id = writer.addDocument(doc);
                customeData.put(id-1, bna);
            }
            writer.close();
            DirectoryReader dreader = DirectoryReader.open(ramDirectory);
            indexSearcher = new IndexSearcher(dreader);

        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("Categorical trie loaded");
    }

    private Document createDocument(String title,
                                    String [] content)
    {
        Document doc = new Document();

        if (title != null) {
            // not tokenized
            doc.add(new StringField("title", title, Field.Store.YES));
        }
        String body = String.join("\n" , content);
        doc.add(new TextField(English, body, Field.Store.NO));
        doc.add(new TextField(Standard, body, Field.Store.NO));
        for (String s : content){
            doc.add(new TextField(Keyword, s, Field.Store.NO));
        }

        return doc;
    }

    public <T> Extraction extract(String str){
        Extraction res = new Extraction();

        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        bqueryBuilder.add(new BooleanClause(new TermQuery(new Term(Keyword,str)), BooleanClause.Occur.SHOULD));

        QueryParser qp_1 = new QueryParser(Standard, StandardAnalyzer);
        QueryParser qp_2 = new QueryParser(English, EnglishAnalyzer);
        qp_1.setDefaultOperator(QueryParser.AND_OPERATOR);
        qp_2.setDefaultOperator(QueryParser.AND_OPERATOR);

        try {
            bqueryBuilder.add(new BooleanClause(qp_1.parse(str), BooleanClause.Occur.SHOULD));
            bqueryBuilder.add(new BooleanClause(qp_2.parse(str), BooleanClause.Occur.SHOULD));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        ArrayList<BaseNameAlts> bna = new ArrayList<>();

        //hack this doesn't work
        // Type customeType = new TypeToken<BaseNameAlts<T>>() {}.getType();
        // Type customeType = new TypeToken<BaseNameAlts<T>>() {}.getType();

        try {
            TopDocs hits = indexSearcher.search(bqueryBuilder.build(), 4);
            float topScore = hits.getMaxScore();
            for (ScoreDoc hit : hits.scoreDocs){
                long id = hit.doc;
                float score = hit.score;
                if (score / topScore < .8){
                    break;
                }
                BaseNameAlts<T> data = (BaseNameAlts<T>) customeData.get(id);
                bna.add(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Emit emit = new Emit(0, 0, str, bna);
        res.add(Extraction.Extractions.PHRASE, emit);

        return res;
    }

    public static void main(String[] args) throws Exception {

        String search_query = "Description";
        InputStream ins = new FileInputStream(new File("test.json"));

        JsonParser parser = new JsonParser();
        JsonObject sov = null;
        try
        {
            sov = parser.parse(new InputStreamReader(ins, "UTF-8")).getAsJsonObject();
        } catch (Exception e){
            logger.error("SOVMap parsing error " + e.getMessage());
        }

        String str_arr = sov.get("Buildings").getAsJsonArray().toString();

        InputStream is = new ByteArrayInputStream(str_arr.getBytes());

        LcText extract = new LcText(new TypeToken<BaseNameAlts<SOVAttributes>>() {}.getType());

        Type customeType = new TypeToken<BaseNameAlts<SOVAttributes>[]>() {}.getType();
        extract.loadCategorical(is, SOVAttributes.class, customeType, true);
        Extraction extraction = extract.extract(search_query);
        Map<Extraction.Extractions, List<Emit>> emitMap = extraction.getExtractions();
        List<Emit> emets = emitMap.get(Extraction.Extractions.PHRASE);

        if (emets != null ) {
            for (Emit e : emets) {
                //exact match
                ArrayList<BaseNameAlts> cdatas = (ArrayList<BaseNameAlts>) e.getCustomeData();
                for (BaseNameAlts cdata : cdatas) {
                    String name = cdata.getName();
                    logger.info(name);
                }
            }
        }
    }
}
