package com.quantxt.io;

import com.quantxt.io.excel.ExcelIO;
import com.quantxt.io.excel.WorkbookData;
import com.quantxt.io.file.FileData;
import com.quantxt.io.file.FileIO;
import com.quantxt.io.model.Workbook;
import com.quantxt.io.model.Workbook.Type;

public class WriterFactory {

    public static Writer<FileData> getFileWriter() {
        return new FileIO();
    }

    public static Writer<WorkbookData> getWorkbookWriter(Workbook.Type type) {
        if (type == Type.EXCEL) {
            return new ExcelIO();
        }
        throw new IllegalArgumentException(
                "Writer unsupported Workbook Type: " + type);
    }

}
