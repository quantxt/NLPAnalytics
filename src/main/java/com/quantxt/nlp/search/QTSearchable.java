package com.quantxt.nlp.search;

import com.quantxt.doc.QTDocument;
import com.quantxt.trie.Emit;
import com.quantxt.types.DictItm;
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

import static com.quantxt.nlp.search.SearchUtils.getFragments;
import static com.quantxt.nlp.search.SearchUtils.getMultimatcheQuery;

@Getter
@Setter
public class QTSearchable extends DictSearch {

    final private static Logger logger = LoggerFactory.getLogger(QTSearchable.class);
    final public static String HIDDEH_ENTITY = "hidden";

    private int topN = 100;

    private Map<String, DctSearhFld> docSearchFldMap = new HashMap<>();
    final private QTDocument.Language lang;
    final private ArrayList<String> synonymPairs;
    final private ArrayList<String> stopWords;


    public QTSearchable(Dictionary dictionary){
        this.lang = null;
        this.synonymPairs = null;
        this.mode = DictSearch.Mode.ORDERED_SPAN;
        this.analyzType = DictSearch.AnalyzType.STANDARD;
        this.dictionary = dictionary;
        this.stopWords = null;
        initDocSearchFldMap(dictionary.getVocab_map());
    }

    public QTSearchable(Dictionary dictionary,
                        QTDocument.Language lang,
                        ArrayList<String> synonymPairs,
                        ArrayList<String> stopWords,
                        DictSearch.Mode mode,
                        DictSearch.AnalyzType analyzType)
    {
        this.lang = lang;
        this.synonymPairs = synonymPairs;
        this.stopWords = stopWords;
        this.mode = mode;
        this.analyzType = analyzType;
        this.dictionary = dictionary;
        initDocSearchFldMap(dictionary.getVocab_map());
    }

    private void initDocSearchFldMap(Map<String, List<DictItm>> vocab_map)
    {
        try {
            for (Map.Entry<String, List<DictItm>> vocab : vocab_map.entrySet()) {
                String vocab_name = vocab.getKey();
                List<DictItm> vocab_items = vocab.getValue();
                DctSearhFld dctSearhFld = new DctSearhFld(lang, synonymPairs, stopWords,
                        mode, analyzType , vocab_items);
                docSearchFldMap.put(vocab_name, dctSearhFld);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, Collection<Emit>> search(final String query_string) {

        String escaped_query = QueryParser.escape(query_string);
        HashMap<String, Collection<Emit>> res = new HashMap<>();

        try {

            for (Map.Entry<String, DctSearhFld> dctSearhFldEntry : docSearchFldMap.entrySet()) {
                DctSearhFld dctSearhFld = dctSearhFldEntry.getValue();
                String search_fld = dctSearhFld.getSearch_fld();
                String vocab_name = dctSearhFldEntry.getKey();
                Query query = getMultimatcheQuery(dctSearhFld.getSearch_analyzer(), search_fld, escaped_query);
                TopDocs topdocs = dctSearhFld.getIndexSearcher().search(query, topN);

                List<Document> matchedDocs = new ArrayList<>();
                for (ScoreDoc hit : topdocs.scoreDocs) {
                    int id = hit.doc;
                    Document doclookedup = dctSearhFld.getIndexSearcher().doc(id);
                    matchedDocs.add(doclookedup);
                }

                if (matchedDocs.size() == 0) continue;
                res.put(vocab_name, getFragments(matchedDocs, mode,
                        dctSearhFld.getIndex_analyzer(), dctSearhFld.getSearch_analyzer(),
                        search_fld, query_string));
            }


        } catch (Exception e ){
            e.printStackTrace();
            logger.error("Error in name search {}: query_string '{}'", e.getMessage() , query_string);
        }

        return res;
    }

}
