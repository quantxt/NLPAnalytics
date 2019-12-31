package com.quantxt.types;

import java.math.BigDecimal;
import java.util.regex.Matcher;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.quantxt.util.Const;
import com.quantxt.util.NLPUtil;

public class ResultCell {
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

    @JsonIgnore
    public Double getNumericCellValue() {
        if (isEmpty()) {
            return 0d;
        }
        String val = NLPUtil.cleanDouble(value);
        Matcher m = Const.POS_DOUBLE_PATTERN.matcher(val);
        return !m.find() ? null : new BigDecimal(Double.parseDouble(val)).doubleValue();
    }

    @JsonIgnore
    public Integer getIntegerCellValue() {
        if (isEmpty()) {
            return 0;
        }
        String val = NLPUtil.cleanDouble(value);
        Matcher m = Const.POS_DOUBLE_PATTERN.matcher(val);
        return !m.find() ? null : (int) Double.parseDouble(val);
    }

    @JsonIgnore
    public Long getLongCellValue() {
        if (isEmpty()) {
            return 0L;
        }
        String val = NLPUtil.cleanDouble(value);
        Matcher m = Const.POS_DOUBLE_PATTERN.matcher(val);
        return !m.find() ? null : (long) Double.parseDouble(val);
    }

    @JsonIgnore
    public boolean isNumericType() {
        return attribute.getType() == AttrType.DOUBLE
                || attribute.getType() == AttrType.PERCENT;
    }

    public boolean isEmpty() {
        return NLPUtil.isEmpty(value);
    }

    @Override
    public String toString() {
        return "[name=" + (attribute == null ? null : attribute.getName())
                + ", type=" + (attribute == null ? null : attribute.getType())
                + ", index=" + index
                + ", value=" + value
                + ", highlight=" + highlight + "]";
    }

}
