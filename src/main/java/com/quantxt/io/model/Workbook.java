package com.quantxt.io.model;

import java.util.Iterator;

public interface Workbook extends Iterable<Sheet> {

    Iterator<Sheet> iterator();

    int getNumberOfSheets();

    String getSheetName(int sheetIndex);


    Sheet getSheetAt(int sheetIndex);

    Sheet getSheet(String name);

    Sheet createSheet(String sheetName);

    default boolean isSheetVeryHidden(int sheetIndex) {
        return false;
    }

    default boolean isSheetHidden(int sheetIndex) {
        return false;
    }

}
