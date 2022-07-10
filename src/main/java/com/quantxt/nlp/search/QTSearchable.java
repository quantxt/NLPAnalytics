package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.model.DictItm;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.DictSearch;
import com.quantxt.model.Dictionary;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
import com.quantxt.types.QSpan;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static com.quantxt.doc.helper.textbox.TextBox.*;
import static com.quantxt.model.DictItm.DONT_CARE;
import static com.quantxt.model.DictSearch.AnalyzType.STANDARD;
import static com.quantxt.model.DictSearch.Mode.*;
import static com.quantxt.nlp.search.DctSearhFld.*;
import static com.quantxt.nlp.search.SearchUtils.*;
import static java.util.Comparator.comparingInt;

public class QTSearchable extends DictSearch<ExtInterval, QSpan> implements Serializable {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);

    protected transient IndexSearcher indexSearcher;

    protected List<DctSearhFld> docSearchFldList = new ArrayList<>();
    protected List<String> synonymPairs;
    protected List<String> stopWords;
    protected QTDocumentHelper.Language lang = QTDocumentHelper.Language.ENGLISH;

    protected int topN = 2000;
    private int minTermLength = 2;
    private int maxEdits = 1;
    private int prefixLength = 1;

    public QTSearchable(Dictionary dictionary) {
        this.synonymPairs = null;
        this.mode = new DictSearch.Mode[]{ORDERED_SPAN};
        this.analyzType = new DictSearch.AnalyzType[]{STANDARD};
        this.dictionary = dictionary;
        this.stopWords = null;
        initDocSearchFld();
        create();
    }

    public QTSearchable(Dictionary dictionary,
                        QTDocumentHelper.Language language,
                        List<String> synonymPairs,
                        List<String> stopWords,
                        DictSearch.Mode mode,
                        DictSearch.AnalyzType analyzType) {
        this.lang = language;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = new DictSearch.Mode[]{mode};
        this.analyzType = new DictSearch.AnalyzType[]{analyzType};
        this.dictionary = dictionary;
        initDocSearchFld();
        create();
    }

    public QTSearchable(Dictionary dictionary,
                        QTDocumentHelper.Language lang,
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
        initDocSearchFld();
        create();
    }

    private boolean useFuzzyMatch() {
        boolean useFuzzyMatching = false;
        for (Mode m : mode) {
            if (m == Mode.FUZZY_SPAN || m == Mode.FUZZY_ORDERED_SPAN
                    || m == Mode.PARTIAL_FUZZY_SPAN) {
                useFuzzyMatching = true;
                break;
            }
        }
        return useFuzzyMatching;
    }

    @Override
    public List<ExtInterval> search(final String content, int slop) {
        ArrayList<ExtInterval> res = new ArrayList<>();
        boolean useFuzzyMatching = useFuzzyMatch();

        try {
            // This list is ordered by priorities
            // so if we find an entry that is matched with STANDARD analysis we won't consider it using STEM analysis
            String vocab_name = dictionary.getName();
            String vocab_id = dictionary.getId();
            for (DctSearhFld dctSearhFld : docSearchFldList) {
                if (res.size() > 0) break;
                String search_fld = dctSearhFld.getSearch_fld();
                Analyzer searchAnalyzer = dctSearhFld.getSearch_analyzer();

                Query query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, content,
                        minTermLength, maxEdits, prefixLength) :
                        getMultimatcheQuery(searchAnalyzer, search_fld, content);

                List<Document> matchedDocs = getMatchedDocs(query);
                if (matchedDocs.size() == 0) continue;
                for (Mode m : mode) {
                    List<ExtIntervalTextBox> matches = getFragments(matchedDocs, m, true, slop,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, content, null);
                    if (matches.size() > 0) {
                        for (ExtIntervalTextBox eitb : matches) {
                            res.add(eitb.getExtInterval());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in name search {}: content '{}'", e.getMessage(), content);
        }

        return res;
    }

    private List<Document> getMatchedDocs(Query query) throws IOException {
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

    @Override
    public List<ExtInterval> search(final String query_string) {
        return search(query_string, 1);
    }

    @Override
    public List<QSpan> search(String content,
                              Map<Integer, BaseTextBox> lineTextBoxMap,
                              int slop) {
        ArrayList<ExtIntervalTextBox> res = new ArrayList<>();
        boolean useFuzzyMatching = useFuzzyMatch();
        List<QSpan> spans = new ArrayList<>();

        try {
            // This list is ordered by priorities
            // so if we find an entry that is matched with STANDARD analysis we won't consider it using STEM analysis
            String vocab_name = dictionary.getName();
            String vocab_id = dictionary.getId();
            for (DctSearhFld dctSearhFld : docSearchFldList) {
                if (res.size() > 0) break;
                String search_fld = dctSearhFld.getSearch_fld();
                Analyzer searchAnalyzer = dctSearhFld.getSearch_analyzer();

                Query doc_query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, content,
                        minTermLength, maxEdits, prefixLength) :
                        getMultimatcheQuery(searchAnalyzer, search_fld, content);

                List<Document> matchedDocs = getMatchedDocs(doc_query);
                // run once with slop = 0


                for (Mode m : mode) {
                    List<ExtIntervalTextBox> matches = getFragments(matchedDocs, m, true, slop,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, content, lineTextBoxMap);

                    for (int i = 0; i < matches.size(); i++) {
                        QSpan qs = new QSpan(matches.get(i));
                        spans.add(qs);
                    }

                    if (lineTextBoxMap != null) {
                        HashSet<String> cur_matches = new HashSet<>();
                        for (ExtIntervalTextBox etb : matches){
                            String key = etb.getExtInterval().getStart()+"_"+etb.getExtInterval().getEnd();
                            cur_matches.add(key);
                        }

                        List<ExtIntervalTextBox> singletokenMatches = getFragments(matchedDocs, PARTIAL_ORDERED_SPAN,  false,20,
                                searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                search_fld, vocab_name, vocab_id, content, lineTextBoxMap);

                        singletokenMatches.sort(comparingInt((ExtIntervalTextBox s) -> s.getExtInterval().getStart()));
                        List<String[]> matchedDocumentTokens = new ArrayList<>();

                        for (Document d : matchedDocs){
                            String [] tokens = tokenize(searchAnalyzer, d.getField(search_fld).stringValue());
                            if (tokens.length == 1) continue;
                            matchedDocumentTokens.add(tokens);
                        }


                        for (String[] tokens : matchedDocumentTokens){
                            Map<String, List<Integer>> tokenIndex = new HashMap<>();

                            for (int i=0; i<tokens.length; i++) {
                                String t = tokens[i];
                                List<Integer> list = tokenIndex.get(t);
                                if (list == null) {
                                    list = new ArrayList<>();
                                    tokenIndex.put(t, list);
                                }
                                list.add(i);
                            }

                            Map<Integer, List<Integer>> tokenIdx2MatchIdx = new HashMap<>();
                            Map<Integer, List<Integer>> relations = new HashMap<>();
                            for (int i=0; i<singletokenMatches.size(); i++) {
                                ExtIntervalTextBox eitb = singletokenMatches.get(i);
                                String singleTokenEitb = eitb.getExtInterval().getStr();
                                String [] sTokens = tokenize(searchAnalyzer, singleTokenEitb);
                                if (sTokens == null || sTokens.length == 0 || sTokens[0].isEmpty()) continue;
                                String singleToken = sTokens[0];
                                List<Integer> tokenIdxs = tokenIndex.get(singleToken);
                                if (tokenIdxs == null) continue;
                                for (Integer tm : tokenIdxs) {
                                    List<Integer> rels = relations.get(i);
                                    if (rels == null) {
                                        rels = new ArrayList<>();
                                        relations.put(i, rels);
                                    }
                                    rels.add(tm);

                                    List<Integer> matchIdxs = tokenIdx2MatchIdx.get(tm);
                                    if (matchIdxs == null){
                                        matchIdxs = new ArrayList<>();
                                        tokenIdx2MatchIdx.put(tm, matchIdxs);
                                    }
                                    matchIdxs.add(i);
                                }

                            }
                            //   tokens      [0 1 2 3]
                            //   tokenCounts [3 2 3 4]
                            //   comb = 3*2*3*4
                            //
                            int total_combinations = 1;
                            for (Map.Entry<Integer, List<Integer>> e : tokenIdx2MatchIdx.entrySet()){
                                total_combinations *= e.getValue().size();
                            }
                            int [][] sequences = new int[total_combinations][tokens.length];

                            boolean notAllTokensFound = false;
                            int blockSize = total_combinations;
                            for (int i = 0; i<tokens.length; i++){
                                List<Integer> matchIdxs = tokenIdx2MatchIdx.get(i);
                                if (matchIdxs == null || matchIdxs.size() == 0){
                                    notAllTokensFound = true;
                                    break;
                                }
                                blockSize /= matchIdxs.size();
                                for (int j = 0; j<total_combinations; j++){
                                    int idx = j/blockSize;
                                    int token_idx = matchIdxs.get(idx % matchIdxs.size());
                                    sequences[j][i] = token_idx;
                                }
                            }

                            if (notAllTokensFound) continue;

                            for (int [] seq : sequences){
                                ExtIntervalTextBox prev = singletokenMatches.get(seq[0]);
                                if (prev == null) continue;

                                QSpan qSpan = new QSpan(prev);

                                for (int i = 1; i<seq.length; i++) {
                                    ExtIntervalTextBox curr = singletokenMatches.get(seq[i]);
                                    // we read tokens in wrriting order/ left to right - top to bottom
                                    if (curr.getExtInterval().getStart() < prev.getExtInterval().getEnd()) continue;
                                    if (curr == null) continue;
                                    BaseTextBox b1 = qSpan.getTextBox();
                                    BaseTextBox b2 = curr.getTextBox();
                                    float hOverlap = getHorizentalOverlap(b1, b2);
                                    boolean isGood = false;
                                    float distV = Math.abs(b1.getBase() - b2.getBase());

                                    if (hOverlap > .4 && (distV < 3*(b2.getBase() - b2.getTop()))) {
                                        isGood = true;
                                    } else {
                                        float vOverlap = getVerticalOverlap(b1, b2);
                                        if (vOverlap > .4 ) {
                                            float dist = b1.getLeft() > b2.getRight() ? b1.getLeft() - b2.getRight() : b2.getLeft() - b1.getRight();
                                            if (dist > 1.2 * (b2.getBase() - b1.getTop())){
                                                if (prev.getExtInterval().getEnd() < curr.getExtInterval().getStart()) {
                                                    String gap = content.substring(prev.getExtInterval().getEnd(), curr.getExtInterval().getStart());
                                                    String[] gap_tokens = tokenize(searchAnalyzer, gap);
                                                    if (gap.length() < 5 && (gap_tokens == null || gap.length() == 0)) {
                                                        isGood = true;
                                                    }
                                                }
                                            } else {
                                                isGood = true;
                                            }
                                        }
                                    }

                                    if (isGood) {
                                        qSpan.add(curr);
                                        qSpan.process(content);
                                        prev = curr;
                                    } else {
                                        break;
                                    }
                                }

                                if (qSpan.size() == tokens.length) {
                                    spans.add(qSpan);
                                }
                            }
                        }
                    }
                }

                if (spans.size() == 0) return spans;
                spans.sort(comparingInt((QSpan s) -> s.getExtInterval().getStart()));

                ListIterator<QSpan> iter = spans.listIterator();
                HashSet<String> ext_keys = new HashSet<>();

                while (iter.hasNext()){
                    QSpan qSpan = iter.next();
                    qSpan.process(content);
                    String str = qSpan.getStr();

                    String k = qSpan.getStart() + "_" + qSpan.getCategory();
                    if (ext_keys.contains(k)) {
                        iter.remove();
                        continue;
                    }
                    ext_keys.add(k);

                    Query doc_mini_query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, str,
                            minTermLength, maxEdits, prefixLength) :
                            getMatchAllQuery(searchAnalyzer, search_fld, str);

                    List<Document> mini_mtchs = getMatchedDocs(doc_mini_query);

                    List<ExtIntervalTextBox> mini_frags = getFragments(mini_mtchs, SPAN, false,1,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, str, null);

                    if (mini_frags.size() == 0) {
                        iter.remove();
                        continue;
                    }

                    for (ExtIntervalTextBox ex : qSpan.getExtIntervalTextBoxes()){
                        if (ex.getExtInterval().getCategory().equals(DONT_CARE)) {
                            iter.remove();
                            break;
                        }
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage(), content);
        }


        return spans;
    }


    public int getMinTermLength(){
        return minTermLength;
    }
    public void setMinTermLength(int minTermLength){
        this.minTermLength = minTermLength;
    }

    public int getMaxEdits() {
        return maxEdits;
    }

    public void setMaxEdits(int maxEdits) {
        this.maxEdits = maxEdits;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    private void create() {
        Map<String, Analyzer> analyzerMap = new HashMap<>();

        for (DctSearhFld dctSearhFld : docSearchFldList) {
            analyzerMap.put(dctSearhFld.getSearch_fld(), dctSearhFld.getIndex_analyzer());
        }

        PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);
        Directory mMapDirectory = new ByteBuffersDirectory();

        IndexWriterConfig config = new IndexWriterConfig(pfaw);

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
    }

    private void initDocSearchFld() {
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
}
