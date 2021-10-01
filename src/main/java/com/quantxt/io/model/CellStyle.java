package com.quantxt.io.model;

public class CellStyle {

    private String dataFormatString;

    public CellStyle() {
    }

    public CellStyle(String dataFormatString) {
        this.dataFormatString = dataFormatString;
    }

    public String getDataFormatString() {
        return dataFormatString;
    }

    public void setDataFormatString(String dataFormatString) {
        this.dataFormatString = dataFormatString;
    }

}
