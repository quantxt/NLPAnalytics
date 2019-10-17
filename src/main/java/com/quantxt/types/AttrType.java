package com.quantxt.types;

import java.util.HashMap;
import java.util.Map;

public enum AttrType {

    INTEGER, LONG, DOUBLE, STRING, DATE, BOOLEAN, SEQ, PERCENT, CAT;

    private static final Map<String, AttrType> cache = new HashMap<>();

    static {
        for (AttrType type : AttrType.values()) {
            cache.put(type.name(), type);
        }
    }

    public static AttrType getByName(String name) {
        return cache.get(name);
    }

}
