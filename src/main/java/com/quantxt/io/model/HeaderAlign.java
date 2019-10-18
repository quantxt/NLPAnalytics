package com.quantxt.io.model;

import java.util.UUID;

public class HeaderAlign {
    private final String uuid = UUID.randomUUID().toString();

    private final Integer targetIndex;
    private final String targetHeader;

    private final Integer sourceIndex;
    private final String sourceHeader;

    public HeaderAlign(Integer targetIndex, String targetHeader, Integer sourceIndex,
            String sourceHeader) {
        this.targetIndex = targetIndex;
        this.targetHeader = targetHeader;
        this.sourceIndex = sourceIndex;
        this.sourceHeader = sourceHeader;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
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
        HeaderAlign other = (HeaderAlign) obj;
        if (uuid == null) {
            if (other.uuid != null)
                return false;
        } else if (!uuid.equals(other.uuid))
            return false;
        return true;
    }

    public Integer getTargetIndex() {
        return targetIndex;
    }

    public String getTargetHeader() {
        return targetHeader;
    }

    public Integer getSourceIndex() {
        return sourceIndex;
    }

    public String getSourceHeader() {
        return sourceHeader;
    }

}
