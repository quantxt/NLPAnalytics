package com.quantxt.io.model;

import javax.naming.OperationNotSupportedException;
import java.util.Iterator;

public interface Workbook extends Iterable<Sheet> {

    enum Type {EXCEL, EXCEL_XSSF}

    Iterator<Sheet> iterator();

    int getNumberOfSheets();

    String getSheetName(int sheetIndex);


    Sheet getSheetAt(int sheetIndex);

    Sheet getSheet(String name);

    Sheet createSheet(String sheetName);

    default boolean isSheetVeryHidden(int sheetIndex) throws OperationNotSupportedException {
        return false;
    }

    default boolean isSheetHidden(int sheetIndex) {
        return false;
    }

}
