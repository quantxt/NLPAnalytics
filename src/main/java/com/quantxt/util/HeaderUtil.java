package com.quantxt.util;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.model.HeaderAlign;
import com.quantxt.io.model.HeaderAlignKey;

import static com.quantxt.util.NLPUtil.isEmpty;

public interface HeaderUtil {

    Logger logger = LoggerFactory.getLogger(HeaderUtil.class);

    static void logHeaderAlign(String logHeaderAlignFile,
            final Map<String, Map<HeaderAlignKey, List<HeaderAlign>>> sheetSovAlingmentMap) {

        if (!isEmpty(logHeaderAlignFile)) {
            Workbook workbook = new XSSFWorkbook();
            for (String sheetName : sheetSovAlingmentMap.keySet()) {
                Sheet sheet = workbook.createSheet(sheetName);
                int rowNum = 0;
                Row headerRow = sheet.createRow(rowNum++);
                headerRow.createCell(0).setCellValue("Source");
                headerRow.createCell(1).setCellValue("Target");

                Map<HeaderAlignKey, List<HeaderAlign>> sovAlignmentMap = sheetSovAlingmentMap
                        .get(sheetName);
                for (HeaderAlignKey key : sovAlignmentMap.keySet()) {
                    if (key.isSource()) {
                        Row row = sheet.createRow(rowNum++);
                        Cell sourceCell = row.createCell(0);
                        sourceCell.setCellValue(key.getName());
                    } else {
                        for (HeaderAlign a : sovAlignmentMap.get(key)) {
                            Row row = sheet.createRow(rowNum++);
                            Cell sourceCell = row.createCell(0);
                            Cell targetCell = row.createCell(1);
                            targetCell.setCellValue(a.getTargetHeader());
                            sourceCell.setCellValue(a.getSourceHeader());
                        }
                    }
                }
            }

            try {
                FileOutputStream outputStream = new FileOutputStream(
                        logHeaderAlignFile);
                workbook.write(outputStream);
                workbook.close();
            } catch (Exception e) {
                logger.error("Unexpected error on write header align file", e);
            }
        }
    }

}
