package com.quantxt.nlp.comp.mkin.aligner;

import java.util.ArrayList;

public class Stage {

	// Word keys
	public int[] words1;
	public int[] words2;

	// List of matches for each start index
//	public ArrayList<ArrayList<Match>> matches;
	public ArrayList<Match> [] matches;

	// Counts of matches covering each index
	public int[] line1Coverage;
	public int[] line2Coverage;

	public Stage(String[] wordStrings1, String[] wordStrings2) {
		words1 = wordsToKeys(wordStrings1);
		words2 = wordsToKeys(wordStrings2);

		matches = new ArrayList[words2.length];

		for (int i = 0; i < words2.length; i++) {
			matches[i] = new ArrayList<>();
		}

		line1Coverage = new int[words1.length];
		line2Coverage = new int[words2.length];
	}

	private int[] wordsToKeys(String[] words) {
		int[] keys = new int[words.length];
		for (int i = 0; i < words.length; i++)
			// Chance of collision statistically insignificant,
			// no need for dictionary
			keys[i] = words[i].hashCode();
		return keys;
	}
}
