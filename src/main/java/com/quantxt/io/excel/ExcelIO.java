package com.quantxt.io.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.binary.XSSFBSharedStringsTable;
import org.apache.poi.xssf.binary.XSSFBSheetHandler;
import org.apache.poi.xssf.binary.XSSFBStylesTable;
import org.apache.poi.xssf.eventusermodel.XSSFBReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.extractor.XSSFBEventBasedExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFComment;
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
        //sooo hacky
        String uuid = UUID.randomUUID().toString();
        String filename = "/tmp/" + uuid.replace("-", "") + ".xlsb";

        log.info("Writing " + filename + " to disk");

        File targetFile = new File(filename);
        Files.copy(inputStream, targetFile.toPath());

        POIXMLTextExtractor extractor = new XSSFBEventBasedExcelExtractor(filename);
        OPCPackage pkg = extractor.getPackage();

        XSSFBReader r = new XSSFBReader(pkg);
        XSSFBSharedStringsTable sst = new XSSFBSharedStringsTable(pkg);
        XSSFBStylesTable xssfbStylesTable = r.getXSSFBStylesTable();
        XSSFBReader.SheetIterator it = (XSSFBReader.SheetIterator) r.getSheetsData();

        Workbook wb = new XSSFWorkbook();

        while (it.hasNext()) {
            InputStream iss = it.next();
            String name = it.getSheetName();
            XLSBSheetHandler testSheetHandler = new XLSBSheetHandler();
            testSheetHandler.setSheet(wb.createSheet(name));
            XSSFBSheetHandler sheetHandler = new XSSFBSheetHandler(iss,
                    xssfbStylesTable,
                    it.getXSSFBSheetComments(),
                    sst,
                    testSheetHandler,
                    new DataFormatter(),
                    false);
            sheetHandler.parse();
        }

        log.info("removing " + filename + " from disk");
        targetFile.delete();
        return wb;
    }

    private static class XLSBSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private Sheet sheet;

        public void setSheet(Sheet sh){
            this.sheet = sh;
        }

        @Override
        public void startRow(int rowNum) {
            sheet.createRow(rowNum);

        }

        @Override
        public void endRow(int i) {

        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            formattedValue = (formattedValue == null) ? "" : formattedValue;
            CellReference ref = new CellReference(cellReference);
            Row r = sheet.getRow(ref.getRow());
            Cell c = r.createCell(ref.getCol());
            c.setCellValue(formattedValue);

        }

        @Override
        public void headerFooter(String s, boolean b, String s1) {

        }
    }

}
