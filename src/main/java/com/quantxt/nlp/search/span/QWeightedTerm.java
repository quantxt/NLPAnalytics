package com.quantxt.nlp.search.span;

public class QWeightedTerm {
    float weight; // multiplier
    String term; // stemmed form

    public QWeightedTerm(float weight, String term) {
        this.weight = weight;
        this.term = term;
    }

    /** @return the term value (stemmed) */
    public String getTerm() {
        return term;
    }

    /** @return the weight associated with this term */
    public float getWeight() {
        return weight;
    }

    /** @param term the term value (stemmed) */
    public void setTerm(String term) {
        this.term = term;
    }

    /** @param weight the weight associated with this term */
    public void setWeight(float weight) {
        this.weight = weight;
    }
}