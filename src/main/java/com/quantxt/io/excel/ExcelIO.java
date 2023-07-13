package com.quantxt.io.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.function.Function;

import com.quantxt.converter.WorkbookConverter;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.binary.XSSFBSharedStringsTable;
import org.apache.poi.xssf.binary.XSSFBSheetHandler;
import org.apache.poi.xssf.binary.XSSFBStylesTable;
import org.apache.poi.xssf.eventusermodel.XSSFBReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Reader;
import com.quantxt.io.Writer;
import com.quantxt.io.model.WorkbookFactory;

public class ExcelIO implements Writer<WorkbookData>, Reader<File, WorkbookData> {

    private static Logger log = LoggerFactory.getLogger(ExcelIO.class);

    @Override
    public void write(WorkbookData data) {
        try (FileOutputStream fileOut = new FileOutputStream(data.getFileName())) {
            //data.getWorkbook().write(fileOut);
        } catch (Exception e) {
            log.error("Error on write Workbook info file: {}", data.getFileName());
        }
    }

    @Override
    public WorkbookData read(File source) {
        WorkbookData workbookData = null;
        try {
            workbookData = getWorkbookData(source);
        } catch (Exception e) {
            log.error("Error reading file {}", source, e);
        }
        return workbookData;
    }

    public static Workbook getWorkbook(File file) throws Exception {
        String name = file.getName().toLowerCase().trim();
        if (!(name.endsWith(".xls")
                || name.endsWith(".xlsx")
                || name.endsWith(".xlsb")
                || name.endsWith(".xlsm"))) {
            throw new RuntimeException("File extension is not supported.");
        }
        log.info("Processing file: {}", name);
        FileInputStream inputStream = new FileInputStream(file);
        Workbook workbook = null;
        try {
            if (name.endsWith(".xlsb")) {
                workbook = getXLSBWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }

        }catch (OfficeXmlFileException e) {
            log.debug("office 2010 file");
            inputStream = new FileInputStream(file);
            workbook = new XSSFWorkbook(inputStream);

        }finally{
            inputStream.close();
        }
        return workbook;
    }

    public static WorkbookData getWorkbookData(File file) throws Exception {
        String name = file.getName().toLowerCase().trim();
        try{
            Workbook workbook = getWorkbook(file);
            return new WorkbookData(WorkbookFactory.create(workbook), name);
        }catch (OldExcelFormatException e) {
            jxl.Workbook jxlWorkbook = jxl.Workbook.getWorkbook(new File(file.toURI()));
            return new WorkbookData(new WorkbookConverter().from(jxlWorkbook), name);
        }
    }

    public static Workbook getXLSBWorkbook(InputStream inputStream) throws Exception {

        POIXMLTextExtractor extractor = (POIXMLTextExtractor) ExtractorFactory.createExtractor(inputStream);
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

        return wb;
    }


    public static class XLSBSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final StringBuilder sb = new StringBuilder();
        private Sheet sheet;

        public void setSheet(Sheet sh){
            this.sheet = sh;
        }

        public void startSheet(String sheetName) {
            sb.append("<sheet name=\"").append(sheetName).append(">");

        }

        public void endSheet() {
            sb.append("</sheet>");
        }

        @Override
        public void startRow(int rowNum) {
            sb.append("\n<tr num=\"").append(rowNum).append(">");
            sheet.createRow(rowNum);

        }

        @Override
        public void endRow(int rowNum) {
            sb.append("\n</tr num=\"").append(rowNum).append(">");
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
        public void headerFooter(String text, boolean isHeader, String tagName) {
            if (isHeader) {
                sb.append("<header tagName=\"" + tagName + "\">" + text + "</header>");
            } else {
                sb.append("<footer tagName=\"" + tagName + "\">" + text + "</footer>");
            }
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    public static Function<Cell, CellValue> getEvalFunc(Workbook workbook) {
        FormulaEvaluator fevaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Function<Cell, CellValue> evalFunc = (t) -> {
            try {
                return fevaluator.evaluate(t);
            } catch (Exception e) {
                log.error("Error in formula evaluator " + e.getMessage());
                return null;
            }
        };
        return evalFunc;
    }

}
