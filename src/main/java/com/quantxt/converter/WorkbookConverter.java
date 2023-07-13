package com.quantxt.converter;

import com.quantxt.io.model.*;
import jxl.Cell;
import jxl.Sheet;

public class WorkbookConverter {
    public Workbook from(jxl.Workbook jxlWorkbook) {
        QTWorkbook qtWorkbook =
                new QTWorkbook() {
                    @Override
                    public boolean isSheetHidden(int sheetIndex) {
                        return jxlWorkbook.getSheet(sheetIndex).isHidden();
                    }
                    @Override
                    public boolean isSheetVeryHidden(int sheetIndex) {
                        return false;
                    }
                };

        for (Sheet srcSheet : jxlWorkbook.getSheets()) {
            QTSheet qtSheet =
                    new QTSheet(srcSheet.getName(), null) {
                        @Override
                        public boolean isColumnHidden(int columnIndex) {
                            return false;
                        }
                    };
            qtWorkbook.getSheets().add(qtSheet);

            for (int i = 0; i < srcSheet.getRows(); i++) {
                final Cell[] srcRow = srcSheet.getRow(i);
                QTRow row = new QTRow(qtSheet, i);
                qtSheet.getRows().put(i, row);

                for (Cell srcCell : srcRow) {
                    String value = srcCell.getContents();
                    com.quantxt.io.model.CellStyle cellStyle =
                            new com.quantxt.io.model.CellStyle(srcCell.getCellFormat().getFormat().getFormatString());
                    com.quantxt.io.model.Cell cell =
                            new QTCell(row, srcCell.getColumn(), from(srcCell.getType()), value);
                    cell.setCellStyle(cellStyle);
                    row.getCells().put(srcCell.getColumn(), cell);
                }
            };
        }

        return qtWorkbook;
    }


    private com.quantxt.io.model.CellType from(jxl.CellType jxlCellType){
        switch (jxlCellType.toString()){
            case "Label":
                return com.quantxt.io.model.CellType.STRING;
            case "Number":
                return com.quantxt.io.model.CellType.NUMERIC;
            case "Boolean":
                return com.quantxt.io.model.CellType.BOOLEAN;
            case "Empty":
                return com.quantxt.io.model.CellType.BLANK;
            case "Error":
                return com.quantxt.io.model.CellType.ERROR;
            case "Numerical Formula":
            case "Date Formula":
            case "String Formula" :
            case "Boolean Formula":
            case "Formula Error":
                return com.quantxt.io.model.CellType.FORMULA;
            default: return com.quantxt.io.model.CellType.BLANK;
        }
    }
}