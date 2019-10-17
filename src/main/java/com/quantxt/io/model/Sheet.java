package com.quantxt.io.model;

import java.util.Iterator;

public interface Sheet extends Iterable<Row> {

    boolean isColumnHidden(int columnIndex);

    String getSheetName();

    int getFirstRowNum();

    int getLastRowNum();

    Row getRow(int rownum);

    Iterator<Row> iterator();

    Workbook getWorkbook();

    Row createRow(int rownum);

}
