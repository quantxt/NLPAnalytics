package com.quantxt.types;

import com.quantxt.model.Interval;

public class LineInfo {
    private static Character NewLine = '\n';

    private int lineNumber;
    private int localStart;
    private int localEnd;

    public LineInfo(int lineNumber, int localStart, int localEnd){
        this.lineNumber = lineNumber;
        this.localStart = localStart;
        this.localEnd = localEnd;
    }

    public LineInfo(String str, Interval interval ){
        this.lineNumber = 0;
        int mostRecentNewLineIndex = 0;
        int str_length = str.length();

        for (int i = 0; i < interval.getStart() && i < str_length; i++) {
            if (str.charAt(i) == NewLine) {
                mostRecentNewLineIndex = i;
                lineNumber++;
            }
        }

        // return 0-based line number : add 1 to account for the newline character and move
        //the cursor to the next character
        int shift = lineNumber == 0? 0 : 1;
        this.localStart = interval.getStart() - mostRecentNewLineIndex - shift;
        this.localEnd = this.localStart  + interval.getEnd() - interval.getStart();
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLocalStart() {
        return localStart;
    }

    public void setLocalStart(int localStart) {
        this.localStart = localStart;
    }

    public int getLocalEnd() {
        return localEnd;
    }

    public void setLocalEnd(int localEnd) {
        this.localEnd = localEnd;
    }
}
