package com.quantxt.nlp;

import com.google.gson.Gson;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.QTExtract;
import com.quantxt.helper.types.QTField;
import com.quantxt.nlp.types.Tagger;
import com.quantxt.trie.Emit;
import com.quantxt.types.Entity;
import com.quantxt.types.NamedEntity;
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
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.CharsRefBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.quantxt.nlp.ExtractLc.Mode.*;
import static com.quantxt.nlp.Speaker.HIDDEH_ENTITY;
import static org.apache.lucene.analysis.CharArraySet.EMPTY_SET;

/**
 * Created by matin on 12/2/18.
 */

public class ExtractLc implements QTExtract {

    final private static Logger logger = LoggerFactory.getLogger(ExtractLc.class);
    final private static Pattern DIGITS = Pattern.compile("\\p{Digit}+");

    enum Mode {
        EXACT_PHRASE, PHRASE, EXACT_SPAN, SPAN, PARTIAL_SPAN
    }

    enum AnalyzerType {
        WHITESPACE, SIMPLE, STANDARD, STEM
    }

    final private static String OPENH  = "<b>";
    final private static String CLOSEH = "</b>";
    final private static int OPENHLENGTH  = OPENH.length();
    final private static int CLOSEHLENGTH = CLOSEH.length();

    final private static String entTypeField = "enttypefield";
    final private static String entNameField = "entnamefield";
    final private static String searchField  = "searchfield";

    final private static FieldType PositionField;
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

    private Tagger tagger = null;
    private Mode mode = PHRASE;   //mode 1
    private ConcurrentHashMap<String, Double> tokenRank;
    private AnalyzerType analyzer_type;

    static {

        BooleanQuery.setMaxClauseCount(15000);

        DataField = new FieldType();
        DataField.setStored(true);
        DataField.setIndexOptions(IndexOptions.NONE);
        DataField.freeze();

        SearchField = new FieldType();
        SearchField.setStored(true);
        SearchField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        SearchField.freeze();

        PositionField = new FieldType();
        PositionField.setStoreTermVectors(true);
        PositionField.setStoreTermVectorPositions(true);
        PositionField.setStored(true);
        PositionField.setTokenized(true);
        PositionField.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        PositionField.freeze();
    }

    private Analyzer search_analyzer;
    private Analyzer index_analyzer;

    public void setTopN(int i){
        topN = i;
    }

    public void setMode(int i){
        switch (i) {
            case 0 : mode = EXACT_PHRASE; break;
            case 1 : mode = PHRASE; break;
            case 2 : mode = EXACT_SPAN; break;
            case 3 : mode = SPAN; break;
            case 4 : mode = PARTIAL_SPAN; break;
        }
    }

    public ExtractLc(QTDocument.Language lang){
        this(AnalyzerType.STANDARD);
        if (lang == null) return;
        analyzer_type = AnalyzerType.STEM;
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

    public ExtractLc(AnalyzerType analyzerType){
        this.analyzer_type = analyzerType;
        switch (analyzerType) {
            case SIMPLE: this.index_analyzer     = new SimpleAnalyzer(); break;
            case STANDARD: this.index_analyzer   = new StandardAnalyzer(); break;
            case WHITESPACE: this.index_analyzer = new WhitespaceAnalyzer(); break;
        }
        this.search_analyzer = this.index_analyzer;
    }

    private String tokenize(Analyzer analyzer, String str){
        TokenStream ts = analyzer.tokenStream(searchField, str);
        CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
        StringBuilder sb = new StringBuilder();
        try {
            ts.reset();

            while (ts.incrementToken()) {
                String term = cattr.toString();
                sb.append(term).append(" ");
            }
            ts.end();
            ts.close();
        } catch (Exception e){

        }
        return sb.toString().trim();
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
                SynonymMap.Builder.join(tokenize(index_analyzer, parts[0]).split(" +"), inputCharsRef);

                CharsRefBuilder outputCharsRef = new CharsRefBuilder();
                SynonymMap.Builder.join(tokenize(index_analyzer, parts[1]).split(" +"), outputCharsRef);
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
        this(AnalyzerType.STANDARD);
        setSynonyms(synonymPairs);
    }

    public ExtractLc(Map<String, Entity[]> entities,
                     String taggerDir,
                     InputStream phraseFile) throws IOException
    {
        this(AnalyzerType.STANDARD);
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

    private void add2all(ArrayList<ArrayList<String>> paths, String t){
        Iterator<ArrayList<String>> arrayIterator = paths.iterator();

        while (arrayIterator.hasNext()){
            ArrayList<String> arr = arrayIterator.next();
            arr.add(t);
        }
    }

    private void dubplicate(ArrayList<ArrayList<String>> paths, String term){
        if (paths.size() == 0) return;
        int lastIndex = paths.get(0).size() -1;
        ArrayList<ArrayList<String>> pathcopy = new ArrayList<>(paths);
        for (ArrayList<String> arr : pathcopy){
            arr.set(lastIndex, term);
        }
        paths.addAll(pathcopy);


    }

    public Query getSpanQuery(Analyzer analyzer,
                              String query,
                              int slop,
                              boolean allTerms) throws IOException {

        TokenStream ts = analyzer.tokenStream(searchField, query);
        CharTermAttribute cattr = ts.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute pattr = ts.addAttribute(PositionIncrementAttribute.class);

        ArrayList<ArrayList<String>> termArrays = new ArrayList<>();
        termArrays.add(new ArrayList<>());
        // get all paths -- flatten the graph
        try {
            ts.reset();

            while (ts.incrementToken()) {
                String term = cattr.toString();

                int positionIncrement = pattr.getPositionIncrement();
                if (positionIncrement == 0){
                    dubplicate(termArrays, term);
                } else {
                    add2all(termArrays, term);
                }
       //         logger.info(term + " " + pattr.getPositionIncrement());
            }

            if (termArrays.size() == 0) return null;
            ts.end();
        } finally {
            ts.close();
        }

        if (termArrays.size() == 1) {
            ArrayList<String> termArray = termArrays.get(0);
            if (termArray.size() > 1) {
                SpanNearQuery.Builder querybuilder = new SpanNearQuery.Builder(searchField, true);
                querybuilder.setSlop(slop);
                for (String term : termArray) {
                    Term t = new Term(searchField, term);
                    querybuilder.addClause(new SpanTermQuery(t));
                }
                Query spanquery = querybuilder.build();
        //        logger.info(spanquery.toString());
                return spanquery;
            } else {
                return new TermQuery(new Term(searchField, termArray.get(0)));
            }
        } else {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            HashSet<String> uniqterms = new HashSet<>();
            BooleanClause.Occur boolean_mode = allTerms ? BooleanClause.Occur.MUST : BooleanClause.Occur.SHOULD;
            for (ArrayList<String> termArray : termArrays){
                String hash = String.join(" " , termArray);
                if (uniqterms.contains(hash))continue;
                uniqterms.add(hash);
                SpanNearQuery.Builder querybuilder = new SpanNearQuery.Builder(searchField, true);
                querybuilder.setSlop(slop);
                for (String term : termArray) {
                    Term t = new Term(searchField, term);
                    querybuilder.addClause(new SpanTermQuery(t));
                }
                Query spanquery = querybuilder.build();
        //        logger.info(spanquery.toString());
                builder.add(spanquery, boolean_mode);
            }
            return builder.build();
        }
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

    private String mergeCloseFragment(String f){
        // if </b> and <b> are nearby
        int start = 0;
        int utteranceLength = f.length();
        while (start >= 0 && start < utteranceLength){
            int endPhraseInFragment = f.indexOf(CLOSEH, start);   // find </b>
            if (endPhraseInFragment < 0) return f;
            int startPhraseInFragment = f.indexOf(OPENH, endPhraseInFragment);   // find <b>
            if (startPhraseInFragment < 0) return f;
            // </b  '>'  vmdn  '<' b>
            start = endPhraseInFragment; //it is at </b '>'
            if (startPhraseInFragment - endPhraseInFragment < (CLOSEHLENGTH + 4)) {// DO NOT HARDCODE THIS NUMBER!! 4 for length of /b>
                //TODO: do some analysis on between to make sure it can be merged
                //TODO: for example if between is a number don't merge it
                String between = f.substring(endPhraseInFragment + CLOSEHLENGTH, startPhraseInFragment);
        //        if (between.length() < 1) continue;
        //        Matcher m = DIGITS.matcher(between);
        //        if (m.find()) continue;
                String f1 = f.substring(0, endPhraseInFragment);
                String f2 = f.substring(startPhraseInFragment+OPENHLENGTH);
                f = f1 + between + f2;
                utteranceLength -= (CLOSEHLENGTH + OPENHLENGTH);
            } else {
                start++;

            }
        }

        return f;

    }

    private ArrayList<Emit> getFragmentsHelper(final String entType,
                                               final String str,
                                               final IndexSearcher searcher,
                                               final Analyzer analyzer,
                                               final Document matchedDoc,
                                               final Query query) throws IOException {
        ArrayList<Emit> emits = new ArrayList<>();

        TopDocs res = searcher.search(query, 1);
        if (res.totalHits.value == 0) return emits;

        int fragmentstart = 0;
        String foundValue = matchedDoc.getField(entNameField).stringValue();

        UnifiedHighlighter uhighlighter = new UnifiedHighlighter(searcher, analyzer);
        String[] fragments = uhighlighter.highlight(searchField, query, res);

        for (String rawFragment : fragments) {
            String f = mergeCloseFragment(rawFragment);
      //      String f = rawFragment;
            String freduced = f.replace(OPENH, "").replace(CLOSEH, "");
            //now start and end on fragment need to be shifted to match str
            int offset = str.indexOf(freduced, fragmentstart);
            if (offset < 0) {// this is impossible!
                logger.error("Can not find fragment in String {} --> {}", f, str);
                continue;
            }

            int start = 0;
            int subFragmentNumber = 0;
            while (start >= 0) {
                int startPhraseInFragment = f.indexOf(OPENH, start);
                if (startPhraseInFragment < 0) break;
                int endPhraseInFragment = f.indexOf(CLOSEH, startPhraseInFragment);
                if (endPhraseInFragment < 0) break;
                subFragmentNumber++;

                int startPhraseInStr = startPhraseInFragment - (OPENHLENGTH + CLOSEHLENGTH) * (subFragmentNumber -1 ) + offset;
                int endPhraseInStr = endPhraseInFragment - (OPENHLENGTH + CLOSEHLENGTH) * (subFragmentNumber -1 ) - OPENHLENGTH + offset;

                fragmentstart += offset;

                String keyword = str.substring(startPhraseInStr, endPhraseInStr);

                //Check if this is partial token and if so extend to the first starting space
                //this has to be a word noundary officially

                logger.debug("KEY {} ---- Fragment {} ", keyword, f);

                Emit emit = new Emit(startPhraseInStr, endPhraseInStr, keyword);
                NamedEntity namedEntity = new NamedEntity(foundValue, null);
                namedEntity.setEntity(entType, new Entity(foundValue, null, true));
                emit.addCustomeData(namedEntity);
                emits.add(emit);
                start = endPhraseInFragment + OPENHLENGTH;
            }
        }
        return emits;
    }

    private IndexSearcher getIndexSearcher(Analyzer analyzer,
                                           String str) throws IOException {

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(mMapDirectory, config);

        Document doc = new Document();
        doc.add(new Field(searchField, str, PositionField));
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
            Query query = null;
            String query_string_raw = matchedDoc.getField(searchField).stringValue();
        //    logger.info("quuu " + query_string_raw);
            String query_string = QueryParser.escape(query_string_raw);
            switch (mode) {
                case EXACT_PHRASE : query = getPhraseQuery(search_analyzer , query_string, 0); break;
                case PHRASE : query = getPhraseQuery(search_analyzer , query_string, 1); break;
                case EXACT_SPAN : query = getSpanQuery(search_analyzer , query_string, 0, true); break;
                case SPAN : query = getSpanQuery(search_analyzer , query_string, 1, true); break;
                case PARTIAL_SPAN : query = getSpanQuery(search_analyzer , query_string, 1, false); break;
            }
            emits.addAll(getFragmentsHelper(entType, str, searcher, search_analyzer, matchedDoc, query));
        }

        ArrayList<Emit> output = new ArrayList<>(emits);
        output.sort((Emit s1, Emit s2)-> s1.getEnd()- (s2.getEnd()));

        //remove overlaps?
        boolean removeOverlaps = true; //TODO: make this a switch
        if (removeOverlaps) {
            ArrayList<Emit> noOverlapOutput = new ArrayList<>();

            for (int i = 0; i < output.size(); i++) {
                final Emit firstEmit = output.get(i);
                int firstEmitStart = firstEmit.getStart();
                int firstEmitEnd = firstEmit.getEnd();
                boolean hasAnoverlappigParent = false;
                for (int j = 0; j < output.size(); j++) {
                    if (i == j) continue;
                    final Emit secondEmit = output.get(j);
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

        return new ArrayList<>(emits);
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
        synonymMap.add("america\tunited states");
        synonymMap.add("will\tshall");
        synonymMap.add("fox\troobah");
        synonymMap.add("fox\tgobreh");
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
     //   String q = "******";
        Query query = lnindex.getMultimatcheQuery(lnindex.search_analyzer, q);
        logger.info(query.toString());
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

    @Override
    public double[] tag(String s) {
        return new double[0];
    }

    @Override
    public double terSimilarity(String s, String s1) {
        return 0;
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
    public Map<String, Collection<Emit>> parseNames(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        HashMap<String, Collection<Emit>> res = new HashMap<>();
    //    HashSet<Integer> uniqDocs = new HashSet<>();

        try {
            if (hidden_entities != null){
                Query query = getMultimatcheQuery(search_analyzer, escaped_query);
                TopDocs topdocs = hidden_entities.search(query, topN);
                List<Document> matchedDocs = new ArrayList<>();
                for (ScoreDoc hit : topdocs.scoreDocs) {
                    int id = hit.doc;
        //            if (uniqDocs.contains(id)) continue;
        //            uniqDocs.add(id);
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
            //        if (uniqDocs.contains(id)) continue;
            //        uniqDocs.add(id);
                    Document doclookedup = indexSearcher.doc(id);
                    matchedDocs.add(doclookedup);
                }

                if (matchedDocs.size() == 0 ) continue;
                res.put(entType, getFragments(entType, matchedDocs, query_string));
            }

        } catch (Exception e ){
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }
        return res;
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

    @Override
    public boolean hasEntities() {
        return nameTree.size() > 0;
    }

    @Override
    public QTField.QTFieldType getType() {
        return qtFieldType;
    }

    @Override
    public void setType(QTField.QTFieldType type) {
        this.qtFieldType = type;
    }

    @Override
    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public void setPattern(Pattern ptr) {
        this.pattern = ptr;
    }

    @Override
    public int[] getGroups() {
        return groups;
    }

    @Override
    public void setGroups(int[] groups) {
        this.groups = groups;
    }
}
