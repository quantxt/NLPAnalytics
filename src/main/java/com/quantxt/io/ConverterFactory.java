package com.quantxt.io;

import java.io.InputStream;

import com.quantxt.io.excel.ExcelResultDataToStreamConverter;
import com.quantxt.io.model.Workbook.Type;
import com.quantxt.types.ResultWrapper;

public interface ConverterFactory {

    public static Converter<ResultWrapper, InputStream> getResultToStreamConverter(
            Type type) throws IllegalArgumentException {
        if (type == Type.EXCEL) {
            return new ExcelResultDataToStreamConverter();
        }
        throw new IllegalArgumentException(
                "Converter unsupported WorkbookType: " + type);
    }

}
