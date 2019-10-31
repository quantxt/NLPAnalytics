package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.trie.Emit;
import com.quantxt.types.DictItm;
import com.quantxt.types.Dictionary;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
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

import static com.quantxt.types.Dictionary.AnalyzType.STANDARD;
import static org.apache.lucene.analysis.CharArraySet.EMPTY_SET;

@Getter
@Setter
public class QTAdvancedSearch extends Dictionary {

    final private static Logger logger = LoggerFactory.getLogger(QTAdvancedSearch.class);
    final public static String HIDDEH_ENTITY = "hidden";

    final private static String dataField    = "dataField";
    final private static String searchField  = "searchfield";

    final private static FieldType SearchField;
    final private static FieldType DataField;

    static {

        BooleanQuery.setMaxClauseCount(15000);

        DataField = new FieldType();
        DataField.setStored(true);
        DataField.setIndexOptions(IndexOptions.NONE);
        DataField.freeze();

        SearchField = new FieldType();
        SearchField.setStoreTermVectors(true);
        SearchField.setStoreTermVectorPositions(true);
        SearchField.setStored(true);
        SearchField.setTokenized(true);
        SearchField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        SearchField.freeze();
    }

    private int topN = 100;

    private Map<String, IndexSearcher> indexSearcherMap = new HashMap<>();
    final private Analyzer search_analyzer;
    private Analyzer index_analyzer;

    public QTAdvancedSearch(){
        this.analyzType = STANDARD;
        this.mode = Dictionary.Mode.ORDERED_SPAN;
        this.index_analyzer = getTypeAnalyzer(STANDARD);
        this.search_analyzer = this.index_analyzer;
    }

    public QTAdvancedSearch(QTDocument.Language lang,
                            ArrayList<String> synonymPairs)
    {
        switch (analyzType) {
            case SIMPLE: this.index_analyzer     = new SimpleAnalyzer(); break;
            case STANDARD: this.index_analyzer   = new StandardAnalyzer(); break;
            case WHITESPACE: this.index_analyzer = new WhitespaceAnalyzer(); break;
            case STEM: {
                switch (lang) {
                    case ENGLISH:
                        this.index_analyzer = new EnglishAnalyzer();
                        break;
                    case SPANISH:
                        this.index_analyzer = new SpanishAnalyzer();
                        break;
                    case RUSSIAN:
                        this.index_analyzer = new RussianAnalyzer();
                        break;
                    case JAPANESE:
                        this.index_analyzer = new JapaneseAnalyzer();
                        break;
                    case FRENCH:
                        this.index_analyzer = new FrenchAnalyzer();
                        break;
                    default:
                        this.index_analyzer = new EnglishAnalyzer();
                }
            }
            break;
        }
        this.search_analyzer = getSynonymAnalyzer(synonymPairs, index_analyzer);
    }


    protected Analyzer getTypeAnalyzer(Dictionary.AnalyzType analyzType){
        Analyzer analyzer = null;
        switch (analyzType) {
            case SIMPLE: analyzer     = new SimpleAnalyzer(); break;
            case STANDARD: analyzer   = new StandardAnalyzer(); break;
            case WHITESPACE: analyzer = new WhitespaceAnalyzer(); break;
            case STEM: analyzer       = new EnglishAnalyzer(); break;
        }
        return analyzer;
    }

    protected Analyzer getSynonymAnalyzer(ArrayList<String> synonymPairs,
                                          Analyzer index_analyzer)
    {
        if (synonymPairs == null || synonymPairs.size() == 0) return index_analyzer;
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
                        case WHITESPACE:
                            WhitespaceTokenizer whitespaceTokenizer = new WhitespaceTokenizer();
                            TokenStream tokenStream = new CachingTokenFilter(whitespaceTokenizer);
                            TokenStream sysfilter = new SynonymGraphFilter(tokenStream, map, false);
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
                            tokenStream = new StopFilter(tokenStream, new CharArraySet(EMPTY_SET, true));
                            sysfilter = new SynonymGraphFilter(tokenStream, map, true);
                            tokenStreamComponents = new TokenStreamComponents(standardTokenizer, sysfilter);
                            break;
                        case STEM:
                            standardTokenizer = new StandardTokenizer();
                            tokenStream = new EnglishPossessiveFilter(standardTokenizer);
                            tokenStream = new LowerCaseFilter(tokenStream);
                            tokenStream = new StopFilter(tokenStream, new CharArraySet(EMPTY_SET, true));
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

    public Query getPhraseQuery(Analyzer analyzer, String query, int slop) {
        QueryParser qp = new QueryParser(searchField, analyzer);
        Query q = qp.createPhraseQuery(searchField, query, slop);
        return q;
    }

    public Query getMultimatcheQuery(Analyzer analyzer, String query)  {
        QueryParser qp = new QueryParser(searchField, analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.SHOULD;
        Query q = qp.createBooleanQuery(searchField, query, matching_mode);
        return q;
    }

    private String[] getTokenizedString(TokenStream tokenStream) {
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

    protected String[] tokenize(Analyzer analyzer, String str) {
        TokenStream ts = analyzer.tokenStream(null, str);
        return getTokenizedString(ts);
    }


    private SpanQuery parse(String query,
                            int slop,
                            AtomicInteger idx,
                            boolean is_synonym_clause,
                            boolean ordered)
    {
        List<SpanQuery> queryList = new ArrayList<>();
        boolean mode_is_field = true;
        StringBuilder fld = new StringBuilder();
        StringBuilder term = new StringBuilder();

        while (idx.get() < query.length()){
            char c = query.charAt(idx.get());

            switch (c) {
                case '+':
                    idx.incrementAndGet();
                    break;
                case ' ':
                    mode_is_field = true;
                    if (fld.length() > 0 && term.length() > 0) {
                        SpanTermQuery spanTermQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
                        fld = new StringBuilder();
                        term = new StringBuilder();
                        queryList.add(spanTermQuery);
                    }
                    idx.incrementAndGet();
                    break;
                case ':':
                    mode_is_field = false;
                    idx.incrementAndGet();
                    break;
                case '(':
                    idx.incrementAndGet();
                    boolean synonym_clause = false;
                    if (fld.toString().equals("Synonym")){
                        synonym_clause = true;
                    }
                    SpanQuery spanQuery = parse(query, slop, idx, synonym_clause, ordered);
                    queryList.add(spanQuery);
                    break;
                case ')':
                    idx.incrementAndGet();
                    if (fld.length() > 0 && term.length() > 0) {
                        SpanTermQuery spanTQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
                        queryList.add(spanTQuery);
                    }
                    if (queryList.size() == 0) {
                        logger.error("This is an empty Span..");
                        return null;
                    } else if (queryList.size() == 1) {
                        return queryList.get(0);
                    } else {
                        if (is_synonym_clause) {
                            SpanOrQuery spanOrQuery = new SpanOrQuery(queryList.toArray(new SpanQuery[queryList.size()]));
                            return spanOrQuery;
                        } else {
                            SpanNearQuery.Builder spanNearBuilder = new SpanNearQuery.Builder(searchField, ordered);
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
                    idx.incrementAndGet();
            }
        }

        if (fld.length() > 0 && term.length() > 0) {
            SpanTermQuery spanTermQuery = new SpanTermQuery(new Term(fld.toString(), term.toString()));
            queryList.add(spanTermQuery);
        }

        if (queryList.size() == 0) return null;
        if (queryList.size() == 1) {
            return queryList.get(0);
        } else {
            SpanNearQuery.Builder spanNearBuilder = new SpanNearQuery.Builder(searchField, true);
            spanNearBuilder.setSlop(slop);
            for (SpanQuery spanQuery : queryList) {
                spanNearBuilder.addClause(spanQuery);
            }
            return spanNearBuilder.build();
        }
    }

    public SpanQuery getSpanQuery(Analyzer analyzer,
                                  String query,
                                  int slop,
                                  boolean ordered)
    {
        QueryParser qp = new QueryParser(searchField, analyzer);
        BooleanClause.Occur matching_mode = BooleanClause.Occur.SHOULD;
        Query q = qp.createBooleanQuery(searchField, query, matching_mode);
        String query_dsl = q.toString();
        //    logger.info(query_dsl);
        AtomicInteger parse_start = new AtomicInteger();
        SpanQuery spanQuery = parse(query_dsl, slop, parse_start, false, ordered);
        return spanQuery;
    }

    public Query getFuzzyQuery(Analyzer analyzer, String query) throws IOException {
        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        TokenStream tokens = analyzer.tokenStream(searchField, query);
        CharTermAttribute cattr = tokens.addAttribute(CharTermAttribute.class);
        tokens.reset();
        while (tokens.incrementToken()){
            String term = cattr.toString();
            bqueryBuilder.add(new FuzzyQuery(new Term(searchField, term), 1, 2, 50, false), BooleanClause.Occur.MUST);
        }

        tokens.end();
        tokens.close();
        return bqueryBuilder.build();
    }

    private IndexSearcher getIndexSearcher(Analyzer analyzer,
                                           String str) throws IOException {

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(mMapDirectory, config);

        Document doc = new Document();
        doc.add(new Field(searchField, str, SearchField));
        writer.addDocument(doc);
        writer.close();
        DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
        return new IndexSearcher(dreader);
    }

    private Collection<Emit> getFragments(final Collection<Document> matchedDocs,
                                          final String str) throws Exception
    {
        HashSet<Emit> emits = new HashSet<>();
        IndexSearcher searcher = getIndexSearcher(search_analyzer, str);

        //TODO: this is messy; re-factor it with proper multi-field index
        for (Document matchedDoc : matchedDocs) {
            SpanQuery query = null;
            String query_string_raw = matchedDoc.getField(searchField).stringValue();
            String query_string = QueryParser.escape(query_string_raw);
            switch (mode) {
                case ORDERED_SPAN :
                    query = getSpanQuery(search_analyzer , query_string, 1, true);
                    break;
                case SPAN :
                    query = getSpanQuery(search_analyzer , query_string, 1, false);
                    break;
            }
            Spans spans = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                    .getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
            if (spans == null) continue;

            int s = spans.nextDoc();
            int spanstart = spans.nextStartPosition();
            String dataValue = matchedDoc.getField(dataField).stringValue();
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
                        Emit emit = new Emit(startPhraseInStr, endPhraseInStr, keyword);
                        emit.addCustomeData(dataValue);
                        emits.add(emit);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    ts.close();
                }
                spanstart = spans.nextStartPosition();
            }
        }

        ArrayList<Emit> emits_sorted = new ArrayList<>(emits);
        emits_sorted.sort((Emit s1, Emit s2)-> s1.getEnd()- (s2.getEnd()));

        //remove overlaps?
        boolean removeOverlaps = true; //TODO: make this a switch
        if (removeOverlaps) {
            ArrayList<Emit> noOverlapOutput = new ArrayList<>();

            for (int i = 0; i < emits_sorted.size(); i++) {
                final Emit firstEmit = emits_sorted.get(i);
                int firstEmitStart = firstEmit.getStart();
                int firstEmitEnd = firstEmit.getEnd();
                boolean hasAnoverlappigParent = false;
                for (int j = 0; j < emits_sorted.size(); j++) {
                    if (i == j) continue;
                    final Emit secondEmit = emits_sorted.get(j);
                    int secondEmitStart = secondEmit.getStart();
                    int secondEmitEnd = secondEmit.getEnd();
                    if (secondEmitStart >= firstEmitEnd) break;
                    if ((secondEmitStart <= firstEmitStart) && (secondEmitEnd >= firstEmitEnd)) {
                        hasAnoverlappigParent = true;
                        break;
                    }
                }

                if (!hasAnoverlappigParent) {
                    noOverlapOutput.add(firstEmit);
                }
            }
            return noOverlapOutput;
        }

        return new ArrayList<>(emits_sorted);
    }

    private IndexSearcher getSearcherFromEntities(List<DictItm> dictItms) throws IOException
    {
        IndexWriterConfig config = new IndexWriterConfig(index_analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(mMapDirectory, config);

        for (DictItm dictItm : dictItms){

            String item_key = dictItm.getKey();
            List<String> item_vals = dictItm.getValue();

            for (String iv : item_vals) {
                Document doc = new Document();
                doc.add(new Field(searchField, iv, SearchField));
                doc.add(new Field(dataField, item_key, DataField));
                writer.addDocument(doc);
            }
        }

        writer.close();
        DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
        return new IndexSearcher(dreader);
    }

    /*
    private IndexSearcher getSearcherFromEntities(String entType,
                                                  Entity[] entities) throws IOException
    {
        IndexWriterConfig config = new IndexWriterConfig(index_analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(mMapDirectory, config);

        for (Entity entity : entities) {
            // include entity as a speaker?
            if (entity.isSpeaker()) {
                String entity_name = entity.getName();
                String[] alts = entity.getAlts();
                if (alts != null) {
                    for (String alt : alts) {
                        Document doc = new Document();
                        doc.add(new Field(searchField, alt, SearchField));
                        doc.add(new Field(entNameField, entity_name, DataField));
                        doc.add(new Field(entTypeField, entType, DataField));
                        writer.addDocument(doc);
                    }
                } else {
                    Document doc = new Document();
                    doc.add(new Field(searchField, entity_name, SearchField));
                    doc.add(new Field(entNameField, entity_name, DataField));
                    doc.add(new Field(entTypeField, entType, DataField));
                    writer.addDocument(doc);
                }
            }

            List<NamedEntity> namedEntities = entity.getNamedEntities();
            if (namedEntities == null) continue;
            for (NamedEntity namedEntity : namedEntities) {
                namedEntity.setEntity(entType, entity);
                String p_name = namedEntity.getName();
                Document doc = new Document();
                doc.add(new Field(searchField, p_name, SearchField));
                doc.add(new Field(entNameField, p_name, DataField));
                doc.add(new Field(entTypeField, entType, DataField));
                writer.addDocument(doc);

                Set<String> nameAlts = namedEntity.getAlts();
                if (nameAlts != null) {
                    for (String alt : namedEntity.getAlts()) {
                        doc = new Document();
                        doc.add(new Field(searchField, alt, SearchField));
                        doc.add(new Field(entNameField, p_name, DataField));
                        doc.add(new Field(entTypeField, entType, DataField));
                        writer.addDocument(doc);
                    }
                }
            }
        }
        writer.close();
        DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
        return new IndexSearcher(dreader);
    }
    */

    @Override
    public void init(Map<String, List<DictItm>> dictionaries)
    {
        try {
            for (Map.Entry<String, List<DictItm>> dictionary : dictionaries.entrySet()) {
                String dictionary_name = dictionary.getKey();
                List<DictItm> dictionary_vals = dictionary.getValue();
                IndexSearcher indexSearcher = getSearcherFromEntities(dictionary_vals);
                indexSearcherMap.put(dictionary_name, indexSearcher);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /*
    public void init(Map<String, Entity[]> entityMap)
    {
        try {
            for (Map.Entry<String, Entity[]> e : entityMap.entrySet()) {
                String entType = e.getKey();
                Entity[] entities = e.getValue();

                IndexSearcher indexSearcher = getSearcherFromEntities(entType, entities);
                indexSearcherMap.put(entType, indexSearcher);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    */

    @Override
    public Map<String, Collection<Emit>> search(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        HashMap<String, Collection<Emit>> res = new HashMap<>();

        try {

            for (Map.Entry<String, IndexSearcher> e : indexSearcherMap.entrySet()) {
                IndexSearcher indexSearcher = e.getValue();
                String entType = e.getKey();
                Query query = getMultimatcheQuery(search_analyzer, escaped_query);
                TopDocs topdocs = indexSearcher.search(query, topN);

                List<Document> matchedDocs = new ArrayList<>();
                for (ScoreDoc hit : topdocs.scoreDocs) {
                    int id = hit.doc;
                    Document doclookedup = indexSearcher.doc(id);
                    matchedDocs.add(doclookedup);
                }

                if (matchedDocs.size() == 0 ) continue;
                res.put(entType, getFragments(matchedDocs, query_string));
            }

        } catch (Exception e ){
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }
        return res;
    }

    /*
    public static void main(String[] args) throws Exception {
        ArrayList<String> uts = new ArrayList<>();
        uts.add("the cat");
        uts.add("the fox");
        uts.add("in world");
        uts.add("in united states");

        ArrayList<String> synonymMap = new ArrayList<>();
        synonymMap.add("america\tunited states of america");
        synonymMap.add("will be\tshall");
        //     synonymMap.add("fox\troobah");
        //     synonymMap.add("fox\tgobreh");
        synonymMap.add("fox\tgobreh siahe");
        synonymMap.add("fox\tpishi");

        ExtractLc lnindex = new ExtractLc(synonymMap);

        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(lnindex.index_analyzer);
        IndexWriter writer = new IndexWriter(mMapDirectory, config);


        for (String str : uts) {
            Document doc = new Document();
            doc.add(new Field(searchField, str, SearchField));
            doc.add(new Field(entNameField, str, DataField));
            writer.addDocument(doc);
        }
        writer.close();

        DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
        lnindex.phraseTree = new IndexSearcher(dreader);

        String q = "so I will be the only fox in the america for you.";
        lnindex.getSpanQuery(lnindex.search_analyzer, q, 1, true);

        Query query = lnindex.getMultimatcheQuery(lnindex.search_analyzer, q);

        TopDocs res = lnindex.phraseTree.search(query, 40);

        List<Document> matchedDocs = new ArrayList<>();
        HashSet<Integer> uniqIds = new HashSet<>();
        for (ScoreDoc hit : res.scoreDocs) {
            int id = hit.doc;
            if (uniqIds.contains(id)) continue;
            uniqIds.add(id);
            Document doclookedup = lnindex.phraseTree.doc(id);
            matchedDocs.add(doclookedup);
        }

        lnindex.getFragments("NEW", matchedDocs, q);

    }
    */
}
