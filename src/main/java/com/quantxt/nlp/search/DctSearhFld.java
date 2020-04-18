package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.types.DictSearch;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.BooleanQuery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.quantxt.nlp.search.SearchUtils.*;

public class DctSearhFld implements Serializable {

    private static final long serialVersionUID = -1000275390599103497L;

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

    final private transient Analyzer search_analyzer;
    final private transient Analyzer mirror_synonym_search_analyzer;
    final private transient Analyzer index_analyzer;
    final private DictSearch.AnalyzType analyzType;
    final private DictSearch.Mode mode;
    final private String search_fld;
    private int priority;


    public DctSearhFld(QTDocument.Language lang,
                       List<String> synonymPairs,
                       List<String> stopwords,
                       DictSearch.Mode mode,
                       DictSearch.AnalyzType analyzType,
                       String customPfx)
    {
        CharArraySet stopWords_charArray = stopwords == null || stopwords.size() == 0?
                CharArraySet.EMPTY_SET : new CharArraySet(stopwords, false);
        String pfx = customPfx == null ? searchFieldPfx : customPfx;
        this.analyzType = analyzType;
        this.mode = mode;
        switch (mode) {
            case ORDERED_SPAN: priority = 1000; break;
            case SPAN: priority = 100; break;
            case FUZZY_ORDERED_SPAN: priority = 10; break;
            case FUZZY_SPAN: priority = 0; break;
            default: priority = 0;

        }
        switch (analyzType){
            case EXACT:
                this.search_fld = pfx + ".exact";
                this.index_analyzer = new KeywordAnalyzer();
                this.priority += 0;
                break;
            case EXACT_CI:
                this.search_fld = pfx + ".exact_ci";
                this.index_analyzer = getExactCaseInsensetiveAnalyzer();
                this.priority += 1;
                break;
            case WHITESPACE:
                this.search_fld = pfx + ".whitespace";
                this.index_analyzer = new WhitespaceAnalyzer();
                this.priority += 2;
                break;
            case SIMPLE:
                this.search_fld = pfx + ".simple";
        //        this.index_analyzer = new SimpleAnalyzer();
                this.index_analyzer = getNgramAnalyzer(stopWords_charArray);
                this.priority += 3;
                break;
            case STANDARD:
                this.search_fld = pfx;
                this.index_analyzer = new StandardAnalyzer(stopWords_charArray);
                this.priority += 4;
                break;
            case STEM: {
                this.search_fld = pfx + ".stem";
                this.priority += 5;
                switch (lang) {
                    case ENGLISH:
                        this.index_analyzer = new EnglishAnalyzer(stopWords_charArray);
                        break;
                    case SPANISH:
                        this.index_analyzer = new SpanishAnalyzer(stopWords_charArray);
                        break;
                    case RUSSIAN:
                        this.index_analyzer = new RussianAnalyzer(stopWords_charArray);
                        break;
                    case JAPANESE:
                        this.index_analyzer = new JapaneseAnalyzer();
                        break;
                    case FRENCH:
                        this.index_analyzer = new FrenchAnalyzer(stopWords_charArray);
                        break;
                    default:
                        this.index_analyzer = new EnglishAnalyzer(stopWords_charArray);
                }
            }
            break;
            default:
                this.index_analyzer = new StandardAnalyzer(stopWords_charArray);
                this.search_fld = pfx;
                this.priority += 4;
        }
        this.search_analyzer = getSynonymAnalyzer(synonymPairs, stopwords, analyzType, index_analyzer);
        if (synonymPairs != null && synonymPairs.size() >0){
            List<String> bisideSynonymPairs = new ArrayList<>(synonymPairs);
            for (String s : synonymPairs) {
                String[] parts = s.split("\\t");
                if (parts.length != 2) continue;
                bisideSynonymPairs.add(parts[1]+"\t" + parts[0]);
            }
            this.mirror_synonym_search_analyzer = getSynonymAnalyzer(bisideSynonymPairs, stopwords, analyzType, index_analyzer);
        } else {
            this.mirror_synonym_search_analyzer = this.search_analyzer;
        }
    }

    public Analyzer getIndex_analyzer() {
        return index_analyzer;
    }

    public Analyzer getMirror_synonym_search_analyzer() {
        return mirror_synonym_search_analyzer;
    }

    public Analyzer getSearch_analyzer() {
        return search_analyzer;
    }

    public DictSearch.AnalyzType getAnalyzType() {
        return analyzType;
    }

    public int getPriority() {
        return priority;
    }

    public DictSearch.Mode getMode() {
        return mode;
    }

    public String getSearch_fld() {
        return search_fld;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
