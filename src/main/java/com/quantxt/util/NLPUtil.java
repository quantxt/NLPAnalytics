package com.quantxt.util;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.quantxt.types.ExtIntervalSimple;
import com.quantxt.types.MapSort;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NLPUtil {

    private static Logger logger = LoggerFactory.getLogger(NLPUtil.class);

    public static String cleanDouble(String str) {
        if (str == null || str.isEmpty()) return "";
        boolean isnegative = false;

        if (str.trim().startsWith("-")) {
            isnegative = true;
        }
        str = str.replaceAll("[^\\d\\.]", "");
        str = str.replaceAll("\\.(?=.*\\.)", ""); // Replace all but last dot
        if (isnegative) {
            return "-" + str;
        }
        return str;
    }

    public static void formatTargetWB(XSSFWorkbook wb, Set<String> sheetNames) {
        for (String sname : sheetNames)
        {
            XSSFSheet sheet = wb.getSheet(sname);
            List<XSSFTable> tables = sheet.getTables();
            for (XSSFTable table : tables) {
                CellReference ref = table.getStartCellReference();
                if (ref == null) continue;
                int headerRow = ref.getRow();
                int firstHeaderColumn = ref.getCol();
                XSSFRow row = sheet.getRow(headerRow);
                if (row != null && row.getCTRow().validate()) {
                    int cellnum = firstHeaderColumn;
                    for (CTTableColumn col : table.getCTTable().getTableColumns().getTableColumnArray()) {
                        XSSFCell cell = row.getCell(cellnum);
                        if (cell != null) {
                            try {
                                String str = cell.getStringCellValue();

                            } catch (Exception e){
                                logger.error("Error in pulling String data from cell: " + cell.getRowIndex() + "x" + cell.getColumnIndex() + " || " + e.getMessage() );
                                //try to get numeric
                                try {
                                    logger.info(cell.getNumericCellValue() + " / " + table.getName() + " / " + sname + " / " + cell.getRowIndex() + "x" + cell.getColumnIndex());
                                    double nval = cell.getNumericCellValue();
                                    //    cell.setCellType(Cell.CELL_TYPE_STRING);
                                    XSSFCell copyCell = row.createCell(cell.getColumnIndex(), CellType.STRING);
                                    copyCell.setCellValue("");
                                    logger.info("nval : " + nval);
                                } catch  (Exception ee){
                                    logger.error("Error in pulling Numeric data from cell: " + cell.getRowIndex() + "x" + cell.getColumnIndex() + " || " +ee.getMessage() );
                                }
                            }
                            //    col.setName(cell.getStringCellValue());
                        }
                        cellnum++;
                    }
                }
            }
        }
    }

    private static ExtIntervalSimple findSpanHelper(String str,
                                                    int startPos,
                                                    List<String> tokenList)
    {
        if (tokenList.size() == 0) {
            return null;
        }
        int strLength = str.length();
        if (strLength == 0) return null;
        int end = startPos;
        for (int c = 0; c < tokenList.size(); c++) {
            String t = tokenList.get(c);
            if (t.length() == 0) continue;
            int pos = str.indexOf(t, end);
            if (pos < 0) {
                return null;
            }
            end = pos + t.length();
            if (end > strLength) return null;
        }

        return new ExtIntervalSimple(startPos, end);
    }

    public static ExtIntervalSimple findSpan(String str,
                                             List<String> tokenList) {
        if (tokenList == null || tokenList.size() == 0)
            return null;
        str = str.trim();

        Map<ExtIntervalSimple, Integer> allMatahces = new HashMap<>();
        String firstToken = tokenList.get(0);
        int cursor = 0;
        while (cursor >=0){
            int pos = str.indexOf(firstToken, cursor);
            if (pos < 0) break;
            ExtIntervalSimple ext = findSpanHelper(str, pos, tokenList);
            if (ext != null){
                allMatahces.put(ext, ext.getEnd() - ext.getStart());
            }
            cursor = pos + firstToken.length();
        }
        if (allMatahces.size() == 0){
            return null;
        }
        //find shortest ..
        // if there multiple shorts then we pick the last one (the one closer to the end of utterance)
        Map<ExtIntervalSimple, Integer> sorted = MapSort.sortByValue(allMatahces);
        int shortestLnegth = sorted.entrySet().iterator().next().getValue();
        ExtIntervalSimple res = sorted.entrySet().iterator().next().getKey();

        for (Map.Entry<ExtIntervalSimple, Integer> e : sorted.entrySet()){
            int length = e.getValue();
            if (length != shortestLnegth) break;
            ExtIntervalSimple key = e.getKey();
            if (key.getStart() > res.getStart()){
                res = key;
            }
        }
        return res;
    }

    public static ExtIntervalSimple [] findAllSpans(String str, String[] tokens){
        ExtIntervalSimple [] allSpans = new ExtIntervalSimple[tokens.length];

        int cursor = 0;
        for (int i=0; i<tokens.length; i++){
            String t = tokens[i];
            int pos = str.indexOf(t, cursor);
            if (pos < 0) {
                logger.warn("Token {} as not found in the string {}", t , str);
                continue;
            }
            ExtIntervalSimple exi = new ExtIntervalSimple(pos, pos + t.length());
            allSpans[i] = exi;
            cursor = exi.getEnd();

        }
        return allSpans;
    }

    public static boolean isEmpty(String value) {
        return value == null || value.replaceAll("\\s*", "").equals("");
    }


    // Maybe can make it more generic
    public static void write(String filename, InputStream wbInputStream) throws Exception {
        byte[] buffer = new byte[wbInputStream.available()];
        wbInputStream.read(buffer);

        FileOutputStream fileOut = new FileOutputStream(filename);
        fileOut.write(buffer);
        fileOut.close();
    }

    // Maybe can meke it more generic
    public static ByteArrayOutputStream getStream(InputStream wbInputStream) throws Exception {
        byte[] buffer = new byte[wbInputStream.available()];
        wbInputStream.read(buffer);

        ByteArrayOutputStream objectStream = new ByteArrayOutputStream();
        objectStream.write(buffer);
        return objectStream;
    }

    public static String convertCellIndexToColumnLetter(int cellIndex) {
        return CellReference.convertNumToColString(cellIndex);
    }

}
