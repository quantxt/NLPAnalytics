package com.quantxt.io.model;

import java.util.Iterator;

import org.apache.poi.ss.usermodel.Row;

public interface Sheet {

    boolean isColumnHidden(int columnIndex);

    String getSheetName();

    int getFirstRowNum();

    int getLastRowNum();

    Row getRow(int rownum);

    Iterator<Row> iterator();

}
