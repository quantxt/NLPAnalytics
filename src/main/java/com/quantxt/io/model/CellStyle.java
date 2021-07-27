package com.quantxt.io.model;

public class CellStyle {

    short dataFormat;

    public CellStyle() {
    }

    public CellStyle(short dataFormat) {
        this.dataFormat = dataFormat;
    }

    public short getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(short dataFormat) {
        this.dataFormat = dataFormat;
    }

}
