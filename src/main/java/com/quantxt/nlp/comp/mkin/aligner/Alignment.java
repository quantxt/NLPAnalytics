package com.quantxt.nlp.comp.mkin.aligner;

import java.util.ArrayList;
import java.util.HashSet;

public class Alignment {

	// Words in strings
	public String[] words1;
	public String[] words2;

	// Function word indices
	public HashSet<Integer> line1FunctionWords;
	public HashSet<Integer> line2FunctionWords;

	// matches[i] contains a match starting at index i in line2
	public Match[] matches;

	// Match totals
	public int line1Matches;
	public int line2Matches;

	// Per-module match totals (Content)
	public ArrayList<Integer> moduleContentMatches1;
	public ArrayList<Integer> moduleContentMatches2;

	// Per-module match totals (Function)
	public ArrayList<Integer> moduleFunctionMatches1;
	public ArrayList<Integer> moduleFunctionMatches2;

	// Chunks
	public int numChunks;
	public double avgChunkLength;

	// Lines as Strings
	public Alignment(String line1, String line2) {
		words1 = tokenize(line1);
		words2 = tokenize(line2);
		initData(words2.length);
	}

	// Lines as ArrayLists of tokenized lowercased Strings
	public Alignment(ArrayList<String> words1, ArrayList<String> words2) {
		this.words1 = words1.toArray(new String[words1.size()]);
		this.words2 = words2.toArray(new String[words2.size()]);
		initData(this.words2.length);
	}

	// Initialize values
	private void initData(int words2length) {
		line1FunctionWords = new HashSet<>();
		line2FunctionWords = new HashSet<>();

		matches = new Match[words2length];

		line1Matches = 0;
		line2Matches = 0;

		moduleContentMatches1 = new ArrayList<>();
		moduleContentMatches2 = new ArrayList<>();

		moduleFunctionMatches1 = new ArrayList<>();
		moduleFunctionMatches2 = new ArrayList<>();

		numChunks = 0;
		avgChunkLength = 0;
	}

	// Tokenize input line
	private String [] tokenize(String line) {
		return line.split("\\s");
	}

	public void printMatchedPhrases() {
		System.out.println(words1);
		System.out.println(words2);
		for (Match m : matches) {
			if (m != null) {
				String s = m.getModule() + " : ";
				for (int i = m.getStart(); i < m.getStart() + m.getLength(); i++)
					s += words2[i] + " ";
				s += "== ";
				for (int i = m.getMatchStart(); i < m.getMatchStart() + m.getMatchLength(); i++)
					s += words1[i] + " ";
				System.out.println(s.trim());
			}
		}
	}

	public String toString() {
		return toString("Alignment");
	}

	public String toString(String header) {
		StringBuilder out = new StringBuilder();
		out.append(header + "\n");
		StringBuilder test = new StringBuilder();
		for (String s : words1)
			test.append(s + " ");
		out.append(test.toString().trim() + "\n");
		StringBuilder ref = new StringBuilder();
		for (String s : words2)
			ref.append(s + " ");
		out.append(ref.toString().trim() + "\n");
		out.append("Line2Start:Length\tLine1Start:Length\tModule\t\tScore\n");
		for (int j = 0; j < matches.length; j++) {
			Match m = matches[j];
			if (m != null) {
				// Second string word
				out.append(m.getStart() + ":" + m.getLength() + "\t\t\t");
				// First string word
				out.append(m.getMatchStart() + ":" + m.getMatchLength() + "\t\t\t");
				// Module stage
				out.append(m.getModule() + "\t\t");
				// Score
				out.append(m.getProb() + "\n");
			}
		}
		return out.toString();
	}
}
