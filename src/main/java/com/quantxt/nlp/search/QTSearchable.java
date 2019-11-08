package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.helper.types.QTMatch;
import com.quantxt.types.DictSearch;
import com.quantxt.types.Dictionary;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.quantxt.nlp.search.SearchUtils.*;

@Getter
@Setter
public class QTSearchable extends QTSearchableBase<QTMatch> {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);

    public QTSearchable(Dictionary dictionary) {
        super(dictionary);
    }

    public QTSearchable(Dictionary dictionary,
                            QTDocument.Language lang,
                            List<String> synonymPairs,
                            List<String> stopWords,
                            DictSearch.Mode mode,
                            DictSearch.AnalyzType analyzType) {
        super(dictionary, lang, synonymPairs, stopWords, mode, analyzType);
    }

    @Override
    public List<QTMatch> search(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        ArrayList<QTMatch> res = new ArrayList<>();

        try {

            for (Map.Entry<String, List<DctSearhFld>> dctSearhFldEntry : docSearchFldMap.entrySet()) {
                List<DctSearhFld> dctSearhFldList = dctSearhFldEntry.getValue();
                String vocab_name = dctSearhFldEntry.getKey();
                List<Document> matchedDocs = new ArrayList<>();
                for (DctSearhFld dctSearhFld : dctSearhFldList) {
                    String search_fld = dctSearhFld.getSearch_fld();
                    Query query = getMultimatcheQuery(dctSearhFld.getSearch_analyzer(), search_fld, escaped_query);
                    TopDocs topdocs = indexSearcher.search(query, topN);

                    for (ScoreDoc hit : topdocs.scoreDocs) {
                        int id = hit.doc;
                        Document doclookedup = indexSearcher.doc(id);
                        matchedDocs.add(doclookedup);
                    }

                    if (matchedDocs.size() == 0) continue;
                    for (Mode m : mode) {
                        res.addAll(getFragments(matchedDocs, m, minFuzzyTermLength,
                                dctSearhFld.getIndex_analyzer(), dctSearhFld.getSearch_analyzer(),
                                search_fld, vocab_name, query_string));
                    }
                }
            }


        } catch (Exception e ){
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }

        return res;
    }

}