package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.model.DictItm;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.DictSearch;
import com.quantxt.model.Dictionary;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
import com.quantxt.types.LineInfo;
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

    private boolean isIsolated(QSpan qSpan, Analyzer analyzer){
        String lineStr = qSpan.getLine_str();
        // For now we only get linestr for paginated documents such as images nd PDFs
        if (lineStr == null) return true;
        String str = qSpan.getStr();
        int idx = lineStr.indexOf(str);
        if (idx > 1) {
            String lastCharBefore = lineStr.substring(idx - 2, idx - 1);
            if (lineStr.charAt(idx - 1) == ' ' && tokenize(analyzer, lastCharBefore) != null) {
                int lnt = str.length();
                if (lineStr.length() > idx + lnt) {
                    String firstCharAfter = lineStr.substring(idx + lnt + 1, idx + lnt + 2);
                    if (lineStr.charAt(idx + lnt) == ' ' && tokenize(analyzer, firstCharAfter) != null) {
                        logger.debug("{} in {} is NOT isolated", str, lineStr);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<byte[]> getNextSequences(List<byte[]> oldSequence,
                                          List<ExtIntervalTextBox> singletokenMatches,
                                          Map<Byte, List<Byte>> tokenIdx2MatchIdx,
                                          int idx2process){
        List<byte[]> newSequence = new ArrayList<>();

        List<Byte> list_current = tokenIdx2MatchIdx.get((byte) idx2process);
        if (list_current == null || list_current.size() == 0) return newSequence;
        for (byte [] seq : oldSequence){
            Byte b_idx = seq[seq.length-1];
            ExtIntervalTextBox b_eitb = singletokenMatches.get(b_idx);
            int l1 = b_eitb.getExtInterval().getLine();
            for (byte c_idx : list_current){
                ExtIntervalTextBox c_eitb = singletokenMatches.get(c_idx);
                int l2 = c_eitb.getExtInterval().getLine();
                if (c_idx > b_idx) {
                    if (l2 - l1 >= 0 && l2 - l1 < 4) {
                        byte newarr[] = new byte[seq.length + 1];

                        // insert the elements from
                        // the old array into the new array
                        // insert all elements till n
                        // then insert x at n+1
                        for (int i = 0; i < seq.length; i++) {
                            newarr[i] = seq[i];
                        }
                        newarr[seq.length] = c_idx;
                        newSequence.add(newarr);
                    }
                }
            }
        }


        return newSequence;
    }
    @Override
    public List<QSpan> search(String content,
                              Map<Integer, BaseTextBox> lineTextBoxMap,
                              int slop,
                              boolean isolatedLabelsOnly) {
        ArrayList<ExtIntervalTextBox> res = new ArrayList<>();
        boolean useFuzzyMatching = useFuzzyMatch();
        List<QSpan> spans = new ArrayList<>();
        List<QSpan> complete_spans = new ArrayList<>();
        List<QSpan> partial_spans = new ArrayList<>();

        List<QSpan> negatives = new ArrayList<>();

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
                        QSpan qSpan = new QSpan(matches.get(i));
                        if (lineTextBoxMap != null) {
                            if (qSpan.getTextBox() == null) continue;
                        }
                        qSpan.process(content);
                        boolean isNegative = false;
                        for (ExtIntervalTextBox ex : qSpan.getExtIntervalTextBoxes()) {
                            if (ex.getExtInterval().getCategory().equals(DONT_CARE)) {
                                isNegative = true;
                                negatives.add(qSpan);
                                break;
                            }
                        }
                        if (!isNegative) {
                            boolean isIsolated = isIsolated(qSpan, searchAnalyzer);
                            if (!isIsolated) continue;

                            LineInfo lineInfo = new LineInfo(content, matches.get(i).getExtInterval());
                            qSpan.getExtIntervalTextBoxes().get(0).getExtInterval().setLine(lineInfo.getLineNumber());
                            complete_spans.add(qSpan);
                        }
                    }

                    if (lineTextBoxMap != null) {
                        /*
                        HashSet<String> cur_matches = new HashSet<>();
                        for (ExtIntervalTextBox etb : matches){
                            String key = etb.getExtInterval().getStart()+"_"+etb.getExtInterval().getEnd();
                            cur_matches.add(key);
                        }

                         */
                        for (Document matchedDoc : matchedDocs) {
                            List<Document> singleMatchedDocList = new ArrayList<>();
                            singleMatchedDocList.add(matchedDoc);
                            List<ExtIntervalTextBox> singletokenMatches = getFragments(singleMatchedDocList, PARTIAL_ORDERED_SPAN, false, 20,
                                    searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                    search_fld, vocab_name, vocab_id, content, lineTextBoxMap);

                            singletokenMatches.sort(comparingInt((ExtIntervalTextBox s) -> s.getExtInterval().getStart()));
                    //        List<String[]> matchedDocumentTokens = new ArrayList<>();

                    //        for (Document d : matchedDocs) {
                                String[] tokens = tokenize(searchAnalyzer, matchedDoc.getField(search_fld).stringValue());
                                if (tokens.length > 127) continue;
                                if (tokens.length == 1) continue;
                    //            matchedDocumentTokens.add(tokens);
                    //        }

                    //        for (String[] tokens : matchedDocumentTokens) {
                                Map<String, List<Byte>> tokenIndex = new HashMap<>();

                                for (byte i = 0; i < tokens.length; i++) {
                                    String t = tokens[i];
                                    List<Byte> list = tokenIndex.get(t);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        tokenIndex.put(t, list);
                                    }
                                    list.add(i);
                                }

                                Map<Byte, List<Byte>> tokenIdx2MatchIdx = new HashMap<>();
                                Map<Byte, List<Byte>> relations = new HashMap<>();
                                for (byte i = 0; i < singletokenMatches.size(); i++) {
                                    ExtIntervalTextBox eitb = singletokenMatches.get(i);
                                    //            LineInfo lineInfo = new LineInfo(content, eitb.getExtInterval());
                                    //            eitb.getExtInterval().setLine(lineInfo.getLineNumber());
                                    String singleTokenEitb = eitb.getExtInterval().getStr();
                                    String[] sTokens = tokenize(searchAnalyzer, singleTokenEitb);
                                    if (sTokens == null || sTokens.length == 0 || sTokens[0].isEmpty()) continue;
                                    String singleToken = sTokens[0];
                                    List<Byte> tokenIdxs = tokenIndex.get(singleToken);
                                    if (tokenIdxs == null) continue;
                                    for (Byte tm : tokenIdxs) {
                                        List<Byte> rels = relations.get(i);
                                        if (rels == null) {
                                            rels = new ArrayList<>();
                                            relations.put(i, rels);
                                        }
                                        rels.add(tm);

                                        List<Byte> matchIdxs = tokenIdx2MatchIdx.get(tm);
                                        if (matchIdxs == null) {
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

                                List<byte[]> sequences = new ArrayList<>();
                                List<Byte> list_0 = tokenIdx2MatchIdx.get((byte) 0);
                                if (list_0 == null) continue;
                                for (byte b : list_0) {
                                    byte[] b_arr = new byte[1];
                                    b_arr[0] = b;
                                    sequences.add(b_arr);
                                }
                                for (int i = 1; i < tokenIdx2MatchIdx.size(); i++) {
                                    sequences = getNextSequences(sequences, singletokenMatches, tokenIdx2MatchIdx, i);
                                }

                                logger.debug("Total Comb {}, tokens {}", sequences.size(), tokens.length);

                                for (byte[] seq : sequences) {
                                    ExtIntervalTextBox prev = singletokenMatches.get(seq[0]);
                                    if (prev == null) continue;
                                    boolean isValidSeq = true;
                                    for (int i = 1; i < seq.length; i++) {
                                        int l1 = singletokenMatches.get(seq[i - 1]).getExtInterval().getLine();
                                        int l2 = singletokenMatches.get(seq[i]).getExtInterval().getLine();
                                        if (l2 - l1 < 0 || l2 - l1 > 3) {
                                            isValidSeq = false;
                                            break;
                                        }
                                    }

                                    if (!isValidSeq) continue;
                                    QSpan qSpan = new QSpan(prev);

                                    for (int i = 1; i < seq.length; i++) {
                                        ExtIntervalTextBox curr = singletokenMatches.get(seq[i]);
                                        // we read tokens in wrriting order/ left to right - top to bottom
                                        if (curr.getExtInterval().getStart() < prev.getExtInterval().getEnd()) continue;
                                        if (curr == null) continue;
                                        BaseTextBox b1 = qSpan.getTextBox();
                                        BaseTextBox b2 = curr.getTextBox();
                                        float hOverlap = getHorizentalOverlap(b1, b2);
                                        boolean isGood = false;
                                        float distV = Math.abs(b1.getBase() - b2.getBase());

                                        if (hOverlap > .4 && (distV < 3 * (b2.getBase() - b2.getTop()))) {
                                            isGood = true;
                                        } else {
                                            float vOverlap = getVerticalOverlap(b1, b2);
                                            boolean currIsAfterqSpan = b1.getLeft() <= b2.getRight(); // this is a sequence of words in english so next word has to be after current
                                            if (vOverlap > .4 && currIsAfterqSpan) {
                                                float dist = b1.getLeft() > b2.getRight() ? b1.getLeft() - b2.getRight() : b2.getLeft() - b1.getRight();
                                                if (dist > 1.2 * (b2.getBase() - b1.getTop())) {
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
                                        boolean isNegative = false;
                                        qSpan.process(content);
                                        // check if the match is negative
                                        // we remove matches that are part of a test line
                                        if (isolatedLabelsOnly) {
                                            boolean isIsolated = isIsolated(qSpan, searchAnalyzer);
                                            if (!isIsolated) continue;
                                        }

                                        List<ExtIntervalTextBox> tmpMatch = getFragments(matchedDocs, SPAN, false, 1,
                                                searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                                search_fld, vocab_name, vocab_id, qSpan.getStr(), null);

                                        for (ExtIntervalTextBox ex : tmpMatch) {
                                            if (ex.getExtInterval().getCategory().equals(DONT_CARE)) {
                                                isNegative = true;
                                                negatives.add(qSpan);
                                                break;
                                            }
                                        }

                                        if (!isNegative) {
                                            ExtIntervalTextBox firstPExt = qSpan.getExtIntervalTextBoxes().get(0);
                                            boolean isInCompleteSpans = false;
                                            ListIterator<QSpan> iter = complete_spans.listIterator();
                                            while (iter.hasNext()) {
                                                QSpan qs = iter.next();
                                                float d1 = firstPExt.getTextBox().getBase() - qs.getBase();
                                                float d2 = firstPExt.getTextBox().getLeft() - qs.getLeft();
                                                if (Math.abs(d1) < 2 && Math.abs(d2) < 2) {
                                                    isInCompleteSpans = true;
                                                    break;
                                                }
                                            }
                                            if (!isInCompleteSpans) {
                                                partial_spans.add(qSpan);
                                            }
                                        }
                                    }
                                }
                //            }
                        }
                    }
                }

        //        spans.addAll(getNonOverlappingSpans(partial_spans));
                spans.addAll(partial_spans);
                spans.addAll(complete_spans);

                if (spans.size() == 0) return spans;
                spans.sort(comparingInt((QSpan s) -> s.getExtInterval(false).getStart()));

                /*
                ListIterator<QSpan> iter = spans.listIterator();

                while (iter.hasNext()) {
                    QSpan qSpan = iter.next();
                    qSpan.process(content);
                    String str = qSpan.getStr();
                    Query doc_mini_query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, str,
                            minTermLength, maxEdits, prefixLength) :
                            getMatchAllQuery(searchAnalyzer, search_fld, str);

                    List<Document> mini_mtchs = getMatchedDocs(doc_mini_query);

                    List<ExtIntervalTextBox> mini_frags = getFragments(mini_mtchs, SPAN, false, 1,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, str, null);

                    if (mini_frags.size() == 0) {
                        iter.remove();
                        continue;
                    }

                }

                 */

                /*
                String str = qSpan.getStr();

                String k = qSpan.getStart() + "_" + qSpan.getCategory();
                if (ext_keys.contains(k)) {
                    iter.remove();
                    continue;
                }
                ext_keys.add(k);

                 */
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage(), content);
        }

        if (spans.size() < 2) return spans;
        if (lineTextBoxMap == null) return spans;

        HashSet<Integer> bad_spans  = new HashSet<>();

        for (int i=0; i< spans.size(); i++) {
            BaseTextBox b1 = spans.get(i).getTextBox();
            if (b1 == null) continue;
            for (int j = 0; j < negatives.size(); j++) {
                BaseTextBox b2 = negatives.get(j).getTextBox();
                if (b2 == null) continue;
                float ho = getHorizentalOverlap(b1, b2);
                float vo = getVerticalOverlap(b1, b2);
                if (ho > .95 && vo > .95) {  // i completely covers j
                    bad_spans.add(i);
                    break;
                }
            }
        }
        for (int i=0; i< spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            BaseTextBox b1 = spans.get(i).getTextBox();
            if (b1 == null) continue;
            float s1 = (b1.getRight() - b1.getLeft()) * (b1.getBase() - b1.getTop());
            for (int j=i+1; j< spans.size(); j++) {
                if (bad_spans.contains(j)) continue;
                BaseTextBox b2 = spans.get(j).getTextBox();
                if (b2 == null) continue;
                float ho = getHorizentalOverlap(b1, b2);
                float vo = getVerticalOverlap(b1, b2);
                if (ho > .95 && vo > .95){  // i completely covers j
                    bad_spans.add(j);
                } else if (ho > 0 && vo > 0){
                    float s2 = (b2.getRight() - b2.getLeft()) * (b2.getBase() - b2.getTop());
                    if (s1 > s2){
                        bad_spans.add(i);
                    } else {
                        bad_spans.add(j);
                    }
                }
            }
        }

        List<QSpan> filtered = new ArrayList<>();
        for (int i=0; i< spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            filtered.add(spans.get(i));
        }

        return filtered;
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
