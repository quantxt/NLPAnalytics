package com.quantxt.io.model;

import java.util.Iterator;
import java.util.TreeMap;

public class QTRow implements Row {

    private final TreeMap<Integer, Cell> cells = new TreeMap<>();
    private final Sheet sheet;
    private final int rownum;

    public QTRow(Sheet sheet, int rownum) {
        this.sheet = sheet;
        this.rownum = rownum;
    }

    @Override
    public short getLastCellNum() {
        return (short) (cells.size() == 0 ? -1 : (cells.lastKey() + 1));
    }

    @Override
    public Sheet getSheet() {
        return sheet;
    }

    @Override
    public Cell getCell(int cellnum) {
        return cells.get(cellnum);
    }

    @Override
    public int getRowNum() {
        return rownum;
    }

    public TreeMap<Integer, Cell> getCells() {
        return cells;
    }

    @Override
    public short getFirstCellNum() {
        return (short) (cells.size() == 0 ? -1 : cells.firstKey());
    }

    @Override
    public Cell createCell(int i) {
        Cell cell = new QTCell(this, i, CellType.BLANK, null);
        cells.put(i, cell);
        return cell;
    }

    @Override
    public Iterator<Cell> iterator() {
        return cells.values().iterator();
    }

}
