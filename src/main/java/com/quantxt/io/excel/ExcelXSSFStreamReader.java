package com.quantxt.io.excel;

import java.io.InputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Reader;
import com.quantxt.io.model.Template;
import com.quantxt.io.model.WorkbookFactory;

public class ExcelXSSFStreamReader implements Reader<InputStream, WorkbookData> {

    private static Logger log = LoggerFactory.getLogger(ExcelXSSFStreamReader.class);

    @Override
    public WorkbookData read(InputStream inputStream) {
        WorkbookData workbookData = null;
        try {
            workbookData = getWorkbookData(inputStream);
        } catch (Exception e) {
            log.error("Error reading inputStream ", e);
        }
        return workbookData;
    }

    public static Workbook getXSSFWorkbook(InputStream inputStream) {
        try {
            return new XSSFWorkbook(inputStream);
        } catch (Exception e){
            throw new RuntimeException("Unexpected error on init SheetHandler from templateInputStream", e);
        }
    }

    public static WorkbookData getWorkbookData(InputStream inputStream) throws Exception {
        // ZipSecureFile.setMinInflateRatio(0);
        Workbook workbook = getXSSFWorkbook(inputStream);
        WorkbookData data = new WorkbookData(WorkbookFactory.create(workbook), null);
        data.setTemplate(() -> {
            return workbook;
        });
        return data;
    }

}
