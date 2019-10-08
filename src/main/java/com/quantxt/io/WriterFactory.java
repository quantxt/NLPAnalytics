package com.quantxt.io;

import com.quantxt.io.excel.ExcelIO;
import com.quantxt.io.excel.WorkbookData;
import com.quantxt.io.file.FileData;
import com.quantxt.io.file.FileIO;

public class WriterFactory {

    public static Writer<FileData> getFileWriter() {
        return new FileIO();
    }

    public static Writer<WorkbookData> getExcelWriter() {
        return new ExcelIO();
    }

}
