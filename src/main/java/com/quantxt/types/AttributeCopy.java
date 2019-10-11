package com.quantxt.types;

public class AttributeCopy {

    private int index;
    private String name;
    private String nameCode;
    private AttrType type;

    public AttributeCopy(int index, String name, String nameCode,
            AttrType type) {
        this.index = index;
        this.name = name;
        this.nameCode = nameCode;
        this.type = type;
    }

    public int getIndex() {
        return index;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getNameCode() {
        return nameCode;
    }
    public void setNameCode(String nameCode) {
        this.nameCode = nameCode;
    }
    public AttrType getType() {
        return type;
    }
    public void setType(AttrType type) {
        this.type = type;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AttributeCopy other = (AttributeCopy) obj;
        if (index != other.index)
            return false;
        return true;
    }

}
