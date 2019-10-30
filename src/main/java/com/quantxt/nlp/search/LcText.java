package com.quantxt.nlp.search;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.Extraction;
import com.quantxt.trie.Emit;
import com.quantxt.types.BaseNameAlts;

import static org.apache.lucene.analysis.CharArraySet.EMPTY_SET;


/**
 * Created by matin on 3/28/18.
 */
@Deprecated
public class LcText<T> {

    final private static Logger logger = LoggerFactory.getLogger(LcText.class);

    final private static String searchField = "searchField";

    final private static FieldType QFieldType;

    private IndexSearcher indexSearcher;

    private Analyzer analyzer;

    private Type customeType;
    private int topN = 3;
    private float thresh = 0;
    private ArrayList<BaseNameAlts<T>> customeData = new ArrayList<>();
    boolean orSearch = false;

    static {
        QFieldType = new FieldType();
        QFieldType.setStored(true);
        QFieldType.setStoreTermVectors(true);
        QFieldType.setStoreTermVectorOffsets(true);
        QFieldType.setStoreTermVectorPositions(true);
        QFieldType.setTokenized(true);
        QFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        QFieldType.freeze();
    }

    public LcText(Type type) {
        this.analyzer = new EnglishAnalyzer(EMPTY_SET);
        this.customeType = type;
    }

    public LcText(Type type, int t, float d) {
        this.analyzer = new EnglishAnalyzer(EMPTY_SET);
        this.customeType = type;
        this.thresh = d;
        this.topN = t;
    }


    public void setTopN(int n){
        topN = n;
    }

    public void setthresh(float n){
        thresh = n;
    }

    public void setOrSearch(boolean b){
        orSearch = b;
    }

    public void loadCategorical(InputStream ins,
                                Type customeType,
                                boolean includeCategory) {

            if (analyzer == null) {
                this.analyzer = new EnglishAnalyzer(EMPTY_SET);
            }
            try {
                Map<String, Analyzer> analyzerMap = new HashMap<>();
                analyzerMap.put(searchField, analyzer);

                PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

                IndexWriterConfig iwc = new IndexWriterConfig(pfaw);
                Directory ramDirectory = new RAMDirectory();
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                IndexWriter writer = new IndexWriter(ramDirectory, iwc);

            /*
            Directory ramDirectory = new RAMDirectory();
            IndexWriterConfig config = new IndexWriterConfig(index_analyzer);
            IndexWriter writer = new IndexWriter(ramDirectory, config);
            */

                byte[] contextArr = org.apache.poi.util.IOUtils.toByteArray(ins);
                String jsonString = new String(contextArr, "UTF-8");

                Gson gson = new Gson();
                BaseNameAlts<T>[] array = gson.fromJson(jsonString, customeType);
                for (BaseNameAlts<T> bna : array) {
                    String[] alts = bna.getAlts();
                    String name = bna.getName();
                    Document doc = includeCategory ? createDocument(name, alts) :
                            createDocument(null, alts);
                    writer.addDocument(doc);
                    customeData.add(bna);
                }
                writer.close();
                DirectoryReader dreader = DirectoryReader.open(ramDirectory);
                indexSearcher = new IndexSearcher(dreader);

            } catch (IOException e) {
                e.printStackTrace();
            }

            logger.debug("Categorical trie loaded");

    }

    private Document createDocument(String title,
                                    String [] content)
    {
        Document doc = new Document();

        if (title != null) {
            doc.add(new Field(searchField, title, QFieldType));
        }
        for (String s : content) {
            doc.add(new Field(searchField, s, QFieldType));
        }
        return doc;
    }

    private Query getMultimatcheQuery(Analyzer analyzer, String query, float fraction) {
        QueryParser qp = new QueryParser(searchField, analyzer);
        return qp.createMinShouldMatchQuery(searchField, query, fraction);
    }

    private Query getPhraseQuery(Analyzer analyzer, String query, int slop) {
        QueryParser qp = new QueryParser(searchField, analyzer);
        Query q = qp.createPhraseQuery(searchField, query, slop);
        return q;
    }

    private int getNumTokens(Analyzer analyzer, String str){
        TokenStream ts = analyzer.tokenStream(searchField, str);
        CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
        int num = 0;
        try {
            ts.reset();

            while (ts.incrementToken()) {
                num++;
            }
            ts.end();
            ts.close();
        } catch (Exception e){

        }
        return num;
    }

    public <T> Extraction extract(String str){
        Extraction res = new Extraction();
        if (str == null || str.isEmpty()) return res;
        str = QueryParser.escape(str);

        try{

            Query query = getPhraseQuery(analyzer, str, 0);
            TopDocs topcdocs = indexSearcher.search(query, topN);
            if (topcdocs.totalHits.value == 0){
                //try phrase but with slope 1
                query = getPhraseQuery(analyzer, str, 1);
                topcdocs = indexSearcher.search(query, topN);
                if (topcdocs.totalHits.value == 0){
                    query = getMultimatcheQuery(analyzer, str, thresh);
                    topcdocs = indexSearcher.search(query, topN);
                    if (topcdocs.totalHits.value == 0){
                        return res;
                    }
                }
            }

            ArrayList<BaseNameAlts> bna = new ArrayList<>();
            for(ScoreDoc hit :topcdocs.scoreDocs)
            {
                int id = hit.doc;
                double score = hit.score;
                BaseNameAlts<T> basenamealt = (BaseNameAlts<T>) customeData.get(id);
                basenamealt.setScore(score);
                bna.add(basenamealt);
            }

            if (!bna.isEmpty()) {
                Emit emit = new Emit(0, 0, str, bna);
                res.add(Extraction.Extractions.PHRASE, emit);
            }
        } catch (Exception e){

        }

        return res;
    }
}
