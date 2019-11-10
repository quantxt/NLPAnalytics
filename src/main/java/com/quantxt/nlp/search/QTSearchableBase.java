package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import com.quantxt.types.MapSort;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.quantxt.nlp.search.DctSearhFld.*;
import static com.quantxt.nlp.search.SearchUtils.*;
import static com.quantxt.types.DictSearch.AnalyzType.STANDARD;
import static com.quantxt.types.DictSearch.Mode.ORDERED_SPAN;

@Getter
@Setter
public class QTSearchableBase<T> extends DictSearch {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchableBase.class);
    final public static String HIDDEH_ENTITY = "hidden";

    protected int topN = 100;
    protected int minFuzzyTermLength = 5;

    protected transient IndexSearcher indexSearcher;
    protected String index_path;

    protected Map<String, List<DctSearhFld>> docSearchFldMap = new HashMap<>();
    final protected QTDocument.Language lang;
    final protected List<String> synonymPairs;
    final protected List<String> stopWords;


    public QTSearchableBase(Dictionary dictionary) {
        this.lang = null;
        this.synonymPairs = null;
        this.mode = new DictSearch.Mode[]{ORDERED_SPAN};
        this.analyzType = new DictSearch.AnalyzType[]{STANDARD};
        this.dictionary = dictionary;
        this.stopWords = null;
        init();
    }

    public QTSearchableBase(Dictionary dictionary,
                        QTDocument.Language lang,
                        List<String> synonymPairs,
                        List<String> stopWords,
                        DictSearch.Mode mode,
                        DictSearch.AnalyzType analyzType) {
        this.lang = lang;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = new DictSearch.Mode[]{mode};
        this.analyzType = new DictSearch.AnalyzType[]{analyzType};
        this.dictionary = dictionary;
        init();
    }

    public QTSearchableBase(Dictionary dictionary,
                        QTDocument.Language lang,
                        List<String> synonymPairs,
                        List<String> stopWords,
                        DictSearch.Mode[] mode,
                        DictSearch.AnalyzType[] analyzType) {
        this.lang = lang;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = mode;
        this.analyzType = analyzType;
        this.dictionary = dictionary;
        init();
    }

    protected void init(){
        initDocSearchFldMap();
        createIndex();
    }

    public List<Document> getMatchedDocs(Query query) throws IOException {
        List<Document> matchedDocs = new ArrayList<>();
        TopDocs topdocs = indexSearcher.search(query, topN);

        if (topdocs.totalHits.value == 0) return matchedDocs;

        for (ScoreDoc hit : topdocs.scoreDocs) {
            int id = hit.doc;
            Document doclookedup = indexSearcher.doc(id);
            matchedDocs.add(doclookedup);
        }

        return matchedDocs;
    }

    public Map<String, Integer> getWfl(int min_freq,
                                       String[] flds) {
        Map<String, Integer> map = new HashMap<>();
        Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
        if (flds == null) return map;
        IndexReader reader = indexSearcher.getIndexReader();
        try {
            for (int i = 0; i < reader.maxDoc(); i++) {

                Document doc = reader.document(i);
                for (String fld : flds) {
                    String val = doc.get(fld);
                    if (val == null) continue;
                    String[] tokens = tokenize(analyzer, val);
                    for (String t : tokens) {
                        Integer count = map.get(t);
                        if (count == null) {
                            count = 0;
                        }
                        map.put(t, count + 1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Integer> sorted_map = new HashMap<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() < min_freq) continue;
            sorted_map.put(e.getKey(), e.getValue());
        }

        return MapSort.sortdescByValue(sorted_map);
    }

    private void createIndex() {
        Map<String, Analyzer> analyzerMap = new HashMap<>();

        for (Map.Entry<String, List<DctSearhFld>> e : docSearchFldMap.entrySet()) {
            for (DctSearhFld dctSearhFld : e.getValue()) {
                analyzerMap.put(dctSearhFld.getSearch_fld(), dctSearhFld.getIndex_analyzer());
            }
        }

        PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);
        Directory mMapDirectory = null;

        IndexWriterConfig config = new IndexWriterConfig(pfaw);
        try {
            mMapDirectory = index_path == null ? new ByteBuffersDirectory() :
                    new MMapDirectory(Paths.get(index_path));
        } catch (Exception e){
            e.printStackTrace();
        }

        try {
            IndexWriter writer = new IndexWriter(mMapDirectory, config);

            for (Map.Entry<String, List<DictItm>> e : dictionary.getVocab_map().entrySet()) {
                String vocab_name = e.getKey();
                List<DctSearhFld> dctSearhFldList = docSearchFldMap.get(vocab_name);
                List<DictItm> item_vals = e.getValue();

                for (DictItm dictItm : item_vals) {
                    String item_key = dictItm.getKey();
                    List<String> values = dictItm.getValue();
                    for (String value : values) {
                        Document doc = new Document();
                        doc.add(new Field(DataField, item_key, DataFieldType));
                        for (DctSearhFld dctSearhFld : dctSearhFldList) {
                            doc.add(new Field(dctSearhFld.getSearch_fld(), value, SearchFieldType));
                        }
                        writer.addDocument(doc);
                    }
                }
            }

            writer.close();
            DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
            indexSearcher = new IndexSearcher(dreader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public  Map<String, Map<String, Long>> getTermCoverage(String str)
    {
        Map<String, Map<String, Long>> stats = new HashMap<>();

        for (Map.Entry<String, List<DctSearhFld>> e : docSearchFldMap.entrySet()){
            for (DctSearhFld dctSearhFld : e.getValue()){
                Map<String, Long> fld_stats = new HashMap<>();
                Analyzer analyzer = dctSearhFld.getSearch_analyzer();
                String sch_fld = dctSearhFld.getSearch_fld();
                try {
                    String[] tokens = tokenize(analyzer, str);
                    for (String t : tokens) {
                        long freq = indexSearcher.getIndexReader().docFreq(new Term(sch_fld, t));
                        Long l = fld_stats.get(t);
                        if (l == null){
                            l = 0L;
                        }
                        fld_stats.put(t, l + freq);
                    }
                } catch (Exception ee){
                    ee.printStackTrace();
                }
                stats.put(sch_fld, fld_stats);
            }
        }

        return stats;
    }

    private void initDocSearchFldMap() {
        try {
            for (Map.Entry<String, List<DictItm>> vocab : dictionary.getVocab_map().entrySet()) {
                String vocab_name = vocab.getKey();
                String vocab_name_conv = vocab_name.toLowerCase().replaceAll("[^a-z]", "");
                List<DctSearhFld> dctSearhFlds = new ArrayList<>();
                for (AnalyzType at : analyzType) {
                    for (Mode m : mode) {
                        DctSearhFld dctSearhFld = new DctSearhFld(lang, synonymPairs, stopWords,
                                m, at, vocab_name_conv);
                        dctSearhFlds.add(dctSearhFld);
                    }
                }
                dctSearhFlds.sort((DctSearhFld s1, DctSearhFld s2) -> s1.getPriority() - s2.getPriority());
                docSearchFldMap.put(vocab_name, dctSearhFlds);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<T> search(final String query_string) {
        return null;
    }
}