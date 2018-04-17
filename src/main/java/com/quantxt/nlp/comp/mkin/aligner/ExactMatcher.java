package com.quantxt.nlp.comp.mkin.aligner;

import java.util.HashMap;

public class ExactMatcher {

	public static void match(int stage, Stage s) {

		// Simplest possible matcher: test all word keys for equality

		HashMap<Integer, Integer> w2idx = new HashMap<>();
		for (int j = 0; j < s.words2.length; j++) {
			w2idx.put(s.words2[j], j);
		}

		for (int i = 0; i < s.words1.length; i++) {
			int w1 = s.words1[i];
			Integer j = w2idx.get(w1);
			if (j == null) continue;
			Match m = new Match(j, 1, i, 1, 1, stage);
			// Add this match to the list of matches and mark coverage
			s.matches[j].add(m);
			s.line1Coverage[i]++;
			s.line2Coverage[j]++;
		}

	}
}
