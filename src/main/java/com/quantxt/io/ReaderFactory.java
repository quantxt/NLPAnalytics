package com.quantxt.io;

import java.io.File;
import java.io.InputStream;

import com.quantxt.io.excel.ExcelIO;
import com.quantxt.io.excel.ExcelStreamReader;
import com.quantxt.io.excel.ExcelXSSFStreamReader;
import com.quantxt.io.excel.WorkbookData;
import com.quantxt.io.file.FileIO;

public interface ReaderFactory {

    public static Reader<String, String> getFileReader() {
        return new FileIO();
    }

    public static Reader<File, WorkbookData> getExcelReader() {
        return new ExcelIO();
    }

    public static Reader<InputStream, WorkbookData> getExcelStreamReader() {
        return new ExcelStreamReader();
    }

    public static Reader<InputStream, WorkbookData> getExcelXSSFStreamReader() {
        return new ExcelXSSFStreamReader();
    }

}
