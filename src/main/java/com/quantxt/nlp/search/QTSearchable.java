package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
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
                List<DctSearhFld> dctSearhFldList = dctSearhFldEntry.getValue();
                String vocab_name = dctSearhFldEntry.getKey();
                for (DctSearhFld dctSearhFld : dctSearhFldList) {
                    String search_fld = dctSearhFld.getSearch_fld();
                    Query query = useFuzzyMatching ? getFuzzyQuery(dctSearhFld.getSearch_analyzer(), search_fld, escaped_query, minTermLength) :
                            getMultimatcheQuery(dctSearhFld.getSearch_analyzer(), search_fld, escaped_query);
                    List<Document> matchedDocs = getMatchedDocs(query);

                    if (matchedDocs.size() == 0) continue;
                    for (Mode m : mode) {
                        res.addAll(getFragments(matchedDocs, m, minFuzzyTermLength,
                                dctSearhFld.getIndex_analyzer(), dctSearhFld.getSearch_analyzer(), dctSearhFld.getMirror_synonym_search_analyzer(),
                                search_fld, vocab_name, query_string));
                    }
                }
            }
        } catch (Exception e ){
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
