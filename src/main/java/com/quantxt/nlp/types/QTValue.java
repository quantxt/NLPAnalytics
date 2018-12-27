package com.quantxt.nlp.types;

import com.quantxt.interval.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by matin on 11/24/18.
 */

public abstract class QTValue {
    final protected static Logger logger = LoggerFactory.getLogger(QTValue.class);

    protected String string;
    protected ArrayList<String> key;
    protected ArrayList<String> value;
    protected ArrayList<Interval> keyInterval;
    protected ArrayList<Interval> valueInterval;

    public QTValue(String str){
        string = str;
        valueInterval = new ArrayList<>();
        value = new ArrayList<>();
    }

    public ArrayList<String> getKeys(){
        return key;
    }

    public ArrayList<String> getValue(){
       return value;
    }

    public static String getPad(final int s, final int e){
        return String.join("", Collections.nCopies(e - s, " "));
    }
}
