package com.quantxt.nlp.search;

import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictSearch;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.CharsRefBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quantxt.nlp.search.DctSearhFld.DataField;
import static com.quantxt.nlp.search.DctSearhFld.SearchFieldType;

public class SearchUtils {


    final private static Logger logger = LoggerFactory.getLogger(SearchUtils.class);

    public static Analyzer getExactCaseInsensetiveAnalyzer() {
        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                final Tokenizer source = new KeywordTokenizer();
                final TokenStream tokenStream = new LowerCaseFilter(source);
                return new Analyzer.TokenStreamComponents(source, tokenStream);
            }
        };
        return analyzer;
    }

    public static Query getPhraseQuery(Analyzer analyzer, String fld, String query, int slop) {
        QueryParser qp = new QueryParser("", analyzer);
        Query q = qp.createPhraseQuery(fld, query, slop);
        return q;
    }

    public static Query getMultimatcheQuery(Analyzer analyzer, String fld, String query)  {
        QueryParser qp = new QueryParser(fld, analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.SHOULD;
        Query q = qp.createBooleanQuery(fld, query, matching_mode);
        return q;
    }

    public static String[] tokenize(Analyzer analyzer, String str) {
        TokenStream ts = analyzer.tokenStream(null, str);
        return getTokenizedString(ts);
    }

    private static String[] getTokenizedString(TokenStream tokenStream) {
        CharTermAttribute cattr = tokenStream.addAttribute(CharTermAttribute.class);
        ArrayList<String> tokens = new ArrayList<>();
        try {
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                String term = cattr.toString();
                tokens.add(term);
            }
            tokenStream.end();
            tokenStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (tokens.size() == 0) return null;
        return tokens.toArray(new String[tokens.size()]);
    }


    private static IndexSearcher getIndexSearcher(Analyzer analyzer,
                                           String fld,
                                           String str) throws IOException {

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(mMapDirectory, config);

        Document doc = new Document();
        doc.add(new Field(fld, str, SearchFieldType));
        writer.addDocument(doc);
        writer.close();
        DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
        return new IndexSearcher(dreader);
    }

    public static Collection<QTMatch> getFragments(final Collection<Document> matchedDocs,
                                                   final DictSearch.Mode mode,
                                                   final int minFuzzyTermLength,
                                                   final Analyzer index_analyzer,
                                                   final Analyzer search_analyzer,
                                                   final String searchField,
                                                   final String vocab_name,
                                                   final String str) throws Exception
    {
        List<QTMatch> matchs = new ArrayList<>();
        IndexSearcher searcher = getIndexSearcher(index_analyzer, searchField, str);

        //TODO: this is messy; re-factor it with proper multi-field index
        for (Document matchedDoc : matchedDocs) {
            SpanQuery query = null;
            String query_string_raw = matchedDoc.getField(searchField).stringValue();
            String query_string = QueryParser.escape(query_string_raw);
            switch (mode) {
                case ORDERED_SPAN :
                    query = getSpanQuery(search_analyzer , searchField, query_string, 1, minFuzzyTermLength, false, true);
                    break;
                case PARTIAL_SPAN:
                case SPAN :
                    query = getSpanQuery(search_analyzer , searchField, query_string, 1, minFuzzyTermLength, false, false);
                    break;
                case FUZZY_ORDERED_SPAN:
                    query = getSpanQuery(search_analyzer , searchField, query_string, 1, minFuzzyTermLength, true, true);
                    break;
                case PARTIAL_FUZZY_SPAN:
                case FUZZY_SPAN:
                    query = getSpanQuery(search_analyzer , searchField, query_string, 1, minFuzzyTermLength, true, false);
                    break;
            }
            if (query == null) continue;
            SpanWeight span_weights = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
            if (span_weights == null) continue;
            Spans spans = span_weights.getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
            if (spans == null) continue;

            int s = spans.nextDoc();
            int spanstart = spans.nextStartPosition();
            String dataValue = matchedDoc.getField(DataField).stringValue();
            while (spanstart < 2147483647) {
                int spanend = spans.endPosition() -1;
                TokenStream ts = search_analyzer.tokenStream(searchField, str);
                PositionIncrementAttribute positionIncrementAttribute = ts.getAttribute(PositionIncrementAttribute.class);
                OffsetAttribute offsetAttribute = ts.getAttribute(OffsetAttribute.class);
                try {
                    ts.reset();
                    int cursur = -1;
                    int startPhraseInStr = -1;
                    int endPhraseInStr = -1;
                    while (ts.incrementToken()) {
                        int positionIncrement = positionIncrementAttribute.getPositionIncrement();
                        cursur += positionIncrement;
                        int start = offsetAttribute.startOffset();
                        int end = offsetAttribute.endOffset();
                        if (cursur == (spanstart)) {
                            startPhraseInStr = start;
                            endPhraseInStr = end;
                        }
                        if (cursur == (spanend)) {
                            endPhraseInStr = end;
                            break;
                        }
                    }
                    ts.end();
                    if (startPhraseInStr >=0 && endPhraseInStr > 0) {
                        String keyword = str.substring(startPhraseInStr, endPhraseInStr);
                        //        logger.info("KEYWORD: {}", keyword);
                        QTMatch qtMatch = new QTMatch(startPhraseInStr, endPhraseInStr, keyword);
                        qtMatch.setCustomData(dataValue);
                        qtMatch.setGroup(vocab_name);
                        matchs.add(qtMatch);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    ts.close();
                }
                spanstart = spans.nextStartPosition();
            }
        }

        ArrayList<QTMatch> matchs_sorted_by_length = new ArrayList<>(matchs);
        matchs_sorted_by_length.sort((QTMatch s1, QTMatch s2)-> (s2.getEnd() - s2.getStart()) - (s1.getEnd() - s1.getStart()));

        boolean [] overlaps = new boolean[matchs_sorted_by_length.size()];

        for (int i = 0; i < matchs_sorted_by_length.size(); i++) {
            if (overlaps[i]) continue;
            final QTMatch firstMatch = matchs_sorted_by_length.get(i);
            int firstMatchStart = firstMatch.getStart();
            int firstMatchEnd   = firstMatch.getEnd();
            for (int j = i+1; j < matchs_sorted_by_length.size(); j++) {
                if (overlaps[j]) continue;
                final QTMatch otherMatch = matchs_sorted_by_length.get(j);
                int otherMatchStart = otherMatch.getStart();
                int otherMatchEnd   = otherMatch.getEnd();
                if ((otherMatchStart >= firstMatchStart) && (otherMatchEnd <= firstMatchEnd) &&
                firstMatch.getGroup().equals(otherMatch.getGroup())) {
                    overlaps[j] = true;
                }
            }
        }
        ArrayList<QTMatch> noOverlapOutput = new ArrayList<>();
        for (int i = 0; i < matchs_sorted_by_length.size(); i++){
            if (overlaps[i]) continue;
            noOverlapOutput.add(matchs_sorted_by_length.get(i));
        }
        noOverlapOutput.sort((QTMatch s1, QTMatch s2)-> (s1.getStart() -  s2.getStart()));
        return noOverlapOutput;

    }


    public static Analyzer getSynonymAnalyzer(List<String> synonymPairs,
                                              List<String> stopwords,
                                              DictSearch.AnalyzType analyzType,
                                              Analyzer index_analyzer)
    {
        if (synonymPairs == null || synonymPairs.size() == 0) return index_analyzer;
        CharArraySet stopWords_charArray = stopwords == null || stopwords.size() == 0?
                CharArraySet.EMPTY_SET : new CharArraySet(stopwords, false);
        try {
            SynonymMap.Builder builder = new SynonymMap.Builder(true);
            // first argument is mapped to second
            for (String s : synonymPairs) {
                String[] parts = s.split("\\t");
                if (parts.length != 2) continue;

                CharsRefBuilder inputCharsRef = new CharsRefBuilder();
                SynonymMap.Builder.join(tokenize(index_analyzer, parts[0]), inputCharsRef);

                CharsRefBuilder outputCharsRef = new CharsRefBuilder();
                SynonymMap.Builder.join(tokenize(index_analyzer, parts[1]), outputCharsRef);
                builder.add(inputCharsRef.get(), outputCharsRef.get(), true);

            }

            final SynonymMap map = builder.build();

            Analyzer analyzer = new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName) {
                    TokenStreamComponents tokenStreamComponents = null;
                    switch (analyzType) {
                        case EXACT:
                            KeywordTokenizer keywordTokenizer = new KeywordTokenizer();
                            TokenStream tokenStream = new CachingTokenFilter(keywordTokenizer);
                            TokenStream sysfilter = new SynonymGraphFilter(tokenStream, map, false);
                            tokenStreamComponents = new TokenStreamComponents(keywordTokenizer, sysfilter);
                            break;
                        case EXACT_CI:
                            KeywordTokenizer keywordLcTokenizer = new KeywordTokenizer();
                            tokenStream = new LowerCaseFilter(keywordLcTokenizer);
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(keywordLcTokenizer, sysfilter);
                            break;
                        case WHITESPACE:
                            WhitespaceTokenizer whitespaceTokenizer = new WhitespaceTokenizer();
                            tokenStream = new CachingTokenFilter(whitespaceTokenizer);
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(whitespaceTokenizer, sysfilter);
                            break;
                        case SIMPLE:
                            LetterTokenizer letterTokenizer = new LetterTokenizer();
                            tokenStream = new LowerCaseFilter(letterTokenizer);
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(letterTokenizer, sysfilter);
                            break;
                        case STANDARD:
                            StandardTokenizer standardTokenizer = new StandardTokenizer();
                            tokenStream = new LowerCaseFilter(standardTokenizer);
                            tokenStream = new org.apache.lucene.analysis.StopFilter(tokenStream, stopWords_charArray);
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(standardTokenizer, sysfilter);
                            break;
                        case STEM:
                            standardTokenizer = new StandardTokenizer();
                            tokenStream = new EnglishPossessiveFilter(standardTokenizer);
                            tokenStream = new LowerCaseFilter(tokenStream);
                            tokenStream = new StopFilter(tokenStream, stopWords_charArray);
                            tokenStream = new PorterStemFilter(tokenStream);
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(standardTokenizer, sysfilter);
                            break;
                    }
                    return tokenStreamComponents;
                }
            };
            return analyzer;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static SpanQuery getSpanQuery(Analyzer analyzer,
                                         String fld,
                                         String query,
                                         int slop,
                                         int minTermLength,
                                         boolean isFuzzy,
                                         boolean ordered)
    {
        QueryParser qp = new QueryParser("", analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.SHOULD;
        Query q = isFuzzy ? getFuzzyQuery(analyzer, fld, query, minTermLength) :
                qp.createBooleanQuery(fld, query, matching_mode);
        String query_dsl = q.toString();
        //    logger.info(query_dsl);
        AtomicInteger parse_start = new AtomicInteger();
        SpanQuery spanQuery = parse(query_dsl, fld, slop, parse_start, false, ordered);
        return spanQuery;
    }

    public static Query getFuzzyQuery(Analyzer analyzer, String fld, String query, int minTermLength) {
        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        TokenStream tokens = analyzer.tokenStream("", query);
        try {
            CharTermAttribute cattr = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken()) {
                String term = cattr.toString();
                if (term.length() < minTermLength) continue;
                bqueryBuilder.add(new FuzzyQuery(new Term(fld, term), 2, 2, 50, false), BooleanClause.Occur.MUST);
            }
            tokens.end();
            tokens.close();
        } catch (Exception e){
            e.printStackTrace();
        }

        return bqueryBuilder.build();
    }

    private static SpanQuery parse(String query,
                            String search_fld,
                            int slop,
                            AtomicInteger idx,
                            boolean is_synonym_clause,
                            boolean ordered)
    {
        LinkedHashSet<SpanQuery> queryList = new LinkedHashSet<>();
        boolean mode_is_field = true;
        StringBuilder fld = new StringBuilder();
        StringBuilder term = new StringBuilder();

        while (idx.get() < query.length()){
            int current_idx = idx.get();
            char c = query.charAt(current_idx);
            idx.incrementAndGet();
            switch (c) {
                case '+':
                    break;
                case ' ':
                    mode_is_field = true;
                    if (fld.length() > 0 && term.length() > 0) {
                        SpanTermQuery spanTermQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
                        fld = new StringBuilder();
                        term = new StringBuilder();
                        queryList.add(spanTermQuery);
                    }
                    break;
                case ':':
                    mode_is_field = false;
                    break;
                case '(':
                    boolean synonym_clause = false;
                    if (fld.toString().equals("Synonym")){
                        synonym_clause = true;
                    }
                    SpanQuery spanQuery = parse(query, search_fld, slop, idx, synonym_clause, ordered);
                    queryList.add(spanQuery);
                    fld = new StringBuilder();
                    term = new StringBuilder();
                    break;
                case ')':
                    if (fld.length() > 0 && term.length() > 0) {
                        SpanTermQuery spanTQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
                        queryList.add(spanTQuery);
                    }
                    if (queryList.size() == 0) {
                        logger.error("This is an empty Span..");
                        return null;
                    } else if (queryList.size() == 1) {
                        return queryList.iterator().next();
                    } else {
                        if (is_synonym_clause) {
                            SpanOrQuery spanOrQuery = new SpanOrQuery(queryList.toArray(new SpanQuery[queryList.size()]));
                            return spanOrQuery;
                        } else {
                            SpanNearQuery.Builder spanNearBuilder = new SpanNearQuery.Builder(search_fld, ordered);
                            spanNearBuilder.setSlop(slop);
                            for (SpanQuery sq : queryList) {
                                spanNearBuilder.addClause(sq);
                            }
                            return spanNearBuilder.build();
                        }
                    }
                default:
                    if (mode_is_field) {
                        fld.append(c);
                    } else {
                        term.append(c);
                    }
            }
        }

        if (fld.length() > 0 && term.length() > 0) {
            SpanTermQuery spanTermQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
            queryList.add(spanTermQuery);
        }

        if (queryList.size() == 0) return null;
        if (queryList.size() == 1) {
            return queryList.iterator().next();
        } else {
            SpanNearQuery.Builder spanNearBuilder = new SpanNearQuery.Builder(search_fld, true);
            spanNearBuilder.setSlop(slop);
            for (SpanQuery spanQuery : queryList) {
                spanNearBuilder.addClause(spanQuery);
            }
            return spanNearBuilder.build();
        }
    }
}
