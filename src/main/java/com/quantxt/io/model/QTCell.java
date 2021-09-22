package com.quantxt.io.model;

import java.math.BigDecimal;

import static com.quantxt.types.ResultCell.isEmpty;

public class QTCell implements Cell {

    private final Row row;
    private final int cellIndex;
    private CellType cellType;
    private CellStyle cellStyle;
    private String value;

    public QTCell(Row row, int cellIndex, CellType cellType, String value) {
        this.row = row;
        this.cellIndex = cellIndex;
        this.cellType = cellType;
        this.value = value;
    }

    @Override
    public String getStringCellValue() {
        return value;
    }

    @Override
    public String getFormulaCellValue() {
        return value;
    }

    @Override
    public boolean getBooleanCellValue() {
        return Boolean.valueOf(value);
    }

    @Override
    public double getNumericCellValue() {
        return new BigDecimal(value).doubleValue();
    }

    @Override
    public CellType getCellType() {
        return cellType;
    }

    @Override
    public Row getRow() {
        return row;
    }

    public int getCellIndex() {
        return cellIndex;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void setCellValue(String value) {
        this.value = value;
        if (isEmpty(value)) {
            this.cellType = CellType.BLANK;
        } else {
            this.cellType = CellType.STRING;
        }
    }

    @Override
    public void setCellValue(boolean value) {
        this.value = String.valueOf(value);
        this.cellType = CellType.BOOLEAN;
    }

    @Override
    public void setCellValue(double value) {
        this.value = BigDecimal.valueOf(value).toPlainString();
        this.cellType = CellType.NUMERIC;
    }

    @Override
    public void setCellType(CellType cellType) {
        this.cellType = cellType;
        //TODO Dejan: Check if value should be set on NULL and converted depending on cellType
        this.value = null;
    }

    @Override
    public CellStyle getCellStyle() {
        return cellStyle;
    }

    @Override
    public void setCellStyle(CellStyle cellStyle) {
        this.cellStyle = cellStyle;
    }

}
