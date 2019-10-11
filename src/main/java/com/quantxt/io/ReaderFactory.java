package com.quantxt.io;

import java.io.File;
import java.io.InputStream;

import com.quantxt.io.excel.ExcelIO;
import com.quantxt.io.excel.ExcelStreamReader;
import com.quantxt.io.excel.ExcelXSSFStreamReader;
import com.quantxt.io.excel.WorkbookData;
import com.quantxt.io.file.FileIO;
import com.quantxt.io.model.Workbook.Type;

public interface ReaderFactory {

    public static Reader<String, String> getFileReader() {
        return new FileIO();
    }

    public static Reader<File, WorkbookData> getWorkbookReader() {
        return new ExcelIO();
    }

    public static Reader<InputStream, WorkbookData> getWorkbookStreamReader(Type type) {
        if (type == null) {
            type = Type.EXCEL;
        }
        Reader<InputStream, WorkbookData> reader = null;
        switch (type) {
        case EXCEL:
            reader = new ExcelStreamReader();
            break;
        case EXCEL_XSSF:
            reader = new ExcelXSSFStreamReader();
            break;

        default:
            throw new IllegalArgumentException(
                    "Reader unsupported Workbook Type: " + type);
        }
        return reader;
    }

}
