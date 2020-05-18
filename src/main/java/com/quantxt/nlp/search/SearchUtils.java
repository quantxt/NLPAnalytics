package com.quantxt.nlp.search;

import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictSearch;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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

    public static Analyzer getNgramAnalyzer(CharArraySet stopWords_charArray) {
        Analyzer analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                final Tokenizer source = new StandardTokenizer();
                TokenStream tokenStream = new LowerCaseFilter(source);
                tokenStream = new StopFilter(tokenStream, stopWords_charArray);
                tokenStream = new NGramTokenFilter(tokenStream,4,4, true);
                return new Analyzer.TokenStreamComponents(source, tokenStream);
            }
        };
        return analyzer;
    }

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

    public static Query getMultimatcheQuery(Analyzer analyzer,
                                            String fld,
                                            String query)  {
        QueryParser qp = new QueryParser(fld, analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.SHOULD;
        Query q = qp.createBooleanQuery(fld, query, matching_mode);
        return q;
    }

    public static Query getMatchAllQuery(Analyzer analyzer, String fld, String query)  {
        QueryParser qp = new QueryParser(fld, analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.MUST;
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
                                                   final Analyzer search_analyzer,
                                                   final Analyzer keyphrase_analyzer,
                                                   final String searchField,
                                                   final String vocab_name,
                                                   final String str) throws Exception
    {
        List<QTMatch> allMatches = new ArrayList<>();

        //TODO: this is messy; re-factor it with proper multi-field index
        for (Document matchedDoc : matchedDocs) {
            SpanQuery query = null;
            String query_string_raw = matchedDoc.getField(searchField).stringValue();
            String query_string = QueryParser.escape(query_string_raw);
            switch (mode) {
                case ORDERED_SPAN :
                    query = getSpanQuery(keyphrase_analyzer , searchField, query_string, 1, false, true);
                    break;
                case PARTIAL_SPAN:
                case SPAN :
                    query = getSpanQuery(keyphrase_analyzer , searchField, query_string, 1, false, false);
                    break;
                case FUZZY_ORDERED_SPAN:
                    query = getSpanQuery(keyphrase_analyzer , searchField, query_string, 1, true, true);
                    break;
                case PARTIAL_FUZZY_SPAN:
                case FUZZY_SPAN:
                    query = getSpanQuery(keyphrase_analyzer , searchField, query_string, 1, true, false);
                    break;
            }
            if (query == null) continue;

            TokenStream tokenStream = search_analyzer.tokenStream(searchField, str);
            String dataValue = matchedDoc.getField(DataField).stringValue();
            List<QTMatch> matches = QTextFragment.getBestTextFragments(tokenStream, query,
                    str, dataValue, vocab_name);

            if (matches.size() == 0) continue;
            allMatches.addAll(matches);
        }

        ArrayList<QTMatch> matchs_sorted_by_length = new ArrayList<>(allMatches);
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
        if (synonymPairs == null || synonymPairs.size() == 0
        || analyzType.equals(DictSearch.AnalyzType.LETTER)) return index_analyzer;

        CharArraySet stopWords_charArray = stopwords == null || stopwords.size() == 0?
                CharArraySet.EMPTY_SET : new CharArraySet(stopwords, false);
        try {
            SynonymMap.Builder builder = new SynonymMap.Builder(true);
            // first argument is mapped to second
            for (String s : synonymPairs) {
                String[] parts = s.split("\\t");
                if (parts.length != 2) continue;

                String[] inTokens = tokenize(index_analyzer, parts[0]);
                if (inTokens == null || inTokens.length == 0){
                    logger.error("p[0] {} wasn't accepted as a synonym", parts[0]);
                    continue;
                }
                String[] outToens = tokenize(index_analyzer, parts[1]);
                if (outToens == null || outToens.length == 0){
                    logger.error("p[1] {} wasn't accepted as a synonym", parts[0]);
                    continue;
                }
                CharsRefBuilder inputCharsRef = new CharsRefBuilder();
                SynonymMap.Builder.join(inTokens, inputCharsRef);

                CharsRefBuilder outputCharsRef = new CharsRefBuilder();
                SynonymMap.Builder.join(outToens, outputCharsRef);

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
                            LetterTokenizer letterTokenizer_s = new LetterTokenizer();
                            tokenStream = new LowerCaseFilter(letterTokenizer_s);
                            tokenStreamComponents = new TokenStreamComponents(letterTokenizer_s, tokenStream);
                            break;
                        case STANDARD:
                            StandardTokenizer standardTokenizer = new StandardTokenizer();
                            tokenStream = new LowerCaseFilter(standardTokenizer);
                            tokenStream = new StopFilter(tokenStream, stopWords_charArray);
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
                                         boolean isFuzzy,
                                         boolean ordered)
    {
        Query q = getMultimatcheQuery(analyzer, fld, query);
        String query_dsl = q.toString();
        AtomicInteger parse_start = new AtomicInteger();
        SpanQuery spanQuery = parse(query_dsl, fld, slop, parse_start, !query_dsl.startsWith("("), isFuzzy, ordered);
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
                bqueryBuilder.add(new FuzzyQuery(new Term(fld, term), 2, 2, 50, false), BooleanClause.Occur.SHOULD);
            }
            tokens.end();
            tokens.close();
        } catch (Exception e){
            e.printStackTrace();
        }

        return bqueryBuilder.build();
    }

    protected static SpanQuery parse(String query,
                                     String search_fld,
                                     int slop,
                                     AtomicInteger idx,
                                     boolean operator_is_and,
                                     boolean is_Fuzzy,
                                     boolean ordered)
    {
        LinkedHashSet<SpanQuery> queryList = new LinkedHashSet<>();
        String fld = "";
        StringBuilder str = new StringBuilder();

        while (idx.get() < query.length()){
            int current_idx = idx.get();
            char c = query.charAt(current_idx);
            idx.incrementAndGet();
            switch (c) {
                case '+':
                    break;
                case ' ':
                    //check if this start of a field or continuation of token str
                    int index_of_next_colon = query.indexOf(':', current_idx+1);
                    if (index_of_next_colon < 0) {// this is the end of the string
                        str.append(c);
                        break;
                    }
                    int index_of_next_space = query.indexOf(' ', current_idx+1);
                    int index_of_next_paran = query.indexOf('(', current_idx+1);

                    if (index_of_next_paran == -1){
                        index_of_next_paran = query.length();
                    }
                    if (index_of_next_space == -1){
                        index_of_next_space = query.length();
                    }

                    if (index_of_next_colon < index_of_next_space || index_of_next_paran < index_of_next_space) {
                        // so next string is a field
                        if (fld.length() > 0 && str.length() > 0) {
                            Term term = new Term(fld, str.toString());
                            SpanQuery spanQuery = is_Fuzzy ? new SpanMultiTermQueryWrapper(new FuzzyQuery(term))
                                    : new SpanTermQuery(term);
                            fld = "";
                            str = new StringBuilder();
                            queryList.add(spanQuery);
                        }
                    } else {
                        str.append(c);
                    }
                    break;

                case ':':
                    fld = str.toString();
                    str = new StringBuilder();
                    break;
                case '(':
                    operator_is_and = false;
                    SpanQuery spanQuery = parse(query, search_fld, slop, idx, operator_is_and, is_Fuzzy, ordered);
                    queryList.add(spanQuery);
                    str = new StringBuilder();
                    break;
                case ')':
                    if (fld.length() > 0 && str.length() > 0) {
                        String q_string = str.toString();
                        Term term = new Term(fld, q_string);
                        SpanQuery spanTQuery = is_Fuzzy && q_string.length() > 4 ? new SpanMultiTermQueryWrapper(new FuzzyQuery(term))
                            : new SpanTermQuery(term);
                        queryList.add(spanTQuery);
                    }
                    if (queryList.size() == 0) {
                        logger.error("This is an empty Span..");
                        return null;
                    } else if (queryList.size() == 1) {
                        return queryList.iterator().next();
                    } else {
                        SpanQuery currentSpanQuery = joinSpanQueryList(queryList,
                                search_fld, slop, operator_is_and, ordered);
                        return currentSpanQuery;
                    }
                default:
                    str.append(c);
            }
        }

        if (fld.length() > 0 && str.length() > 0) {
            String q_string = str.toString();
            Term term = new Term(fld, q_string);
            SpanQuery spanQuery = is_Fuzzy && q_string.length() > 4 ? new SpanMultiTermQueryWrapper(new FuzzyQuery(term)) :
                    new SpanTermQuery(term);
            queryList.add(spanQuery);
        }

        if (queryList.size() == 0) return null;
        if (queryList.size() == 1) {
            return queryList.iterator().next();
        } else {
            SpanQuery squery = joinSpanQueryList(queryList, search_fld, slop, !query.startsWith("("), ordered);
            return squery;
        }
    }

    private static SpanQuery joinSpanQueryList(LinkedHashSet<SpanQuery> queryList,
                                        String search_fld,
                                        int slop,
                                        boolean operator_is_and,
                                        boolean ordered){
        if (!operator_is_and) {
            return new SpanOrQuery(queryList.toArray(new SpanQuery[queryList.size()]));
        }
        SpanNearQuery.Builder spanNearBuilder = new SpanNearQuery.Builder(search_fld, ordered);
        spanNearBuilder.setSlop(slop);
        for (SpanQuery sq : queryList) {
            spanNearBuilder.addClause(sq);
        }
        return spanNearBuilder.build();
    }
}
