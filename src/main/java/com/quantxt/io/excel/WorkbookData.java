package com.quantxt.io.excel;

import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Workbook;

public class WorkbookData {

    private Workbook workbook;
    private String fileName;
    private Function<Cell, CellValue> evalFunc;

    public WorkbookData(Workbook workbook, Function<Cell, CellValue> evalFunc) {
        this.workbook = workbook;
        this.evalFunc = evalFunc;
    }

    public WorkbookData(Workbook workbook, String fileName,
            Function<Cell, CellValue> evalFunc) {
        this(workbook, evalFunc);
        this.fileName = fileName;
    }

    public Workbook getWorkbook() {
        return workbook;
    }

    public void setWorkbook(Workbook workbook) {
        this.workbook = workbook;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Function<Cell, CellValue> getEvalFunc() {
        return evalFunc;
    }

    public void setEvalFunc(Function<Cell, CellValue> evalFunc) {
        this.evalFunc = evalFunc;
    }

}
