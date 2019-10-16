package com.quantxt.io.model;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class QTSheet implements Sheet {

    private final SortedMap<Integer, Row> rows = new TreeMap<>();
    private final String sheetName;
    private final Workbook workbook;

    public QTSheet(String sheetName, Workbook workbook) {
        this.sheetName = sheetName;
        this.workbook = workbook;
    }

    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public int getFirstRowNum() {
        return rows.isEmpty() ? 0 : rows.firstKey();
    }

    @Override
    public int getLastRowNum() {
        return rows.isEmpty() ? 0 : rows.lastKey();
    }

    @Override
    public Row getRow(int rownum) {
        final Integer rowNum = Integer.valueOf(rownum);
        return rows.get(rowNum);
    }

    @Override
    public Iterator<Row> iterator() {
        return rows.values().iterator();
    }

    public Workbook getWorkbook() {
        return workbook;
    }

    public SortedMap<Integer, Row> getRows() {
        return rows;
    }

    @Override
    public Row createRow(int rownum) {
        Row row = null;
        if (rows.containsKey(rownum)) {
            row = rows.get(rownum);
            QTRow qtRow = (QTRow) row;
            qtRow.getCells().clear();
        } else {
            row = new QTRow(this, rownum);
        }
        rows.put(rownum, row);
        return row;
    }

}
