package com.quantxt.io.model;

public interface Cell {

    Row getRow();

    CellType getCellType();

    String getStringCellValue();

    String getFormulaCellValue();

    boolean getBooleanCellValue();

    double getNumericCellValue();

    void setCellValue(String value);

    void setCellValue(boolean value);

    void setCellValue(double value);

    void setCellType(CellType cellType);

}
