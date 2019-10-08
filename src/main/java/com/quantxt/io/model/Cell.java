package com.quantxt.io.model;

public interface Cell {

    CellType getCellType();

    String getStringCellValue();

    String getFormulaCellValue();

    boolean getBooleanCellValue();

    double getNumericCellValue();

}
