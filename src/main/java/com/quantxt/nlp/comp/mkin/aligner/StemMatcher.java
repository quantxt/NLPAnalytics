package com.quantxt.nlp.comp.mkin.aligner;

import com.quantxt.doc.QTDocumentHelper;

import java.util.List;

public class StemMatcher {

	public static void match(int stage, Alignment a, Stage s, QTDocumentHelper stemmer) {

		// Get keys for word stems
		String[] stems1 = wordsToStemKeys(a.words1, stemmer);
		String[] stems2 = wordsToStemKeys(a.words2, stemmer);

		for (int j = 0; j < stems2.length; j++) {
			for (int i = 0; i < stems1.length; i++) {
				// Match for DIFFERENT words with SAME stems
				if (stems1[i].equals(stems2[j]) /*&& !a.words1[i].equals(a.words2[j])*/) {
					Match m = new Match(j, 1, i, 1, 1, stage);

					// Add this match to the list of matches and mark coverage
					s.matches[j].add(m);
					s.line1Coverage[i]++;
					s.line2Coverage[j]++;
				}
			}
		}
	}

	private static String[] wordsToStemKeys(String [] words,
										 QTDocumentHelper helper) {
		List<String> stems = helper.stemmer(String.join(" ", words));
		return stems.toArray(new String[stems.size()]);
	}
}
