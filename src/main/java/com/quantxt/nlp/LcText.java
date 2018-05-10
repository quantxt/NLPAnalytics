package com.quantxt.nlp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.helper.types.Extraction;
import com.quantxt.trie.Emit;
import com.quantxt.types.BaseNameAlts;


/**
 * Created by matin on 3/28/18.
 */
public class LcText<T> {

    final private static Logger logger = LoggerFactory.getLogger(LcText.class);

    final private static String English  = "english";
    final private static String Standard = "standard";
    final private static String Keyword  = "keyword";
    final private static Analyzer EnglishAnalyzer = new EnglishAnalyzer();
    final private static Analyzer StandardAnalyzer = new StandardAnalyzer();
    final private static Gson gson = new Gson();
    final private static FieldType QFieldType;

    private IndexSearcher indexSearcher;
    private Type customeType;
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
        this.customeType = type;
    }

    public void setOrSearch(boolean b){
        orSearch = b;
    }

    public void loadCategorical(InputStream ins,
                                Type customeType,
                                boolean includeCategory) {
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
                writer.addDocument(doc);
                customeData.add(bna);
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
        doc.add(new Field(English, body, QFieldType));
        doc.add(new Field(Standard, body, QFieldType));

        //      doc.add(new TextField(English, body, Field.Store.NO));
        //      doc.add(new TextField(Standard, body, Field.Store.NO));
        for (String s : content){
            doc.add(new Field(Keyword, s, QFieldType));
            //          doc.add(new TextField(Keyword, s, Field.Store.NO));
        }

        return doc;
    }

    private TopDocs getPhrase(String str,
                              int num) throws IOException {

        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        bqueryBuilder.add(new BooleanClause(new PhraseQuery(2, Standard, new BytesRef(str)), BooleanClause.Occur.SHOULD));
        bqueryBuilder.add(new BooleanClause(new PhraseQuery(2, English,  new BytesRef(str)), BooleanClause.Occur.SHOULD));
        bqueryBuilder.add(new BooleanClause(new PhraseQuery(2, Keyword,  new BytesRef(str)), BooleanClause.Occur.SHOULD));

        return indexSearcher.search(bqueryBuilder.build(), num);
    }

    private TopDocs gethists(String str,
                             int num,
                             QueryParser.Operator op) throws IOException
    {
        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        bqueryBuilder.add(new BooleanClause(new TermQuery(new Term(Keyword,str)), BooleanClause.Occur.SHOULD));

        QueryParser qp_1 = new QueryParser(Standard, StandardAnalyzer);
        QueryParser qp_2 = new QueryParser(English, EnglishAnalyzer);

        qp_1.setDefaultOperator(op);
        qp_2.setDefaultOperator(op);

        try {
            bqueryBuilder.add(new BooleanClause(qp_1.parse(str), BooleanClause.Occur.SHOULD));
            bqueryBuilder.add(new BooleanClause(qp_2.parse(str), BooleanClause.Occur.SHOULD));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return indexSearcher.search(bqueryBuilder.build(), num);
    }

    public <T> Extraction extract(String str){
        Extraction res = new Extraction();
        if (str == null || str.isEmpty()) return res;
        ArrayList<BaseNameAlts> bna = new ArrayList<>();

        //hack this doesn't work
        // Type customeType = new TypeToken<BaseNameAlts<T>>() {}.getType();
        // Type customeType = new TypeToken<BaseNameAlts<T>>() {}.getType();

        //escape the query
        str = QueryParser.escape(str);

        try {
            //    TopDocs hits = getPhrase(str, 4);
            //    if (hits.totalHits == 0 ) {
            TopDocs hits = gethists(str, 4, QueryParser.Operator.AND);
            //    }
            if (orSearch && hits.totalHits == 0){
                hits = gethists(str, 4, QueryParser.Operator.OR);
            }

            ScoreDoc[] scorehits = hits.scoreDocs;

            float topScore = hits.getMaxScore();
            for (ScoreDoc hit : scorehits){
                int id = hit.doc;
                float score = hit.score;

                if (score / topScore < .8){
                    break;
                }
                //     Fields fields = indexSearcher.getIndexReader().getTermVectors(id);
                //     Terms terms = fields.terms(Standard);

                //     TermsEnum iter = terms.iterator();
                //    iter.postings(0);

                /*

                TermFreqVector tfvector = indexSearcher.getIndexReader().document(id);
                TermPositionVector tpvector = (TermPositionVector)tfvector;
                // this part works only if there is one term in the query string,
                // otherwise you will have to iterate this section over the query terms.
                int termidx = tfvector.indexOf(querystr);
                int[] termposx = tpvector.getTermPositions(termidx);
                TermVectorOffsetInfo[] tvoffsetinfo = tpvector.getOffsets(termidx);

                for (int j=0;j<termposx.length;j++) {
                    System.out.println("termpos : "+termposx[j]);
                }
                for (int j=0;j<tvoffsetinfo.length;j++) {
                    int offsetStart = tvoffsetinfo[j].getStartOffset();
                    int offsetEnd = tvoffsetinfo[j].getEndOffset();
                    System.out.println("offsets : "+offsetStart+" "+offsetEnd);
                }
                */

                BaseNameAlts<T> data = (BaseNameAlts<T>) customeData.get(id);
                bna.add(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!bna.isEmpty()) {
            Emit emit = new Emit(0, 0, str, bna);
            res.add(Extraction.Extractions.PHRASE, emit);
        }
        return res;
    }


    public static void main(String[] args) throws Exception {

        String search_query = "Fire Resistive";
        InputStream ins = new FileInputStream(new File("test"));

        JsonParser parser = new JsonParser();
        JsonObject sov = null;
        try
        {
            sov = parser.parse(new InputStreamReader(ins, "UTF-8")).getAsJsonObject();
        } catch (Exception e){
            logger.error("SOVMap parsing error " + e.getMessage());
        }

        Map<String, ArrayList<String>> cat2vars = new HashMap<>();
        for (Map.Entry<String, JsonElement> e: sov.entrySet()){
            String text = e.getKey();
            String category = e.getValue().toString();
            ArrayList<String> vars = cat2vars.get(category);
            if (vars == null){
                cat2vars.put(category, new ArrayList<>());
                vars = cat2vars.get(category);
            }
            vars.add(text);
        }

        JsonArray bnas = new JsonArray();
        Gson gson = new Gson();
        for (Map.Entry<String, ArrayList<String>> e: cat2vars.entrySet()) {
            String [] alts = e.getValue().toArray(new String[e.getValue().size()]);
            JsonObject bna = new JsonObject();
            bna.addProperty("name" , e.getKey());
            bna.add("alts" , gson.toJsonTree(alts));
            bna.addProperty("data" , e.getKey());
            bnas.add(bna);
        }
        LcText lctext = new LcText(String.class);
        lctext.setOrSearch(true);

        Type ct = new TypeToken<BaseNameAlts<String>[]>(){}.getType();
        try {
            InputStream is = new ByteArrayInputStream(bnas.toString().getBytes());
            lctext.loadCategorical(is, ct, false);

        } catch (Exception e){
            logger.error("ERROR is creating the object... " + e.getMessage());
        }

        Extraction extraction = lctext.extract(search_query);
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
