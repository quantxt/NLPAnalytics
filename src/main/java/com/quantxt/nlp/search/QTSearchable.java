package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.nlp.search.SearchUtils.*;


public class QTSearchable extends QTSearchableBase<QTMatch> {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);

    private int minTermLength = 5;

    public QTSearchable(Dictionary dictionary) {
        super(dictionary);
        this.create();
    }

    public QTSearchable(Dictionary dictionary,
                        QTDocument.Language lang,
                        List<String> synonymPairs,
                        List<String> stopWords,
                        DictSearch.Mode mode,
                        DictSearch.AnalyzType analyzType) {
        super(dictionary, null, lang, synonymPairs, stopWords, mode, analyzType);
        this.create();
    }

    public QTSearchable(Dictionary dictionary,
                        QTDocument.Language lang,
                        List<String> synonymPairs,
                        List<String> stopWords,
                        DictSearch.Mode[] mode,
                        DictSearch.AnalyzType[] analyzType) {
        super(dictionary, null, lang, synonymPairs, stopWords, mode, analyzType);
        this.create();
    }

    @Override
    public List<QTMatch> search(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        ArrayList<QTMatch> res = new ArrayList<>();
        boolean useFuzzyMatching = false;
        for (Mode m : mode){
            if  (m ==  Mode.FUZZY_SPAN || m == Mode.FUZZY_ORDERED_SPAN
                    || m == Mode.PARTIAL_FUZZY_SPAN){
                useFuzzyMatching = true;
                break;
            }
        }
        try {
            for (Map.Entry<String, List<DctSearhFld>> dctSearhFldEntry : docSearchFldMap.entrySet()) {
                // This list is ordered by priorities
                // so if we find an entry that is matched with STANDARD analysis we won't consider it using STEM analysis
                List<DctSearhFld> dctSearhFldList = dctSearhFldEntry.getValue();
                String vocab_name = dctSearhFldEntry.getKey();
                for (DctSearhFld dctSearhFld : dctSearhFldList) {
                    String search_fld = dctSearhFld.getSearch_fld();
                    Analyzer searchAnalyzer = dctSearhFld.getSearch_analyzer();

                    Query query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, escaped_query, minTermLength) :
                            getMultimatcheQuery(searchAnalyzer, search_fld, escaped_query);

                    List<Document> matchedDocs = getMatchedDocs(query);
                    boolean found = false;
                    if (matchedDocs.size() != 0) {
                        for (Mode m : mode) {
                            Collection<QTMatch> matches = getFragments(matchedDocs, m, minFuzzyTermLength,
                                    searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                    search_fld, vocab_name, query_string);
                            if (matches.size() > 0) {
                                found = true;
                                res.addAll(matches);
                                break;
                            }
                        }
                    }
                    if (found) break;
                }
            }
        } catch (Exception e ){
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }

        return res;
    }

    public int getMinTermLength(){
        return minTermLength;
    }
    public void setMinTermLength(int minTermLength){
        this.minTermLength = minTermLength;
    }

}
