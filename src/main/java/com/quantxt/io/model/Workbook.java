package com.quantxt.io.model;

public interface Workbook {

    int getNumberOfSheets();

    String getSheetName(int sheetIndex);

    boolean isSheetHidden(int sheetIndex);

    Sheet getSheetAt(int sheetIndex);

}
