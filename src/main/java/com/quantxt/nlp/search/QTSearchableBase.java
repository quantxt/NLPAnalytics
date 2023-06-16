package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.model.Dictionary;
import com.quantxt.model.DictItm;
import com.quantxt.model.DictSearch;
import com.quantxt.types.MapSort;
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
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.*;

import static com.quantxt.model.DictSearch.AnalyzType.STANDARD;
import static com.quantxt.model.DictSearch.Mode.ORDERED_SPAN;
import static com.quantxt.nlp.search.DctSearhFld.*;
import static com.quantxt.nlp.search.SearchUtils.*;

@Deprecated
public class QTSearchableBase<T> extends DictSearch implements Serializable  {

    private static final long serialVersionUID = -2557457339416308514L;

    final private static Logger logger = LoggerFactory.getLogger(QTSearchableBase.class);

    protected int topN = 2000;
    protected int minFuzzyTermLength = 5;

    protected transient IndexSearcher indexSearcher;
    protected String index_path;
    protected QTDocumentHelper.Language lang = QTDocumentHelper.Language.ENGLISH;

    protected List<DctSearhFld> docSearchFldList = new ArrayList<>();
    protected List<String> synonymPairs;
    protected List<String> stopWords;

    public QTSearchableBase(Dictionary dictionary) {
        this.synonymPairs = null;
        this.mode = new DictSearch.Mode[]{ORDERED_SPAN};
        this.analyzType = new DictSearch.AnalyzType[]{STANDARD};
        this.dictionary = dictionary;
        this.stopWords = null;
        this.index_path = null;
        initDocSearchFld();
    }

    public QTSearchableBase(Dictionary dictionary,
                            String index_path,
                            QTDocumentHelper.Language lang,
                            List<String> synonymPairs,
                            List<String> stopWords,
                            DictSearch.Mode mode,
                            DictSearch.AnalyzType analyzType) {
        this.lang = lang;
        this.index_path = index_path;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = new DictSearch.Mode[]{mode};
        this.analyzType = new DictSearch.AnalyzType[]{analyzType};
        this.dictionary = dictionary;
        initDocSearchFld();
    }

    public QTSearchableBase(Dictionary dictionary,
                            String index_path,
                            QTDocumentHelper.Language lang,
                            List<String> synonymPairs,
                            List<String> stopWords,
                            DictSearch.Mode[] mode,
                            DictSearch.AnalyzType[] analyzType) {
        this.lang = lang;
        this.index_path = index_path;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = mode;
        this.analyzType = analyzType;
        this.dictionary = dictionary;
        initDocSearchFld();
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

    public QTSearchableBase load() {
        if (index_path == null){
            logger.error("Path to Index must be set");
            return null;
        }
        initDocSearchFld();
        try {
            Directory mMapDirectory = new MMapDirectory(Paths.get(index_path));
            DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
            indexSearcher = new IndexSearcher(dreader);
        } catch (Exception e){
            e.printStackTrace();
        }
        return this;
    }

    public void purge() throws IOException {
        Map<String, Analyzer> analyzerMap = new HashMap<>();

        for (DctSearhFld dctSearhFld : docSearchFldList) {
            analyzerMap.put(dctSearhFld.getSearch_fld(), dctSearhFld.getIndex_analyzer());
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

        IndexWriter writer = new IndexWriter(mMapDirectory, config);
        writer.deleteAll();
        writer.close();
    }


    public QTSearchableBase create() {
        Map<String, Analyzer> analyzerMap = new HashMap<>();

        for (DctSearhFld dctSearhFld : docSearchFldList) {
            analyzerMap.put(dctSearhFld.getSearch_fld(), dctSearhFld.getIndex_analyzer());
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

            for (DictItm dictItm : dictionary.getVocab()) {
                String category = dictItm.getCategory();
                List<String> phraseList = dictItm.getPhraseList();
                for (String value : phraseList) {
                    Document doc = new Document();
                    doc.add(new Field(DataField, category, DataFieldType));
                    for (DctSearhFld dctSearhFld : docSearchFldList) {
                        Field field = new Field(dctSearhFld.getSearch_fld(), value, SearchFieldType);
                        doc.add(field);
                    }
                    writer.addDocument(doc);
                }
            }
            writer.close();
            DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
            indexSearcher = new IndexSearcher(dreader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    public  Map<String, Map<String, Long>> getTermCoverage(String str)
    {
        Map<String, Map<String, Long>> stats = new HashMap<>();

        for (DctSearhFld dctSearhFld : docSearchFldList){
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

        return stats;
    }

    public void initDocSearchFld() {
        try {
            String filednamePfx = dictionary.getName().toLowerCase().replaceAll("[^a-z0-9_]", "");
            docSearchFldList = new ArrayList<>();
            for (AnalyzType at : analyzType) {
                for (Mode m : mode) {
                    DctSearhFld dctSearhFld = new DctSearhFld(lang, synonymPairs, stopWords,
                            m, at, filednamePfx);
                    docSearchFldList.add(dctSearhFld);
                }
            }
            docSearchFldList.sort((DctSearhFld s1, DctSearhFld s2) -> s2.getPriority() - s1.getPriority());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<T> search(final String query_string) {
        return null;
    }

    @Override
    public List<T> search(final String query_string, int slop) {
        return null;
    }

    @Override
    public List postSearch(boolean hasTextboxes) {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public Collection search(String query_string, Map lineTextBoxMap, int slop, boolean isolatedLabelsOnly) {
        return null;
    }

    public IndexSearcher getIndexSearcher() {
        return indexSearcher;
    }

    public int getMinFuzzyTermLength() {
        return minFuzzyTermLength;
    }

    public int getTopN() {
        return topN;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public QTDocumentHelper.Language getLang() {
        return lang;
    }

    public void setIndexSearcher(IndexSearcher indexSearcher) {
        this.indexSearcher = indexSearcher;
    }

    public void setMinFuzzyTermLength(int minFuzzyTermLength) {
        this.minFuzzyTermLength = minFuzzyTermLength;
    }

    public List<DctSearhFld> getDocSearchFldList() {
        return docSearchFldList;
    }

    public List<String> getStopWords() {
        return stopWords;
    }

    public List<String> getSynonymPairs() {
        return synonymPairs;
    }

    public String getIndex_path() {
        return index_path;
    }

    public void setDocSearchFldList(List<DctSearhFld> docSearchFldList) {
        this.docSearchFldList = docSearchFldList;
    }

    public void setIndex_path(String index_path) {
        this.index_path = index_path;
    }

    public void setLang(QTDocumentHelper.Language lang) {
        this.lang = lang;
    }

    public void setStopWords(List<String> stopWords) {
        this.stopWords = stopWords;
    }

    public void setSynonymPairs(List<String> synonymPairs) {
        this.synonymPairs = synonymPairs;
    }
}