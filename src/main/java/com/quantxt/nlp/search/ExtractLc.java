package com.quantxt.nlp.search;

import com.google.gson.Gson;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.helper.types.QTField;
import com.quantxt.nlp.topic.word2vec;
import com.quantxt.nlp.topic.Tagger;
import com.quantxt.trie.Emit;
import com.quantxt.types.Dictionary;
import com.quantxt.types.Entity;
import com.quantxt.types.NamedEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.IOUtils;
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

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.quantxt.nlp.search.QTAdvancedSearch.HIDDEH_ENTITY;
import static com.quantxt.types.Dictionary.Mode.ORDERED_SPAN;
import static com.quantxt.types.Dictionary.Mode.SPAN;
import static org.apache.lucene.analysis.CharArraySet.EMPTY_SET;

/**
 * Created by matin on 12/2/18.
 */

@Getter
@Setter
@Deprecated
public class ExtractLc implements QTExtract {

    final private static Logger logger = LoggerFactory.getLogger(ExtractLc.class);

    final private static String entTypeField = "enttypefield";
    final private static String entNameField = "entnamefield";
    final private static String searchField  = "searchfield";

    final private static FieldType SearchField;
    final private static FieldType DataField;

    private QTField.QTFieldType qtFieldType = QTField.QTFieldType.DOUBLE;
    private Pattern pattern;
    private int [] groups;

    private int topN = 100;
    private IndexSearcher phraseTree = null;
    private IndexSearcher hidden_entities;

    private Map<String, IndexSearcher> nameTree = new HashMap<>();

    private List<String> search_terms = new ArrayList<>();

    private transient Tagger tagger = null;
    private Dictionary.Mode mode = ORDERED_SPAN;
    private transient ConcurrentHashMap<String, Double> tokenRank;
    private Dictionary.AnalyzType analyzer_type;

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

    private Analyzer search_analyzer;
    private Analyzer index_analyzer;

    public void setTopN(int i){
        topN = i;
    }

    public void setMode(int i){
        switch (i) {
            case 0 : mode = ORDERED_SPAN; break;
            case 1 : mode = SPAN; break;
        }
    }

    public ExtractLc(QTDocument.Language lang){
        this(Dictionary.AnalyzType.STANDARD);
        if (lang == null) return;
        analyzer_type = Dictionary.AnalyzType.STEM;
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

    public ExtractLc(Dictionary.AnalyzType analyzerType){
        this.analyzer_type = analyzerType;
        switch (analyzerType) {
            case SIMPLE: this.index_analyzer     = new SimpleAnalyzer(); break;
            case STANDARD: this.index_analyzer   = new StandardAnalyzer(); break;
            case WHITESPACE: this.index_analyzer = new WhitespaceAnalyzer(); break;
            case STEM: this.index_analyzer = new EnglishAnalyzer(); break;
        }
        this.search_analyzer = this.index_analyzer;
    }

    public void setSynonyms(ArrayList<String> synonymPairs){
        if (synonymPairs == null || synonymPairs.size() == 0) return;
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

            this.search_analyzer = new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName) {
                    TokenStreamComponents tokenStreamComponents = null;
                    switch (analyzer_type) {
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
        }catch (IOException e) {
            e.printStackTrace();
        }
    }


    public ExtractLc(ArrayList<String> synonymPairs){
        this(Dictionary.AnalyzType.STANDARD);
        setSynonyms(synonymPairs);
    }

    public ExtractLc(Map<String, Entity[]> entities,
                     String taggerDir,
                     InputStream phraseFile) throws IOException
    {
        this(Dictionary.AnalyzType.STANDARD);
        loadEntsAndPhs(entities, phraseFile);
        if (taggerDir != null) {
            tagger = Tagger.load(taggerDir);
            logger.info("Speaker for " + taggerDir + " is created");
        }
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
    //    logger.info(" ++ " + q.toString());
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

    private String[] tokenize(Analyzer analyzer, String str) {
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

    public  Query getFuzzyQuery(Analyzer analyzer, String query) throws IOException {
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

    private Collection<Emit> getFragments(final String entType,
                                          final Collection<Document> matchedDocs,
                                          final String str) throws Exception
    {

        HashSet<Emit> emits = new HashSet<>();
        IndexSearcher searcher = getIndexSearcher(search_analyzer, str);

        //TODO: this is messy; re-factor it with proper multi-field index
        for (Document matchedDoc : matchedDocs) {
            SpanQuery query = null;
            String query_string_raw = matchedDoc.getField(searchField).stringValue();
        //    logger.info("quuu " + query_string_raw);
            String query_string = QueryParser.escape(query_string_raw);
            switch (mode) {
                case ORDERED_SPAN : query = getSpanQuery(search_analyzer , query_string, 1, true); break;
                case SPAN : query = getSpanQuery(search_analyzer , query_string, 1, false); break;
            }
            Spans spans = query.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                            .getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
            if (spans == null) continue;

            int s = spans.nextDoc();
            int spanstart = spans.nextStartPosition();
            String foundValue = matchedDoc.getField(entNameField).stringValue();
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
                        NamedEntity namedEntity = new NamedEntity(foundValue, null);
                        namedEntity.setEntity(entType, new Entity(foundValue, null, true));
                        emit.addCustomeData(namedEntity);
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

    private void loadEntsAndPhs(Map<String, Entity[]> entityMap,
                                InputStream phraseFile) throws IOException
    {
        if (phraseFile != null)
        {
            IndexWriterConfig config = new IndexWriterConfig(index_analyzer);
            Directory mMapDirectory = new ByteBuffersDirectory();
            IndexWriter writer = new IndexWriter(mMapDirectory, config);

            String line;
            int num = 0;
            BufferedReader br = new BufferedReader(new InputStreamReader(phraseFile, "UTF-8"));
            while ((line = br.readLine()) != null) {
                Document doc = new Document();
                doc.add(new Field(searchField, line, SearchField));
                writer.addDocument(doc);
                num++;
            }
            logger.info(num + " phrases loaded for tagging");
            DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
            phraseTree = new IndexSearcher(dreader);
            writer.close();
        }

        Gson gson = new Gson();

        //names
        if (entityMap == null){
            byte[] subjectArr = IOUtils.toByteArray(ExtractLc.class.getClassLoader().getResource("subject.json").openStream());
            entityMap = new HashMap<>();
            entityMap.put("Person", gson.fromJson(new String(subjectArr, "UTF-8"), Entity[].class));
        }

        for (Map.Entry<String, Entity[]> e : entityMap.entrySet()) {
            String entType = e.getKey();
            Entity [] entities = e.getValue();

            for (Entity entity : e.getValue()) {
                String entity_name = entity.getName();
                search_terms.add(entity_name);
            }
            IndexSearcher indexSearcher = getSearcherFromEntities(entType, entities);
            if (entType.equals(HIDDEH_ENTITY)){
                hidden_entities = indexSearcher;
            } else {
                nameTree.put(entType, indexSearcher);
            }
        }
    }

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

    public Map<String, Double> popTokenRank(List<String> tokens, int nums){
        word2vec w2v = tagger.getW2v();
        if (w2v == null) return null;
        tokenRank = new ConcurrentHashMap<>();
        for (String t : tokens) {
            Collection<String> closests = w2v.getClosest(t, nums);
            tokenRank.put(t, 1d);
            for (String c : closests){
                double r = w2v.getDistance(c, t);
                Double r_cur = tokenRank.get(c);
                if (r_cur == null){
                    r_cur = 0d;
                }
                tokenRank.put(c, r + r_cur);
            }
        }
        return tokenRank;
    }

    @Override
    public double[] tag(String s) {
        return new double[0];
    }

    @Override
    public double terSimilarity(String s, String s1) {
        return 0;
    }

    @Override
    public Map<String, Collection<Emit>> parseNames(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        HashMap<String, Collection<Emit>> res = new HashMap<>();

        try {
            if (hidden_entities != null){
                Query query = getMultimatcheQuery(search_analyzer, escaped_query);
                TopDocs topdocs = hidden_entities.search(query, topN);
                List<Document> matchedDocs = new ArrayList<>();
                for (ScoreDoc hit : topdocs.scoreDocs) {
                    int id = hit.doc;
                    Document doclookedup = hidden_entities.doc(id);
                    matchedDocs.add(doclookedup);
                }
                if (matchedDocs.size() > 0 ) {
                    res.put(HIDDEH_ENTITY, getFragments(HIDDEH_ENTITY, matchedDocs, query_string));
                }
            }

            for (Map.Entry<String, IndexSearcher> e : nameTree.entrySet()) {
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
                res.put(entType, getFragments(entType, matchedDocs, query_string));
            }

        } catch (Exception e ){
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }
        return res;
    }

    @Override
    public boolean hasEntities() {
        return false;
    }

    @Override
    public QTField.QTFieldType getType() {
        return null;
    }

    @Override
    public void setType(QTField.QTFieldType qtFieldType) {

    }

    public double getSentenceRank(List<String> parts){
        double rank  = 0;
        for (String p : parts){
            Double d = tokenRank.get(p);
            if (d != null){
                rank += d;
            }
        }
        return rank;
    }
}
