package com.quantxt.nlp.comp.meteor.aligner;

/**
 * Universal interface for all kinds of stemmers
 */
public interface Stemmer {
	public String stem(String word);
}
