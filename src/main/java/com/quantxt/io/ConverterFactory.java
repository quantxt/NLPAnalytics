package com.quantxt.io;

import java.io.InputStream;

import com.quantxt.io.excel.ExcelResultDataToStreamConverter;
import com.quantxt.types.ResultWrapper;

public interface ConverterFactory {

    public static Converter<ResultWrapper, InputStream> getExcelRsultToStreamConverter() {
        return new ExcelResultDataToStreamConverter();
    }

}
