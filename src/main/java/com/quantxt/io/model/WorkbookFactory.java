package com.quantxt.io.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.quantxt.util.ExceptionUtil;
import org.apache.poi.ss.formula.CollaboratingWorkbooksEnvironment.WorkbookNotFoundException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkbookFactory {

    private static Logger logger = LoggerFactory.getLogger(WorkbookFactory.class);

    public static Workbook create(org.apache.poi.ss.usermodel.Workbook srcWb) {
        FormulaEvaluator fevaluator = srcWb.getCreationHelper().createFormulaEvaluator();

        QTWorkbook resWb = new QTWorkbook() {

            @Override
            public boolean isSheetHidden(int sheetIndex) {
                return srcWb.isSheetHidden(sheetIndex);
            }

            @Override
            public boolean isSheetVeryHidden(int sheetIndex) {
                return srcWb.isSheetVeryHidden(sheetIndex);
            }
        };

        for (org.apache.poi.ss.usermodel.Sheet srcSheet : srcWb) {
            QTSheet sheet = new QTSheet(srcSheet.getSheetName(), resWb) {

                @Override
                public boolean isColumnHidden(int columnIndex) {
                    return srcSheet.isColumnHidden(columnIndex);
                }
            };
            if (srcSheet.getMergedRegions() != null) {
                List<MergeRegion> mergeRegions = srcSheet.getMergedRegions()
                        .stream()
                        .map(r -> new MergeRegion(r.getFirstRow(), r.getLastRow(), r.getFirstColumn(), r.getLastColumn()))
                        .collect(Collectors.toList());
                sheet.setMergeRegions(mergeRegions);
            }
            resWb.getSheets().add(sheet); // Add

            for (org.apache.poi.ss.usermodel.Row srcRow : srcSheet) {
                QTRow row = new QTRow(sheet, srcRow.getRowNum());
                sheet.getRows().put(srcRow.getRowNum(), row); // Add

                for (org.apache.poi.ss.usermodel.Cell srcCell : srcRow) {
                    String value = null;
                    switch (srcCell.getCellType()) {
                    case STRING:
                        value = srcCell.getStringCellValue();
                        break;
                    case BOOLEAN:
                        value = String.valueOf(srcCell.getBooleanCellValue());
                        break;
                    case NUMERIC:
                        value = new BigDecimal(srcCell.getNumericCellValue()).toString();
                        break;
                    case FORMULA:
                        value = evalCell(srcCell, fevaluator);
                        break;

                    default:
                        break;
                    }

                    // Get CellStyle to set date format
                    CellStyle cs = srcCell.getCellStyle();
                    com.quantxt.io.model.CellStyle cellStyle = new com.quantxt.io.model.CellStyle(cs.getDataFormatString());
                    com.quantxt.io.model.Cell cell = new QTCell(row,
                            srcCell.getColumnIndex(),
                            com.quantxt.io.model.CellType.valueOf(srcCell.getCellType().name()), value);
                    cell.setCellStyle(cellStyle);

                    row.getCells().put(srcCell.getColumnIndex(), cell);
                }
            }
        }

        return resWb;
    }

    private static String evalCell(Cell c, FormulaEvaluator fevaluator) {
        String value = null;
        try {
            CellValue cellValue = fevaluator.evaluate(c);
            if (cellValue.getCellType() == CellType.BOOLEAN) {
                value = String.valueOf(cellValue.getBooleanValue());
            } else if (cellValue.getCellType() == CellType.NUMERIC) {
                value = String.valueOf(new BigDecimal(cellValue.getNumberValue()));
            } else if (cellValue.getCellType() == CellType.STRING) {
                value = cellValue.getStringValue();
            }

        } catch (RuntimeException re){
            logger.warn("Formula can not be evaluated for the cell with the column index {}, and the row index",
                    c.getColumnIndex(), c.getRowIndex(), re);
            if(ExceptionUtil.getRootCause(re) instanceof WorkbookNotFoundException
                    && c.getCachedFormulaResultType() == CellType.NUMERIC){
                value = String.valueOf(new BigDecimal(c.getNumericCellValue()));
                logger.warn("Retrieved cached value.");
            }
        }

        return value;
    }

}
