package com.quantxt.types;

import java.util.Comparator;
import java.util.Map;

/**
 * Created by matin on 1/28/17.
 */
public class StringDoubleComparator implements Comparator<String> {
    Map<String, Double> base;

    public StringDoubleComparator(Map<String, Double> base) {
        this.base = base;
    }

    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        }
    }
}