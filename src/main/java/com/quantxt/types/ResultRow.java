package com.quantxt.types;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResultRow {
    private final int index;
    private final Map<Integer, ResultCell> cells = new LinkedHashMap<>();

    public ResultRow(int index) {
        this.index = index;
    }

    public synchronized ResultCell createCell(int index, Attribute attribute) {
        ResultCell cell = new ResultCell(index, attribute);
        cells.put(index, cell);
        return cell;
    }

    public synchronized ResultCell getCell(Attribute attribute) {
        ResultCell cell = cells.get(attribute.getSourceIndex());
        if (cell == null) {
            cell = new ResultCell(attribute.getSourceIndex(), attribute);
            cells.put(attribute.getSourceIndex(), cell);
        }
        return cell;
    }

    public int getIndex() {
        return index;
    }

    public Map<Integer, ResultCell> getCells() {
        return cells;
    }

    public ResultCell getCell(int index) {
        return cells.get(index);
    }

    public ResultCell getByNameCode(String name) {
        return cells.values().parallelStream()
                .filter(c -> c.getAttribute().getNameCode().equals(name))
                .findFirst()
                .orElse(null);
    }

    public ResultCell getByName(String name) {
        return cells.values().parallelStream()
                .filter(c -> c.getAttribute().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public int getRowNum() {
        return index;
    }

    @Override
    public String toString() {
        return "[index=" + index + "]";
    }

}
