package com.quantxt.io.excel;

import java.io.*;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.XLSBUnsupportedException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Reader;
import com.quantxt.io.model.WorkbookFactory;

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

    private static byte[] getBytesFromStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();

        return output.toByteArray();
    }

    public static Workbook getWorkbook(InputStream inputStream) throws Exception {
        Workbook workbook;
        byte [] bytes = getBytesFromStream(inputStream);
        try {
            workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (OfficeXmlFileException e) {
            log.debug("office 2010 file");
            try {
                workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
            } catch (XLSBUnsupportedException ee){
                log.debug("Try Binary Excel XLSB");
                workbook = ExcelIO.getXLSBWorkbook(new ByteArrayInputStream(bytes));
            }
        } catch (IOException e){
            log.debug("Try Binary Excel XLSB");
            workbook = ExcelIO.getXLSBWorkbook(new ByteArrayInputStream(bytes));
        }
        return workbook;
    }

    public static Workbook getXSSFWorkbook(InputStream templateInputStream) {
        try {
            Workbook workbook = new XSSFWorkbook(templateInputStream);
            return workbook;
        } catch (Exception e){
            log.error("Unexpected error on init SheetHandler from templateInputStream", e);
            return null;
        }
    }

    public static WorkbookData getWorkbookData(InputStream inputStream) throws Exception {
        Workbook workbook = getWorkbook(inputStream);
        return new WorkbookData(WorkbookFactory.create(workbook), null);
    }
}
