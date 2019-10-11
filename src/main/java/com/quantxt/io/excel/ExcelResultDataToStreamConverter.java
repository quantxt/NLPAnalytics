package com.quantxt.io.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Converter;
import com.quantxt.types.AttrType;
import com.quantxt.types.ResultCell;
import com.quantxt.types.ResultRow;
import com.quantxt.types.ResultSheet;
import com.quantxt.types.ResultWrapper;
import com.quantxt.util.NLPUtil;

public class ExcelResultDataToStreamConverter implements Converter<ResultWrapper, InputStream> {

    private static Logger logger = LoggerFactory.getLogger(ExcelResultDataToStreamConverter.class);

    @Override
    public InputStream convert(ResultWrapper input) {

        Workbook wb = (Workbook) input.getTemplate().getSource();

        CellStyle emptyCellStyle = wb.createCellStyle();
        emptyCellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        emptyCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (Sheet sheet : wb) {
            ResultSheet resultSheet = input.getResultData().getSheet(sheet.getSheetName());
            for (Map.Entry<Integer, ResultRow> rowEntry : resultSheet.getRows().entrySet()) {
                Row newRow = sheet.createRow(rowEntry.getKey());
                for (Map.Entry<Integer, ResultCell> cellEntry : rowEntry.getValue().getCells().entrySet()) {
                    ResultCell resultCell = cellEntry.getValue();
                    if (!resultCell.isHighlight() && resultCell.isEmpty()) {
                        logger.warn("===>>> Skip empty cell {}", resultCell.getAttribute().getName());
                        continue;
                    }
                    Cell newCell = newRow.createCell(cellEntry.getKey());
                    if (resultCell.isHighlight()) {
                        newCell.setCellStyle(emptyCellStyle);
                    }
                    if (resultCell.isEmpty()) {
                        continue;
                    }

                    //TODO Dejan: Check this for all types
                    if (resultCell.isNumericType()) {
                        newCell.setCellValue(resultCell.getNumericCellValue());
                    } else if (resultCell.getAttribute().getType() == AttrType.LONG) {
                        newCell.setCellValue(resultCell.getLongCellValue());
                    } else if (resultCell.getAttribute().getType() == AttrType.INTEGER
                            || resultCell.getAttribute().getType() == AttrType.SEQ) {
                        newCell.setCellValue(resultCell.getIntegerCellValue());
                    } else {
                        newCell.setCellValue(resultCell.getValue());
                    }
                }
            }
        }
        if (XSSFWorkbook.class.isAssignableFrom(wb.getClass())) {
            NLPUtil.formatTargetWB((XSSFWorkbook) wb, input.getSheetNames());
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            wb.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Error writing workbook data to output stream", e);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

}
