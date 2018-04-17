package com.quantxt.nlp.comp.mkin.aligner;

public class ParaphraseMatcher {

	public static void match(int stage,
							 Alignment a,
							 Stage s,
							 ParaphraseTransducer pt) {

		// Create word keys using the paraphrase transducer's dictionary
		int[] line1 = new int[a.words1.length];
		int[] line2 = new int[a.words2.length];

		// Line 1
		for (int i = 0; i < a.words1.length; i++) {
			line1[i] = pt.translate(a.words1[i]);
		}

		// Line 2
		for (int i = 0; i < a.words2.length; i++) {
			line2[i] = pt.translate(a.words2[i]);
		}

		// Paraphrases in line1 of line2
		for (int j = 0; j < line2.length; j++) {

			int[] pars = pt.getReferencePaths(line2, j);

			for (int idx = 0; idx < pars.length; idx += 2) {

				int len = pars[idx];
				int[] words = pt.getParaphrase(pars[idx + 1]);

				for (int i = 0; i < line1.length; i++) {

					boolean match = true;
					for (int k = 0; k < words.length; k++)
						if (i + k == line1.length || line1[i + k] != words[k]) {
							match = false;
							break;
						}
					if (match) {
						Match m = new Match(j, len, i, words.length, 1, stage);
						s.matches[j].add(m);

						for (int k = 0; k < words.length; k++) {
							s.line1Coverage[i + k]++;
						}
						for (int k = 0; k < len; k++) {
							s.line2Coverage[j + k]++;
						}
					}
				}
			}
		}

		// Paraphrases in line2 of line1
		for (int i = 0; i < line1.length; i++) {

			int[] pars = pt.getReferencePaths(line1, i);

			for (int idx = 0; idx < pars.length; idx += 2) {

				int len = pars[idx];
				int[] words = pt.getParaphrase(pars[idx + 1]);

				for (int j = 0; j < line2.length; j++) {

					boolean match = true;
					for (int k = 0; k < words.length; k++)
						if (j + k == line2.length || line2[j + k] != words[k]) {
							match = false;
							break;
						}
					if (match) {
						Match m = new Match(j, words.length, i, len, 1, stage);
						s.matches[j].add(m);

						for (int k = 0; k < len; k++) {
							s.line1Coverage[i + k]++;
						}
						for (int k = 0; k < words.length; k++) {
							s.line2Coverage[j + k]++;
						}
					}
				}
			}
		}
	}
}