package com.quantxt.io.excel;

import com.quantxt.io.model.Template;
import com.quantxt.io.model.Workbook;

public class WorkbookData {

    private Workbook workbook;
    private Template template;
    private String fileName;

    public WorkbookData(Workbook workbook) {
        this.workbook = workbook;
    }

    public WorkbookData(Workbook workbook, String fileName) {
        this(workbook);
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

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }

}
