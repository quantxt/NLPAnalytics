package com.quantxt.io.model;

import java.util.Iterator;

public interface Row extends Iterable<Cell> {

    Iterator<Cell> iterator();

    short getFirstCellNum();

    short getLastCellNum();

    Sheet getSheet();

    Cell getCell(int cellnum);

    int getRowNum();

    Cell createCell(int i);

}
