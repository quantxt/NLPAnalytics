package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.model.ExtInterval;
import com.quantxt.model.DictSearch;
import com.quantxt.model.Dictionary;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.nlp.search.SearchUtils.*;

public class QTSearchable extends QTSearchableBase<ExtInterval> {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);

    private int minTermLength = 5;
    private int maxEdits = 2;
    private int prefixLength = 2;

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
    public List<ExtInterval> search(final String query_string, int slop) {
        ArrayList<ExtInterval> res = new ArrayList<>();
        boolean useFuzzyMatching = false;
        for (Mode m : mode){
            if  (m ==  Mode.FUZZY_SPAN || m == Mode.FUZZY_ORDERED_SPAN
                    || m == Mode.PARTIAL_FUZZY_SPAN){
                useFuzzyMatching = true;
                break;
            }
        }

        try {
            // This list is ordered by priorities
            // so if we find an entry that is matched with STANDARD analysis we won't consider it using STEM analysis
            String vocab_name = dictionary.getName();
            String vocab_id = dictionary.getId();
            for (DctSearhFld dctSearhFld : docSearchFldList) {
                String search_fld = dctSearhFld.getSearch_fld();
                Analyzer searchAnalyzer = dctSearhFld.getSearch_analyzer();

                Query query = useFuzzyMatching ? getFuzzyQuery(searchAnalyzer, search_fld, query_string,
                        minTermLength, maxEdits, prefixLength) :
                        getMultimatcheQuery(searchAnalyzer, search_fld, query_string);

                List<Document> matchedDocs = getMatchedDocs(query);
                boolean found = false;
                if (matchedDocs.size() != 0) {
                    for (Mode m : mode) {
                        Collection<ExtInterval> matches = getFragments(matchedDocs, m, slop,
                                searchAnalyzer, dctSearhFld.getMirror_synonym_search_analyzer(),
                                search_fld, vocab_name, vocab_id, query_string);
                        if (matches.size() > 0) {
                            found = true;
                            res.addAll(matches);
                            break;
                        }
                    }
                }
                if (found) break;
            }
        } catch (Exception e ){
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }

        return res;
    }

    @Override
    public List<ExtInterval> search(final String query_string) {
        return  search(query_string, 1);
    }

    public int getMinTermLength(){
        return minTermLength;
    }
    public void setMinTermLength(int minTermLength){
        this.minTermLength = minTermLength;
    }

    public int getMaxEdits() {
        return maxEdits;
    }

    public void setMaxEdits(int maxEdits) {
        this.maxEdits = maxEdits;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }
}
