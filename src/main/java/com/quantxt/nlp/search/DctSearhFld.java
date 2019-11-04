package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.types.DictItm;
import com.quantxt.types.DictSearch;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.util.ArrayList;
import java.util.List;

import static com.quantxt.nlp.search.SearchUtils.getExactCaseInsensetiveAnalyzer;
import static com.quantxt.nlp.search.SearchUtils.getSynonymAnalyzer;

@Setter
@Getter
public class DctSearhFld {

    final public static FieldType SearchFieldType;
    final public static FieldType DataFieldType;
    final public static String DataField    = "DataField";
    final public static String searchFieldPfx  = "searchfield";

    static {

        BooleanQuery.setMaxClauseCount(15000);

        DataFieldType = new FieldType();
        DataFieldType.setStored(true);
        DataFieldType.setIndexOptions(IndexOptions.NONE);
        DataFieldType.freeze();

        SearchFieldType = new FieldType();
        SearchFieldType.setStoreTermVectors(true);
        SearchFieldType.setStoreTermVectorPositions(true);
        SearchFieldType.setStored(true);
        SearchFieldType.setTokenized(true);
        SearchFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        SearchFieldType.freeze();
    }



    final private List<DictItm> dictionary_items;
    final private IndexSearcher indexSearcher;
    final private Analyzer search_analyzer;
    final private Analyzer index_analyzer;
    final private DictSearch.AnalyzType analyzType;
    final private DictSearch.Mode mode;
    final private String search_fld;


    public DctSearhFld(QTDocument.Language lang,
                       ArrayList<String> synonymPairs,
                       DictSearch.Mode mode,
                       DictSearch.AnalyzType analyzType,
                       List<DictItm> dictionary_items)
    {
        this.analyzType = analyzType;
        this.mode = mode;
        switch (analyzType){
            case EXACT:
                this.search_fld = searchFieldPfx + ".exact";
                this.index_analyzer = new KeywordAnalyzer();
                break;
            case EXACT_CI:
                this.search_fld = searchFieldPfx + ".exact_ci";
                this.index_analyzer = getExactCaseInsensetiveAnalyzer();
                break;
            case WHITESPACE:
                this.search_fld = searchFieldPfx + ".whitespace";
                this.index_analyzer = new WhitespaceAnalyzer();
                break;
            case SIMPLE:
                this.search_fld = searchFieldPfx + ".simple";
                this.index_analyzer = new SimpleAnalyzer();
                break;
            case STANDARD:
                this.search_fld = searchFieldPfx;
                this.index_analyzer = new StandardAnalyzer();
                break;
            case STEM: {
                this.search_fld = searchFieldPfx + ".stem";
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
            default:
                this.index_analyzer = new StandardAnalyzer();
                this.search_fld = searchFieldPfx;
        }
        this.dictionary_items = dictionary_items;
        this.indexSearcher = getSearcherFromEntities();
        this.search_analyzer = getSynonymAnalyzer(synonymPairs, analyzType, index_analyzer);
    }

    private IndexSearcher getSearcherFromEntities()
    {
        IndexWriterConfig config = new IndexWriterConfig(index_analyzer);
        Directory mMapDirectory = new ByteBuffersDirectory();
        try {
            IndexWriter writer = new IndexWriter(mMapDirectory, config);

            for (DictItm dictItm : dictionary_items) {

                String item_key = dictItm.getKey();
                List<String> item_vals = dictItm.getValue();

                for (String iv : item_vals) {
                    Document doc = new Document();
                    doc.add(new Field(search_fld, iv, SearchFieldType));
                    doc.add(new Field(DataField, item_key, DataFieldType));
                    writer.addDocument(doc);
                }
            }

            writer.close();
            DirectoryReader dreader = DirectoryReader.open(mMapDirectory);
            return new IndexSearcher(dreader);
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
