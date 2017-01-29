package com.quantxt.types;

/**
 * Created by matin on 12/24/16.
 */
public class StringDouble {
    private String str;
    private double val;

    public StringDouble(String s, double d){
        str = s;
        val = d;
    }

    public String getStr(){
        return str;
    }
    public double getVal(){
        return val;
    }
}
