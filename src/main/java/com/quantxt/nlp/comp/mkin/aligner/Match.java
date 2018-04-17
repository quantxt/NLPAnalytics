package com.quantxt.nlp.comp.mkin.aligner;

public class Match {

	final private int start; // start of the match (line2)
	final private int length; // length of this match (line2)
	final private int matchStart; // start of this match (line1)
	final private int matchLength; // length of this match (line1)
	final private double prob; // probability supplied by matcher
	final private int module; // module which made this match


	public Match(int s2, int l2, int s1, int l1, double p, int m){
		start = s2;
		length = l2;
		matchStart = s1;
		matchLength = l1;
		prob = p;
		module = m;
	}

	public int getStart(){
		return start;
	}

	public int getModule(){
		return module;
	}

	public int getMatchStart(){
		return matchStart;
	}

	public int getMatchLength(){
		return matchLength;
	}

	public double getProb(){
		return prob;
	}

	public int getLength(){
		return length;
	}

	public String toString() {
		return start + ":" + length + " " + matchStart + ":" + matchLength;
	}
}