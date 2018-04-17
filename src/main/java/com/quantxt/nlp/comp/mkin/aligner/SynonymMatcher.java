package com.quantxt.nlp.comp.mkin.aligner;

import java.util.*;

public class SynonymMatcher {

	public static void match(int stage,
							 Alignment a,
							 Stage s,
							 final SynonymDictionary synonyms)
	{
		// Map words to sets of synonym set numbers

		ArrayList<HashSet<Integer>> string1Syn = new ArrayList<>();
		ArrayList<HashSet<Integer>> string2Syn = new ArrayList<>();

		// Line 1
		for (int i = 0; i < a.words1.length; i++) {
			HashSet<Integer> set = new HashSet<>(synonyms.getSynSets(a.words1[i]));
			set.addAll(synonyms.getStemSynSets(a.words1[i]));
			string1Syn.add(set);
		}

		// Line 2
		for (int i = 0; i < a.words2.length; i++) {
			HashSet<Integer> set = new HashSet<>(synonyms.getSynSets(a.words2[i]));
			set.addAll(synonyms.getStemSynSets(a.words2[i]));
			string2Syn.add(set);
		}

		for (int j = 0; j < a.words2.length; j++) {
			HashSet<Integer> sets2 = string2Syn.get(j);
			for (int i = 0; i < a.words1.length; i++) {
				if (s.words1[i] == s.words2[j]) continue;
				Iterator<Integer> sets1 = string1Syn.get(i).iterator();

				boolean syn = false;
				double weight = 0;
				while (sets1.hasNext()) {
					if (sets2.contains(sets1.next())) {
						syn = true;
						weight = 1;
						break;
					}
				}

				// Match if DIFFERENT words with SAME synset
				if (syn) {
					Match m = new Match(j, 1, i, 1, weight, stage);
					// Add this match to the list of matches and mark coverage
					s.matches[j].add(m);
					s.line1Coverage[i]++;
					s.line2Coverage[j]++;
				}
			}
		}
	}
}