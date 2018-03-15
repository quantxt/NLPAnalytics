package com.quantxt.io;

import java.io.File;

import org.apache.poi.ss.usermodel.Workbook;

import com.quantxt.io.excel.ExcelIO;
import com.quantxt.io.excel.WorkbookData;
import com.quantxt.io.file.FileData;
import com.quantxt.io.file.FileIO;

public class ResourceIOFactory {

    public static ResourceIO<FileData, String, String> getFileResourceIO() {
        return new FileIO();
    }

    public static ResourceIO<WorkbookData, File, Workbook> getExcelResourceIO() {
        return new ExcelIO();
    }

}
