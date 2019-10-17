package com.quantxt.types;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultData {

    private Map<String, ResultSheet> sheets = new LinkedHashMap<>();

    public ResultSheet createSheet(String name) {
        ResultSheet sheet = new ResultSheet(name);
        sheets.put(name, sheet);
        return sheet;
    }

    public ResultSheet getSheet(String name) {
        ResultSheet sheet = sheets.getOrDefault(name, new ResultSheet(name));
        sheets.put(name, sheet);
        return sheet;
    }

    public Map<String, ResultSheet> getSheets() {
        return sheets;
    }

}
