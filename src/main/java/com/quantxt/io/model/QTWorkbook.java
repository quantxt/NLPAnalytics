package com.quantxt.io.model;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class QTWorkbook implements Workbook {

    private final List<Sheet> sheets = new LinkedList<>();

    @Override
    public Iterator<Sheet> iterator() {
        return sheets.iterator();
    }

    @Override
    public int getNumberOfSheets() {
        return sheets.size();
    }

    @Override
    public String getSheetName(int sheetIndex) {
        return getSheetAt(sheetIndex).getSheetName();
    }

    @Override
    public Sheet getSheetAt(int sheetIndex) {
        validateSheetIndex(sheetIndex);
        return sheets.get(sheetIndex);
    }

    private void validateSheetIndex(int index) {
        int lastSheetIx = sheets.size() - 1;
        if (index < 0 || index > lastSheetIx) {
            String range = "(0.." +    lastSheetIx + ")";
            if (lastSheetIx == -1) {
                range = "(no sheets)";
            }
            throw new IllegalArgumentException("Sheet index ("
                    + index +") is out of range " + range);
        }
    }

    private void validateSheetName(final String sheetName)
            throws IllegalArgumentException {
        if (containsSheet(sheetName)) {
            throw new IllegalArgumentException(
                    "The workbook already contains a sheet named '" + sheetName
                            + "'");
        }
    }

    private boolean containsSheet(String name) {
        return sheets.parallelStream()
                .filter(s -> s.getSheetName().equals(name)).findAny()
                .isPresent();
    }

    public List<Sheet> getSheets() {
        return sheets;
    }

    public Sheet getSheet(String name) {
        return sheets.parallelStream()
                .filter(s -> s.getSheetName().equals(name)).findFirst()
                .orElse(null);
    }

    public Sheet createSheet(String sheetName) {
        validateSheetName(sheetName);
        Sheet sheet = new QTSheet(sheetName, this) {

            @Override
            public boolean isColumnHidden(int columnIndex) {
                return false;
            }
        };
        sheets.add(sheet);
        return sheet;
    }

}
