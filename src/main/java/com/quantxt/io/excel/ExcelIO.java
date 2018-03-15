package com.quantxt.io.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.ResourceIO;

public class ExcelIO implements ResourceIO<WorkbookData, File, Workbook> {

    private static Logger log = LoggerFactory.getLogger(ExcelIO.class);

    @Override
    public void write(WorkbookData data) {
        try (FileOutputStream fileOut = new FileOutputStream(data.getFileName())) {
            data.getWorkbook().write(fileOut);
        } catch (Exception e) {
            log.error("Error on write Workbook info file: {}", data.getFileName());
        }
    }

    @Override
    public Workbook read(File source) {
        Workbook workbook = null;
        try {
            workbook = getWorkbook(source);
        } catch (Exception e) {
            log.error("Error reading file {}", source, e);
        }
        return workbook;
    }

    public static Workbook getWorkbook(File file) throws Exception {
        String name = file.getName().toLowerCase().trim();
        if (!(name.endsWith(".xls")
                || name.endsWith(".xlsx")
                || name.endsWith(".xlsb")
                || name.endsWith(".xlsm"))) {
            return null;
        }
        log.info("Processing file: {}", name);
        FileInputStream inputStream = new FileInputStream(file);
        Workbook workbook = null;
        if (name.endsWith(".xlsb")) {
            workbook = getXLSBWorkbook(inputStream);
        } else {
            try {
                workbook = new HSSFWorkbook(inputStream);
            } catch (OfficeXmlFileException e) {
                log.debug("office 2010 file");
                inputStream = new FileInputStream(file);
                workbook = new XSSFWorkbook(inputStream);
            } catch (Exception e) {
                log.error("Workbook can not be created: {}", name);
                return null;
            }
        }
        inputStream.close();
        return workbook;
    }

    public static Workbook getXLSBWorkbook(InputStream inputStream)
            throws Exception {
        com.smartxls.WorkBook wb = new com.smartxls.WorkBook();
        wb.readXLSB(inputStream);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(wb.getSheetName(0));
        int numRows = wb.getLastRow();
        if (numRows > 3000) {
            log.error("File too big too process " + numRows);
            return null;
        }
        log.info("Num rows: {}", numRows);
        for (int i = 0; i < numRows; i++) {
            Row newRow = sheet.createRow(i);
            int numCol = wb.getLastCol();
            for (int j = 0; j < numCol; j++) {
                Cell cell = newRow.createCell(j);
                cell.setCellValue(wb.getText(i, j));
            }
        }
        return workbook;
    }

}
