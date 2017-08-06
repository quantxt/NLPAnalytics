package com.quantxt.QTDocument;

/**
 * Created by matin on 8/6/17.
 */
public class QTHelper {

    public static String removePrnts(String str){
        str = str.replaceAll("\\([^\\)]+\\)", " ");
        str = str.replaceAll("([\\.])+$", " $1");
        str = str.replaceAll("\\s+", " ");
        return str;
    }
}
