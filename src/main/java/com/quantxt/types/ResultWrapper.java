package com.quantxt.types;

import java.util.Set;

import com.quantxt.io.model.Template;

public class ResultWrapper {

    private final Template template;
    private final Set<String> sheetNames;
    private final ResultData resultData;

    public ResultWrapper(Template template, Set<String> sheetNames,
            ResultData resultData) {
        this.sheetNames = sheetNames;
        this.template = template;
        this.resultData = resultData;
    }

    public Template getTemplate() {
        return template;
    }

    public ResultData getResultData() {
        return resultData;
    }

    public Set<String> getSheetNames() {
        return sheetNames;
    }

}
