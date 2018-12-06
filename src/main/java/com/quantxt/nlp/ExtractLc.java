package com.quantxt.nlp;

import com.google.gson.Gson;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.Token;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by matin on 12/2/18.
 */
public class ExtractLc {

    final private static Logger logger = LoggerFactory.getLogger(ExtractLc.class);


    final private static String English  = "english";
    final private static String Standard = "standard";
    final private static String Keyword  = "keyword";
    final private static Analyzer EnglishAnalyzer = new EnglishAnalyzer();
    final private static Analyzer StandardAnalyzer = new StandardAnalyzer();
    final private static FieldType QFieldType;

    private IndexSearcher indexSearcher;
    private ArrayList<Object> customeData;
    boolean orSearch = false;

    static {
        QFieldType = new FieldType();
        QFieldType.setStored(true);
        QFieldType.setStoreTermVectors(true);
        QFieldType.setStoreTermVectorOffsets(true);
        QFieldType.setStoreTermVectorPositions(true);
        QFieldType.setTokenized(true);
        QFieldType.setStoreTermVectorPayloads(true);
        QFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        QFieldType.freeze();
    }

    public ExtractLc(){

    }


    public static void main(String[] args) throws Exception {
        String str = "For you I'm only a fox like a hundred thousand other foxes. " +
                "But if you tame me, we'll need each other. " +
                "You'll be the only boy in the world for me. " +
                "I'll be the only fox in the world for you.";


        String q = "only";
        ExtractLc lnindex = new ExtractLc();

        Map<String, Analyzer> analyzerMap = new HashMap<>();
        analyzerMap.put(Standard, StandardAnalyzer);
        analyzerMap.put(English, EnglishAnalyzer);
        PerFieldAnalyzerWrapper pfaw = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

        IndexWriterConfig iwc = new IndexWriterConfig(pfaw);
        Directory ramDirectory = new RAMDirectory();
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter writer = new IndexWriter(ramDirectory, iwc);


        Field fld;
        Document doc = new Document();

        fld = new Field(English, str, QFieldType);
        fld.setTokenStream(EnglishAnalyzer.tokenStream(English, str));
        doc.add(fld);

        fld = new Field(Standard, str, QFieldType);
        fld.setTokenStream(StandardAnalyzer.tokenStream(Standard, str));
        doc.add(fld);

        writer.addDocument(doc);
        writer.close();

        DirectoryReader dreader = DirectoryReader.open(ramDirectory);
        lnindex.indexSearcher = new IndexSearcher(dreader);
        Terms ddd = lnindex.indexSearcher.getIndexReader().getTermVector(0, Standard);


        QueryParser qp_1 = new QueryParser(Standard, StandardAnalyzer);

        PhraseQuery.Builder qp_2 = new PhraseQuery.Builder();
        qp_2.setSlop(1);
        qp_2.add(new Term(Standard, q));


        BooleanQuery.Builder bqueryBuilder = new BooleanQuery.Builder();
        bqueryBuilder.add(qp_1.parse(q), BooleanClause.Occur.SHOULD);

        TopDocs res = lnindex.indexSearcher.search(bqueryBuilder.build(), 4);

        Tokenizer tokenizer = new StandardTokenizer();
        CharTermAttribute cattr = tokenizer.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute pattr = tokenizer.addAttribute(PositionIncrementAttribute.class);
        OffsetAttribute oattr = tokenizer.addAttribute(OffsetAttribute.class);


        for (ScoreDoc hit : res.scoreDocs) {
            int id = hit.doc;
            float score = hit.score;
            Document doclookedup = lnindex.indexSearcher.doc(id);
            tokenizer.setReader(new StringReader(doclookedup.get(Standard)));
            tokenizer.reset();
            int count = 0;
            while (tokenizer.incrementToken()) {
                count += pattr.getPositionIncrement();
                logger.info(cattr.toString() + " | {" + oattr.startOffset() + " , " + oattr.endOffset() +"}" + " | " + count);
            }
            tokenizer.end();

            logger.info(id + " " + score);
        }

    }

}
