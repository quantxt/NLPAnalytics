package com.quantxt.types;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResultCell {

    static final Pattern POS_DOUBLE_PATTERN  = Pattern.compile("^\\s*\\d{1,15}|\\.\\d+|\\d{1,15}\\.\\d+\\s*$");
    private static final String EMPTY_STRING  = "";

    private final Attribute attribute;
    private final int index;
    private String value;
    private boolean highlight;

    public ResultCell(int index, Attribute attribute) {
        this.index = index;
        this.attribute = attribute;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public int getIndex() {
        return index;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setValue(double doubleValue) {
        this.value = String.valueOf(doubleValue);
    }

    public void setValue(long longValue) {
        this.value = String.valueOf(longValue);
    }

    public boolean isHighlight() {
        return highlight;
    }

    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
    }

    public Double getNumericCellValue() {
        if (isEmpty()) {
            return 0d;
        }
        String val = cleanDouble(value);
        Matcher m = POS_DOUBLE_PATTERN.matcher(val);
        return !m.find() ? null : new BigDecimal(Double.parseDouble(val)).doubleValue();
    }

    private String cleanDouble(String str) {
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

    public boolean isEmpty() {
        return isEmpty(value);
    }

    public static boolean isEmpty(String value) {
        return value == null || value.replaceAll("\\s*", EMPTY_STRING).equals(EMPTY_STRING);
    }

    @Override
    public String toString() {
        return "[name=" + (attribute == null ? null : attribute.getName())
                + ", numericType=" + (attribute == null ? null : attribute.isNumericType())
                + ", index=" + index
                + ", value=" + value
                + ", highlight=" + highlight + "]";
    }

}
