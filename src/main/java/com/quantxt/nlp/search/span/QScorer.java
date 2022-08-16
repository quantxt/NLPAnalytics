package com.quantxt.nlp.search.span;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QScorer implements QTScorer {
    private float totalScore;
    private Set<String> foundTerms;
    private Map<String, QWeightedSpanTerm> fieldWeightedSpanTerms;
    private float maxTermWeight;
    private int position = -1;
    private String defaultField;
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncAtt;
    private boolean expandMultiTermQuery = true;
    private Query query;
    private String field;
    private IndexReader reader;
    private boolean skipInitExtractor;
    private boolean wrapToCaching = true;
    private int maxCharsToAnalyze;
    private boolean usePayloads = false;

    /** @param query Query to use for highlighting */
    public QScorer(Query query) {
        init(query, null, null, true);
    }

    /**
     * @param query Query to use for highlighting
     * @param field Field to highlight - pass null to ignore fields
     */
    public QScorer(Query query, String field) {
        init(query, field, null, true);
    }

    /**
     * @param query Query to use for highlighting
     * @param field Field to highlight - pass null to ignore fields
     * @param reader {@link IndexReader} to use for quasi tf/idf scoring
     */
    public QScorer(Query query, IndexReader reader, String field) {
        init(query, field, reader, true);
    }

    /**
     * @param query to use for highlighting
     * @param reader {@link IndexReader} to use for quasi tf/idf scoring
     * @param field to highlight - pass null to ignore fields
     */
    public QScorer(Query query, IndexReader reader, String field, String defaultField) {
        this.defaultField = defaultField;
        init(query, field, reader, true);
    }

    /** @param defaultField - The default field for queries with the field name unspecified */
    public QScorer(Query query, String field, String defaultField) {
        this.defaultField = defaultField;
        init(query, field, null, true);
    }

    public QScorer(QWeightedSpanTerm[] weightedTerms) {
        this.fieldWeightedSpanTerms = new HashMap<>(weightedTerms.length);

        for (int i = 0; i < weightedTerms.length; i++) {
            QWeightedSpanTerm existingTerm = fieldWeightedSpanTerms.get(weightedTerms[i].term);

            if ((existingTerm == null) || (existingTerm.weight < weightedTerms[i].weight)) {
                // if a term is defined more than once, always use the highest
                // scoring weight
                fieldWeightedSpanTerms.put(weightedTerms[i].term, weightedTerms[i]);
                maxTermWeight = Math.max(maxTermWeight, weightedTerms[i].getWeight());
            }
        }
        skipInitExtractor = true;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.search.highlight.Scorer#getFragmentScore()
     */
    @Override
    public float getFragmentScore() {
        return totalScore;
    }

    /**
     * @return The highest weighted term (useful for passing to GradientFormatter to set top end of
     *     coloring scale).
     */
    public float getMaxTermWeight() {
        return maxTermWeight;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.search.highlight.Scorer#getTokenScore(org.apache.lucene.analysis.Token,
     *      int)
     */
    @Override
    public float getTokenScore() {
        position += posIncAtt.getPositionIncrement();
        String termText = termAtt.toString();

        QWeightedSpanTerm weightedSpanTerm;

        if ((weightedSpanTerm = fieldWeightedSpanTerms.get(termText)) == null) {
            return 0;
        }

        if (weightedSpanTerm.positionSensitive && !weightedSpanTerm.checkPosition(position)) {
            return 0;
        }

        float score = weightedSpanTerm.getWeight();

        // found a query term - is it unique in this doc?
        if (!foundTerms.contains(termText)) {
            totalScore += score;
            foundTerms.add(termText);
        }

        return score;
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.search.highlight.Scorer#init(org.apache.lucene.analysis.TokenStream)
     */
    @Override
    public TokenStream init(TokenStream tokenStream) throws IOException {
        position = -1;
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        posIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        if (!skipInitExtractor) {
            if (fieldWeightedSpanTerms != null) {
                fieldWeightedSpanTerms.clear();
            }
            return initExtractor(tokenStream);
        }
        return null;
    }

    /**
     * Retrieve the {@link QWeightedSpanTerm} for the specified token. Useful for passing Span
     *
     * @param token to get {@link QWeightedSpanTerm} for
     * @return WeightedSpanTerm for token
     */
    public QWeightedSpanTerm getWeightedSpanTerm(String token) {
        return fieldWeightedSpanTerms.get(token);
    }

    /** */
    private void init(Query query, String field, IndexReader reader, boolean expandMultiTermQuery) {
        this.reader = reader;
        this.expandMultiTermQuery = expandMultiTermQuery;
        this.query = query;
        this.field = field;
    }

    private TokenStream initExtractor(TokenStream tokenStream) throws IOException {
        QWeightedSpanTermExtractor qse = newTermExtractor(defaultField);
        qse.setMaxDocCharsToAnalyze(maxCharsToAnalyze);
        qse.setExpandMultiTermQuery(expandMultiTermQuery);
        qse.setWrapIfNotCachingTokenFilter(wrapToCaching);
        qse.setUsePayloads(usePayloads);
        if (reader == null) {
            this.fieldWeightedSpanTerms = qse.getWeightedSpanTerms(query, 1f, tokenStream, field);
        } else {
            this.fieldWeightedSpanTerms =
                    qse.getWeightedSpanTermsWithScores(query, 1f, tokenStream, field, reader);
        }
        if (qse.isCachedTokenStream()) {
            return qse.getTokenStream();
        }

        return null;
    }

    protected QWeightedSpanTermExtractor newTermExtractor(String defaultField) {
        return new QWeightedSpanTermExtractor(defaultField);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.search.highlight.Scorer#startFragment(org.apache.lucene.search.highlight.TextFragment)
     */
    @Override
    public void startFragment(QTextFragment newFragment) {
        foundTerms = new HashSet<>();
        totalScore = 0;
    }

    /** @return true if multi-term queries should be expanded */
    public boolean isExpandMultiTermQuery() {
        return expandMultiTermQuery;
    }

    public void setExpandMultiTermQuery(boolean expandMultiTermQuery) {
        this.expandMultiTermQuery = expandMultiTermQuery;
    }

    public boolean isUsePayloads() {
        return usePayloads;
    }

    public void setUsePayloads(boolean usePayloads) {
        this.usePayloads = usePayloads;
    }

    public void setWrapIfNotCachingTokenFilter(boolean wrap) {
        this.wrapToCaching = wrap;
    }

    public void setMaxDocCharsToAnalyze(int maxDocCharsToAnalyze) {
        this.maxCharsToAnalyze = maxDocCharsToAnalyze;
    }
}