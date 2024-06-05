package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocumentHelper;
import com.quantxt.model.*;
import com.quantxt.model.Dictionary;
import com.quantxt.model.document.BaseTextBox;
import com.quantxt.model.document.ExtIntervalTextBox;
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
import static com.quantxt.model.DictSearch.AnalyzType.STANDARD;
import static com.quantxt.model.DictSearch.Mode.*;
import static com.quantxt.nlp.search.DctSearhFld.*;
import static com.quantxt.nlp.search.SearchUtils.*;
import static java.util.Comparator.comparingInt;

public class QTSearchable extends DictSearch<ExtInterval, Interval> implements Serializable {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);

    protected transient IndexSearcher indexSearcher;
    protected transient IndexSearcher negativeIndexSearcher;

    protected List<DctSearhFld> docSearchFldList = new ArrayList<>();
    protected List<String> synonymPairs;
    protected List<String> stopWords;
    protected QTDocumentHelper.Language lang = QTDocumentHelper.Language.ENGLISH;

    protected int topN = 2000;
    private int minTermLength = 2;
    private int maxEdits = 1;
    private int prefixLength = 1;

    private List<Interval> spread_spans = new ArrayList<>();
    private List<Interval> compact_spans = new ArrayList<>();
    private List<Interval> single_word_spans = new ArrayList<>();

    private List<Interval> spread_negatives = new ArrayList<>();
    private List<Interval> compact_negatives = new ArrayList<>();
    private List<Interval> single_word_negatives = new ArrayList<>();

    public QTSearchable(Dictionary dictionary) {
        this.synonymPairs = null;
        this.mode = new DictSearch.Mode[]{ORDERED_SPAN};
        this.analyzType = new DictSearch.AnalyzType[]{STANDARD};
        this.dictionary = dictionary;
        this.stopWords = null;
        initDocSearchFld();
        this.indexSearcher = create(dictionary.getVocab());
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
        this.indexSearcher = create(dictionary.getVocab());
    }

    public void setNegativeDictionary(Dictionary negativeDictionary){
        this.negativeDictionary = negativeDictionary;
        this.negativeIndexSearcher = create(negativeDictionary.getVocab());
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
        this.indexSearcher = create(dictionary.getVocab());
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
        boolean useFuzzyMatching = useFuzzyMatch();

        try {
            // This list is ordered by priorities
            // so if we find an entry that is matched with STANDARD analysis we won't consider it using STEM analysis
            String vocab_name = dictionary.getName();
            String vocab_id = dictionary.getId();
            for (DctSearhFld dctSearhFld : docSearchFldList) {
                String search_fld = dctSearhFld.getSearch_fld();
                Analyzer searchAnalyzer = dctSearhFld.getSearch_analyzer();

                Query query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, content,
                        minTermLength, maxEdits, prefixLength) :
                        getMultimatcheQuery(searchAnalyzer, search_fld, content);

                List<Document> matchedDocs = getMatchedDocs(indexSearcher, query);
                if (matchedDocs.size() == 0) continue;
                for (Mode m : mode) {
                    List<ExtInterval> matches = getFragments(matchedDocs, m, true, true, slop,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, content, null);
                    return matches;
                }
            }
        } catch (Exception e) {
            logger.error("Error in name search {}: content '{}'", e.getMessage(), content);
        }

        return new ArrayList<>();
    }

    private List<Document> getMatchedDocs(IndexSearcher indexSearcher, Query query) throws IOException {
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

    private boolean isIsolated(String lineStr, String str, Analyzer analyzer) {
        if (lineStr == null) return true;
        int idx = lineStr.indexOf(str);
        if (idx > 1) {
            String lastCharBefore = lineStr.substring(idx - 2, idx - 1);
            if (lineStr.charAt(idx - 1) == ' ' && tokenize(analyzer, lastCharBefore) != null) {
                int lnt = str.length();
                if (lineStr.length() > idx + lnt) {
                    if (lineStr.length() == idx + lnt + 1) {
                        return true;
                    }
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
                                          List<ExtInterval> singletokenMatches,
                                          Map<Byte, List<Byte>> tokenIdx2MatchIdx,
                                          int idx2process) {
        List<byte[]> newSequence = new ArrayList<>();

        List<Byte> list_current = tokenIdx2MatchIdx.get((byte) idx2process);
        if (list_current == null || list_current.size() == 0) return newSequence;
        for (byte[] seq : oldSequence) {
            Byte b_idx = seq[seq.length - 1];
            ExtInterval b_eitb = singletokenMatches.get(b_idx);
            int l1 = b_eitb.getLine();
            for (byte c_idx : list_current) {
                ExtInterval c_eitb = singletokenMatches.get(c_idx);
                int l2 = c_eitb.getLine();
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
    public List<Interval> postSearch(boolean hasTextboxes){
        filterNegativeWTextBox(spread_spans, single_word_spans, .25f, false);

        filterNegativeWTextBox(spread_spans, compact_negatives, .25f, false);

        filterNegativeWTextBox(spread_spans, compact_spans, .25f, true);

        filterNegativeWithoutTextBox(spread_spans, spread_negatives);
        filterNegativeWTextBox(compact_spans, compact_negatives, .25f, false);

        filterNegativeWithoutTextBox(spread_spans, single_word_negatives);
        filterNegativeWTextBox(compact_spans, single_word_negatives, .98f, false);

        filterNegativeWTextBox(compact_spans, spread_negatives, .98f, false);

        compact_spans = removeSmallOverlappingSpans(compact_spans);
        compact_negatives = removeSmallOverlappingSpans(compact_negatives);

        List<Interval> split_spread_spans = getSplitSpans(spread_spans);
        List<Interval> split_compact_spans = getSplitSpans(compact_spans);

        List<Interval> split_spread_negatives = getSplitSpans(spread_negatives);
        List<Interval> split_compact_negatives = getSplitSpans(compact_negatives);

        List<Interval> filtered = !hasTextboxes ? getFilteredSpansWithoutTextBox(compact_spans, compact_negatives) :
                getFilteredSpansWithTextBox(split_compact_spans, split_compact_negatives);

        List<Interval> filtered_spread = hasTextboxes ? getFilteredSpansWithoutTextBox(spread_spans, spread_negatives) :
                getFilteredSpansWithTextBox(split_spread_spans, split_spread_negatives);

        if (filtered_spread.size() > 0){
            filtered.addAll(filtered_spread);
        }

        // remove duplicates
        HashSet<String> uniq_labels = new HashSet<>();
        ListIterator<Interval> iter = filtered.listIterator();
        while (iter.hasNext()){
            Interval qSpan = iter.next();
            String key = qSpan.getStart() + "_" + qSpan.getEnd();
            if (uniq_labels.contains(key)) {
                iter.remove();
                continue;
            }
            uniq_labels.add(key);
        }
        return filtered;
    }

    @Override
    public void reset() {
        spread_spans = new ArrayList<>();
        compact_spans = new ArrayList<>();
        single_word_spans = new ArrayList<>();
        spread_negatives = new ArrayList<>();
        compact_negatives = new ArrayList<>();
        single_word_negatives = new ArrayList<>();
    }

    @Override
    public List<Interval> search(String content,
                                 Map<Integer, List<BaseTextBox>> lineTextBoxMap,
                                 int slop,
                                 boolean isolatedLabelsOnly) {

        searchHelper(indexSearcher, content, lineTextBoxMap, spread_spans,
                compact_spans, single_word_spans, slop, isolatedLabelsOnly);

        if (negativeIndexSearcher != null){
            searchHelper(negativeIndexSearcher, content, lineTextBoxMap,
                    spread_negatives, compact_negatives, single_word_negatives, slop, isolatedLabelsOnly);
        }

        // spans should not contain keyword other than associated label keywords
        if (lineTextBoxMap != null) {
            spread_spans = removeSpansWithIrrelevantKeywords(spread_spans, lineTextBoxMap);
            compact_spans = removeSpansWithIrrelevantKeywords(compact_spans, lineTextBoxMap);
        }
        // we have to run searchPost()
        return compact_spans;
    }

    public void searchHelper(IndexSearcher indexSearcher,
                             String content,
                             Map<Integer, List<BaseTextBox>> lineTextBoxMap,
                             List<Interval> spread_spans,
                             List<Interval> compact_spans,
                             List<Interval> single_word_spans,
                             int slop,
                             boolean isolatedLabelsOnly) {
        ArrayList<ExtIntervalTextBox> res = new ArrayList<>();
        boolean useFuzzyMatching = useFuzzyMatch();
        String [] lines = content.split("\\n");

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

                List<Document> matchedDocs = getMatchedDocs(indexSearcher, doc_query);
                // run once with slop = 0

                for (Mode m : mode) {
                    // we search for phrases in one line only or split over consecutive lines
                    List<ExtInterval> matches = getFragments(matchedDocs, m, true, isolatedLabelsOnly, slop,
                            searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                            search_fld, vocab_name, vocab_id, content, lineTextBoxMap);

                    for (int i = 0; i < matches.size(); i++) {
                        ExtInterval exti = matches.get(i);

                        int line = exti.getTextBoxes().get(0).getLine();

                        String str = exti.getStr();
                        boolean isIsolated = isIsolated(lines[line], str, searchAnalyzer);
                        if (isolatedLabelsOnly && !isIsolated) continue;

                        boolean str_is_spread = str.split("   ").length > 1;

                        if (str_is_spread){
                            spread_spans.add(exti);
                        } else {
                            compact_spans.add(exti);
                            single_word_spans.add(exti);
                        }
                    }

                    if (lineTextBoxMap != null) {

                        for (Document matchedDoc : matchedDocs) {
                            String string_value = matchedDoc.getField(search_fld).stringValue();
                            String[] tokens = tokenize(searchAnalyzer, string_value);
                            if (tokens.length > 127) continue;
                            if (tokens.length == 1) continue;

                            List<Document> singleMatchedDocList = new ArrayList<>();
                            singleMatchedDocList.add(matchedDoc);

                            List<ExtInterval> singletokenMatches = getFragments(singleMatchedDocList, PARTIAL_ORDERED_SPAN, false, false,20,
                                    searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                    search_fld, vocab_name, vocab_id, content, lineTextBoxMap);

                            if (singletokenMatches.size() > 127){
                                logger.info("Too long for single token concatenation", singletokenMatches.size(), string_value);
                                continue;
                            }

                            singletokenMatches.sort(comparingInt((ExtInterval s) -> s.getStart()));

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
                                String singleTokenEitb = singletokenMatches.get(i).getStr();
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

                            List<Interval> pre_compact_spans = new ArrayList<>();
                            for (byte[] seq : sequences) {
                                ExtInterval prev = singletokenMatches.get(seq[0]);
                                if (prev == null || prev.getTextBoxes() == null) continue;
                                boolean isValidSeq = true;
                                for (int i = 1; i < seq.length; i++) {
                                    int l1 = singletokenMatches.get(seq[i - 1]).getTextBoxes().get(0).getLine();
                                    int l2 = singletokenMatches.get(seq[i]).getTextBoxes().get(0).getLine();
                                    if (l2 - l1 < 0 || l2 - l1 > 3) {
                                        isValidSeq = false;
                                        break;
                                    }
                                }

                                if (!isValidSeq) continue;

                                List<Interval> qSpans = new ArrayList<>();
                                for (int i = 0; i < seq.length; i++) {
                                    ExtInterval eit = singletokenMatches.get(seq[i]);
                                    qSpans.add(eit);
                                }

                                Interval qSpan = singletokenMatches.get(0);

                                // merge horizentally
                                for (int i = 1; i < seq.length; i++) {
                                    Interval curr = singletokenMatches.get(i);
                                    // we read tokens in wrriting order/ left to right - top to bottom
                                    if (curr == null){
                                        continue;
                                    }
                                    if (curr.getStart() < qSpan.getEnd()) continue;
                                    BaseTextBox b1 = qSpan.getTextBox();
                                    BaseTextBox b2 = curr.getTextBox();
                                    float vOverlap = 0;
                                    boolean isGood = false;
                                    boolean currIsAfterqSpan = false;
                                    if (b2 == null || b1 == null){
                                        if ((b1 == null && qSpan.getStr().length() < 3) || (b2 == null && curr.getStr().length() < 3)) {
                                            isGood = true;
                                        }
                                    } else {
                                        vOverlap = getVerticalOverlap(b1, b2);
                                        currIsAfterqSpan = b1.getLeft() <= b2.getRight(); // this is a sequence of words in english so next word has to be after current
                                    }
                                    if (vOverlap > .4 && currIsAfterqSpan) {
                                        float dist = b1.getLeft() > b2.getRight() ? b1.getLeft() - b2.getRight() : b2.getLeft() - b1.getRight();
                                        if (dist > 1.2 * (b2.getBase() - b1.getTop())) {
                                            if (qSpan.getEnd() < curr.getStart()) {
                                                String gap = content.substring(qSpan.getEnd(), curr.getStart());
                                                if (gap.contains("  ")) break; // double space
                                                String[] gap_tokens = tokenize(searchAnalyzer, gap);
                                                if (gap_tokens != null && gap.length() > 0) break;
                                                // we're capturing tokens in a table header and most likely tapping
                                                // to adjacent column
                                                if (gap.length() < 5) {
                                                    isGood = true;
                                                }
                                            }
                                        } else {
                                            isGood = true;
                                        }
                                    }

                                    if (isGood) {
                                        for (BaseTextBox bt : curr.getTextBoxes()) {
                                            qSpan.add(bt);
                                        }
                                        qSpans.set(i, null);
                                    } else {
                                        qSpan = curr;
                                    }
                                }

                                // merge vertically
                                qSpan = qSpans.get(0);
                                for (int i = 1; i < seq.length; i++) {
                                    Interval curr = qSpans.get(i);
                                    // we read tokens in wrriting order/ left to right - top to bottom
                                    if (curr == null) {
                                        continue;
                                    }
                                    if (curr.getStart() < qSpan.getEnd()) continue;
                                    BaseTextBox b1 = qSpan.getTextBox();
                                    BaseTextBox b2 = curr.getTextBox();

                                    float hOverlap = 0;
                                    if (b2 == null || b1 == null){
                                        if ((b1 == null && qSpan.getStr().length() < 3) || (b2 == null && curr.getStr().length() < 3)) {
                                            for (BaseTextBox bt : curr.getTextBoxes()) {
                                                qSpan.add(bt);
                                            }
                                            qSpans.set(i, null);
                                        }
                                    } else {
                                        hOverlap = getHorizentalOverlap(b1, b2);
                                    }

                                    float distV = Math.abs(b1.getBase() - b2.getBase());

                                    if (hOverlap > .25 && (distV < 3 * (b2.getBase() - b2.getTop()))) {
                                        // we have to make sure there no other token verticaly in-between
                                        // compute textbox in between and check if any other textbox overlaps with it
                                        // we do this only if the two candidate spans are in lines that are NOT right under each other
                                        // l1 - l2 > 1
                                        if ((curr.getLine() - qSpan.getLine()) > 1) {
                                            BaseTextBox gapBetween = new BaseTextBox(b1.getBase(), b2.getBase(), Math.min(b1.getLeft(), b2.getLeft()), Math.max(b1.getRight(), b2.getRight()), "");
                                            boolean gapIsClear = true;
                                            int lastLine = qSpan.getKeys().get(qSpan.getKeys().size()-1).getLine();
                                            for (Map.Entry<Integer, BaseTextBox> e : lineTextBoxMap.entrySet()) {
                                                int gline = e.getKey();
                                                if (gline <= lastLine || gline >= curr.getLine()) continue;
                                                for (BaseTextBox gbox : e.getValue().getChilds()) {
                                                    float ghOverlap = getHorizentalOverlap(gapBetween, gbox);
                                                    if (ghOverlap > .2) {
                                                        gapIsClear = false;
                                                        break;
                                                    }
                                                }
                                                if (!gapIsClear) break;
                                            }
                                            if (!gapIsClear) continue;
                                        }
                                        for (BaseTextBox bt : curr.getTextBoxes()) {
                                            qSpan.add(bt);
                                        }
                                        qSpans.set(i, null);
                                    } else {
                                        break;
                                    }
                                }

                                if (qSpan.size() == tokens.length) {
                                    pre_compact_spans.add(qSpan);
                                }
                            }
                            // check pre_compact_spans and if there are one-line and multi-lines matches,
                            // remove multi-lines
                            // Case:   School Tax
                            //         City Tax
                            // We don't want "school tax" to pick up "tax" from second line
                            Map<String, Map<Integer, List<Interval>>> numLine2Match= new HashMap<>();
                            for (Interval qSpan : pre_compact_spans){
                                int nm = qSpan.getNum_lines();
                                String key = qSpan.getStart() + "_" + qSpan.getLine();
                                Map<Integer, List<Interval>> numLine2list = numLine2Match.get(key);
                                List<Interval> list = new ArrayList<>();
                                if (numLine2list == null) {
                                    numLine2list = new HashMap<>();
                                    numLine2list.put(nm, list);
                                    numLine2Match.put(key, numLine2list);
                                } else {
                                    List<Interval> alist = numLine2list.get(nm);
                                    if (alist != null){
                                        list = alist;
                                    } else {
                                        numLine2list.put(nm, list);
                                    }
                                }
                                list.add(qSpan);
                            }

                            for (Map.Entry<String, Map<Integer, List<Interval>>> e : numLine2Match.entrySet()){
                                Map<Integer, List<Interval>> numList2List = e.getValue();
                                if (numList2List.containsKey(1) && numList2List.size() > 1){
                                    compact_spans.addAll(numList2List.get(1));
                                } else {
                                    for (Map.Entry<Integer, List<Interval>> l : numList2List.entrySet()){
                                        compact_spans.addAll(l.getValue());
                                    }
                                }
                            }
                        }
                    }
                }
           }
        } catch (Exception e) {
            logger.error("Error in name search {}", e.getMessage());
        }
    }

    private static List<Interval> removeSpansWithIrrelevantKeywords(List<Interval> spans,
                                                                    Map<Integer, BaseTextBox> lineTextBoxMap)
    {
        List<Interval> filtered = new ArrayList<>();

        for (Interval qSpan : spans){
            boolean isGood = false;
            int l_eib = qSpan.getLine();
            BaseTextBox lineBox = lineTextBoxMap.get(l_eib);
            if (lineBox == null) continue;
            List<BaseTextBox> line_btbs = lineBox.getChilds();
            for (Interval eib : qSpan.getKeys()){

                // so we check if any of the other boxes on this line have major overlap with our span
                List<BaseTextBox> l_btbs = eib.getTextBoxes();
                if (l_btbs == null) continue;
                BaseTextBox l_btb = l_btbs.get(0);
                for (BaseTextBox btb : line_btbs){
                    String str = btb.getStr();
                    if (str == null || str.isEmpty() || str.replaceAll("\\p{Punct}", "").trim().isEmpty()) continue;
                    if (btb == null) continue;
                    float vo = getVerticalOverlap(btb, l_btb);
                    float ho = getHorizentalOverlap(btb, l_btb);
                    if (ho > .95f && ho < 1.05f && vo > .95f && vo < 1.05f) {
                        isGood = true;
                        break;
                    }
                }
                if (isGood) break;
            }
            if (isGood){
                filtered.add(qSpan);
            }
        }

        return filtered;
    }

    public static List<Interval> getFilteredSpansWithoutTextBox(List<Interval> spans,
                                                                List<Interval> negatives) {
        HashSet<Integer> bad_spans = new HashSet<>();
        for (int i = 0; i < spans.size(); i++) {
            Interval span1 = spans.get(i);
            int s1 = span1.getStart();
            int e1 = span1.getEnd();
            int l1 = span1.getLine();
            for (int j = 0; j < negatives.size(); j++) {
                Interval span2 = negatives.get(j);
                int s2 = span2.getStart();
                int e2 = span2.getEnd();
                int l2 = span2.getLine();
                if (l1 == l2) {
                    if ((s1 >= s2 && s1 <= e2) || (e1 >= s2 && e1 <= e2)) {
                        bad_spans.add(i);
                        break;
                    }
                }
            }
        }

        List<Interval> filtered = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            filtered.add(spans.get(i));
        }

        return filtered;
    }

    private List<Interval> removeSmallOverlappingSpans(List<Interval> spans){

        HashSet<Integer> bad_spans = new HashSet<>();
        spans.sort(Comparator.comparing(Interval::getArea).reversed());

        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            Interval qSpan1 = spans.get(i);
            int l1 = qSpan1.getNum_lines();
            BaseTextBox b1 = qSpan1.getTextBox();
            if (b1 == null) continue;
            float left1 = b1.getLeft();
            float right1 = b1.getRight();
            for (int j = i + 1; j < spans.size(); j++) {
                if (bad_spans.contains(j)) continue;
                Interval qSpan2 = spans.get(j);
                BaseTextBox b2 = qSpan2.getTextBox();
                if (b2 == null) continue;
                int l2 = qSpan2.getNum_lines();

                // b1 can be bigger than b2
            //    float ho2 = getSignedHorizentalOverlap(b1, b2);

                float left2 = b2.getLeft();
                float right2 = b2.getRight();
                boolean hasHorizentalOverlap = left1 <= left2 && right1 >= right2;
                if (!hasHorizentalOverlap) continue;
                float vo2 = getVerticalOverlap(b2, b1);

                if (vo2 > .1) {
                    if (qSpan2.getStr().equals(qSpan1.getStr())){
                        if (qSpan1.getOcc_area() > qSpan2.getOcc_area()){
                            bad_spans.add(i);
                        } else {
                            bad_spans.add(j);
                        }
                        /*
                        if (l1 == 1 && l2 != 1){
                            bad_spans.add(j);
                        } else if (l1 != 1 && l2 == 1){
                            bad_spans.add(i);
                        } else {
                            bad_spans.add(j);
                        }
                         */

                    } else {
                        bad_spans.add(j);
                    }
                }

            }
        }

        List<Interval> filtered = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            filtered.add(spans.get(i));
        }
        return filtered;
    }

    private List<Interval> getSplitSpans(List<Interval> spans){
        HashSet<Integer> bad_spans = new HashSet<>();

        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            Interval qSpan1 = spans.get(i);
            BaseTextBox b1 = qSpan1.getTextBox();
            if (b1 == null) continue;
            float s1 = qSpan1.getArea();
            String str1 = qSpan1.getStr();

            int lines_1 = qSpan1.getNum_lines();
            //unique case                  AMT
            //                   AMT       PAID
            // here we should keep both AMT PAID and filter one or both of them with negatives
            // removing `AMT PAID` since it occupies 2 lines is not ok
            if (lines_1 == 1 && str1.split(" {2,}").length > 1) continue;
            for (int j = i + 1; j < spans.size(); j++) {
                if (bad_spans.contains(j)) continue;
                Interval qSpan2 = spans.get(j);
                BaseTextBox b2 = qSpan2.getTextBox();
                if (b2 == null) continue;
                String str2 = qSpan2.getStr();

                float ho = getHorizentalOverlap(b1, b2);
                float vo = getVerticalOverlap(b1, b2);

                if (ho > .1 && vo > .1) {
                    int lines_2 = qSpan2.getNum_lines();

                    // if both spans have 1 line, we prefer the longer one
                    if (lines_2 == 1 && lines_1 == 1){
                        int e1 = qSpan1.getEnd();
                        int e2 = qSpan2.getEnd();
                        int st1 = qSpan1.getStart();
                        int st2 = qSpan2.getStart();
                        if (st1 == st2){
                            if (e1 >= e2){
                                bad_spans.add(j);
                            } else {
                                bad_spans.add(i);
                            }
                        } else {
                            int n1 = qSpan1.getTextBoxes().size();
                            int n2 = qSpan2.getTextBoxes().size();
                            if (n1 >= n2){
                                bad_spans.add(i);
                            } else {
                                bad_spans.add(j);
                            }
                        }
                        break;
                    }

                    if (str1.equals(str2)){
                        if (lines_2 == 1 && lines_1 != 1){
                            bad_spans.add(i);
                            break;
                        }

                        if (lines_2 != 1 && lines_1 == 1){
                            bad_spans.add(j);
                            break;
                        }
                    }
                    if (ho > .98 && vo > .98) {
                        // we remove the larger one
                        float s2 = qSpan2.getArea();
                        if (s2 >= s1){
                            bad_spans.add(j);
                        } else {
                            bad_spans.add(i);
                        }
                    } else {
                        //we take the one that is spread on less number of lines

                        if (lines_2 == 1 && str2.split(" {2,}").length > 1) continue;
                        if (lines_2 > lines_1) {
                            bad_spans.add(j);
                        } else if (lines_2 < lines_1) {
                            bad_spans.add(i);
                        }
                    }
                }
            }
        }

        List<Interval> filtered = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            filtered.add(spans.get(i));
        }

        return filtered;
    }

    public static void filterNegativeWTextBox(List<Interval> positives,
                                              List<Interval> negatives,
                                              float ratio,
                                              boolean ignoreSingleLines){
        List<Integer> negativesLineNumber = new ArrayList<>();
        if (ignoreSingleLines) {
            for (Interval neg : negatives) {
                HashSet<Integer> uniqLines = new HashSet<>();
                for (BaseTextBox bt : neg.getTextBoxes()) {
                    uniqLines.add(bt.getLine());
                }
                negativesLineNumber.add(uniqLines.size());
            }
        }

        ListIterator<Interval> iter1 = positives.listIterator();
        while (iter1.hasNext()){
            Interval pos = iter1.next();
            List<BaseTextBox> b1List = pos.getTextBoxes();
            if (b1List == null) continue;

            HashSet<Integer> uniqLines = new HashSet<>();
            for (BaseTextBox bt : pos.getTextBoxes()) {
                uniqLines.add(bt.getLine());
            }
            int posLines = uniqLines.size();
            for (int j = 0; j < negatives.size(); j++) {
                Interval neg = negatives.get(j);
                List<BaseTextBox> b2List = neg.getTextBoxes();
                if (b2List == null) continue;
                if (ignoreSingleLines && posLines == 1 && negativesLineNumber.get(j) == 1) continue;
                float ho = getHorizentalOverlaps(b1List, b2List);
                float vo = getVerticalOverlaps(b1List, b2List);
                if (ho > ratio && vo > ratio) {  // i completely covers j
                    iter1.remove();
                    break;
                }
            }
        }
    }

    public static void filterNegativeWithoutTextBox(List<Interval> positives,
                                                    List<Interval> negatives){
        ListIterator<Interval> iter1 = positives.listIterator();
        while (iter1.hasNext()){
            Interval qSpan1 = iter1.next();
            int s1 = qSpan1.getStart();
            int e1 = qSpan1.getEnd();
            for (int j = 0; j < negatives.size(); j++) {
                Interval qSpan2 = negatives.get(j);
                int s2 = qSpan2.getStart();
                int e2 = qSpan2.getEnd();
                if ((s2 >=s1 && s2 <=e1 ) || (s1 >=s2 && s1 <= e2) ) {  // i completely covers j
                    iter1.remove();
                    break;
                }
            }
        }
    }

    public static void filterMultiTokenMultiLineSpans(List<Interval> spans,
                                                      List<Interval> oneLineSpans)
    {
        List<Interval> good_spans = new ArrayList<>();
        for (int i=0; i<spans.size(); i++){
            Interval qSpan = spans.get(i);
            int num_tokens = qSpan.getTextBoxes().size();
            int num_lines = qSpan.getNum_lines();

            if (num_tokens == 1 || num_lines == 1) {
                good_spans.add(qSpan);
                continue;
            }
            BaseTextBox b1 = qSpan.getTextBox();
            if (b1 == null) {
                good_spans.add(qSpan);
                continue;
            }
            boolean isGood = true;
            for (Interval qSpan1 : oneLineSpans) {
                BaseTextBox b2 = qSpan1.getTextBox();
                if (b2 == null) continue;
                float ov = getOverlapPerc(b1, b2);
                if (ov > .99) continue; // it's the same qspan
                if (ov > .1){
                    logger.info("Filtering {} with {}", qSpan.getStr(), qSpan1.getStr());
                    isGood = false;
                    break;
        //        float ho = getHorizentalOverlap(b1, b2);
        //        float vo = getVerticalOverlap(b1, b2);
        //        if (ho > .99 && vo > .99) continue; // it's the same qspan
        //        if (ho >= .1 && vo >= .1) {
        //            logger.info("Filtering {} with {}", qSpan.getStr(), qSpan1.getStr());
        //            isGood = false;
        //            break;
                }
            }
            if (isGood) {
                good_spans.add(qSpan);
            }
        }
        spans.clear();
        spans.addAll(good_spans);
    }
    public static List<Interval> getFilteredSpansWithTextBox(List<Interval> spans,
                                                             List<Interval> negatives) {

        HashSet<Integer> bad_spans = new HashSet<>();
        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            BaseTextBox b1 = spans.get(i).getTextBox();
            if (b1 == null) continue;
            float s1 = (b1.getRight() - b1.getLeft()) * (b1.getBase() - b1.getTop());
            for (int j = i + 1; j < spans.size(); j++) {
                if (bad_spans.contains(j)) continue;
                BaseTextBox b2 = spans.get(j).getTextBox();
                if (b2 == null) continue;
                float ho = getHorizentalOverlap(b1, b2);
                float vo = getVerticalOverlap(b1, b2);
                if (ho > .95 && vo > .95) {  // i completely covers j
                    bad_spans.add(j);
                } else if (ho > 0 && vo > 0) {
                    float s2 = (b2.getRight() - b2.getLeft()) * (b2.getBase() - b2.getTop());
                    if (s1 > s2) {
                        bad_spans.add(i);
                    } else {
                        bad_spans.add(j);
                    }
                }
            }
        }

        List<Interval> filtered = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            if (bad_spans.contains(i)) continue;
            filtered.add(spans.get(i));
        }

        return filtered;
    }

    public int getMinTermLength() {
        return minTermLength;
    }

    public void setMinTermLength(int minTermLength) {
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

    private IndexSearcher create(List<DictItm> dictItms) {
        Map<String, Analyzer> analyzerMap = new HashMap<>();

        for (DctSearhFld dctSearhFld : docSearchFldList) {
            analyzerMap.put(dctSearhFld.getSearch_fld(), dctSearhFld.getIndex_analyzer());
        }

        PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);
        Directory mMapDirectory = new ByteBuffersDirectory();

        IndexWriterConfig config = new IndexWriterConfig(pfaw);

        try {
            IndexWriter writer = new IndexWriter(mMapDirectory, config);

            for (DictItm dictItm : dictItms) {
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
            return new IndexSearcher(dreader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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

    public List<Interval> getSpread_spans() {
        return spread_spans;
    }

    public List<Interval> getCompact_spans() {
        return compact_spans;
    }

    public List<Interval> getSingle_word_spans() {
        return single_word_spans;
    }

    public List<Interval> getSpread_negatives() {
        return spread_negatives;
    }

    public List<Interval> getCompact_negatives() {
        return compact_negatives;
    }

    public List<Interval> getSingle_word_negatives() {
        return single_word_negatives;
    }
}
