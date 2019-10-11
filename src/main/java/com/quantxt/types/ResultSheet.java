package com.quantxt.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ResultSheet {
    private final String name;
    private final Map<Integer, ResultRow> rows = new LinkedHashMap<>();
    private int lastRowIndex;

    public ResultSheet(String name) {
        this.name = name;
    }

    public ResultRow createRow(int rowNum) {
        ResultRow row = new ResultRow(rowNum);
        rows.put(rowNum, row);
        updateLastRowIndex(rowNum);
        return row;
    }

    public void removeRow(Integer rowNum) {
        rows.remove(rowNum);
    }

    public String getName() {
        return name;
    }

    public Map<Integer, ResultRow> getRows() {
        return rows;
    }

    public ResultRow getRow(Integer index) {
        return rows.get(index);
    }

    private synchronized void updateLastRowIndex(int index) {
        Optional<Integer> op = rows.keySet()
                .parallelStream()
                .filter(k -> k > index)
                .findAny();
        if (op.isPresent()) {
            return;
        }
        lastRowIndex = index;
    }

    public int getLastRowIndex() {
        return lastRowIndex;
    }

}
