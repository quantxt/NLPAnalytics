package com.quantxt.io.excel;

import org.apache.poi.ss.usermodel.Workbook;

public class WorkbookData {

    private Workbook workbook;
    private String fileName;

    public WorkbookData(Workbook workbook, String fileName) {
        this.workbook = workbook;
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

}
