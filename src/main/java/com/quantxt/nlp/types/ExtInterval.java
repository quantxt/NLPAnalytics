package com.quantxt.nlp.types;

import com.quantxt.interval.Interval;

/**
 * Created by matin on 5/20/17.
 */
public class ExtInterval extends Interval {
    private String type;
    public ExtInterval(int start, int end) {
        super(start, end);
    }

    public void setType(String t){
        type = t;
    }

    public String getType(){
        return type;
    }
}
