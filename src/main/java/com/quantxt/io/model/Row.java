package com.quantxt.io.model;

import org.apache.poi.ss.usermodel.Cell;

public interface Row {

    short getLastCellNum();

    Sheet getSheet();

    Cell getCell(int cellnum);

    int getRowNum();

}
