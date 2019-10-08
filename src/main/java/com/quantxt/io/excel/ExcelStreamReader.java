package com.quantxt.io.excel;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.XLSBUnsupportedException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Reader;

public class ExcelStreamReader implements Reader<InputStream, WorkbookData> {

    private static Logger log = LoggerFactory.getLogger(ExcelStreamReader.class);

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

    public static Workbook getWorkbook(InputStream inputStream) throws Exception {
        Workbook workbook;
        try {
            workbook = new HSSFWorkbook(inputStream);
        } catch (OfficeXmlFileException e) {
            log.debug("office 2010 file");
            try {
                workbook = new XSSFWorkbook(inputStream);
            } catch (XLSBUnsupportedException ee){
                log.debug("Try Binary Excel XLSB");
                workbook = ExcelIO.getXLSBWorkbook(inputStream);
            }
        } catch (IOException e){
            log.debug("Try Binary Excel XLSB");
            workbook = ExcelIO.getXLSBWorkbook(inputStream);
        }
        return workbook;
    }
    public static WorkbookData getWorkbookData(InputStream inputStream) throws Exception {
        Workbook workbook = getWorkbook(inputStream);
        return new WorkbookData(workbook, null, ExcelIO.getEvalFunc(workbook));
    }

}
