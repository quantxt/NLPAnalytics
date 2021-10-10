package com.quantxt.io.model;

import java.io.Serializable;

public class MergeRegion implements Serializable {

    private static final long serialVersionUID = 908262543155320497L;

    private int firstRow;
    private int lastRow;
    private int firstColumn;
    private int lastColumn;

    public MergeRegion() {
    }

    public MergeRegion(int firstRow, int lastRow, int firstColumn, int lastColumn) {
        this.firstRow = firstRow;
        this.lastRow = lastRow;
        this.firstColumn = firstColumn;
        this.lastColumn = lastColumn;
    }

    public int getFirstRow() {
        return firstRow;
    }

    public void setFirstRow(int firstRow) {
        this.firstRow = firstRow;
    }

    public int getLastRow() {
        return lastRow;
    }

    public void setLastRow(int lastRow) {
        this.lastRow = lastRow;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public void setFirstColumn(int firstColumn) {
        this.firstColumn = firstColumn;
    }

    public int getLastColumn() {
        return lastColumn;
    }

    public void setLastColumn(int lastColumn) {
        this.lastColumn = lastColumn;
    }

    @Override
    public String toString() {
        return "MergeRegion [firstRow=" + firstRow + ", lastRow=" + lastRow
                + ", firstColumn=" + firstColumn + ", lastColumn=" + lastColumn + "]";
    }

}
