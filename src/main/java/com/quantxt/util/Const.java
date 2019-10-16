package com.quantxt.util;

import java.nio.file.FileSystems;
import java.util.regex.Pattern;

public interface Const {

    Pattern POS_INTEGER_PATTERN = Pattern.compile("^\\s*[0-9]{1,10}\\s*$");
    Pattern POS_LONG_PATTERN = Pattern.compile("^\\s*[0-9]{1,19}\\s*$");
    Pattern POS_DOUBLE_PATTERN  = Pattern.compile("^\\s*\\d{1,15}|\\.\\d+|\\d{1,15}\\.\\d+\\s*$");

    String ADDRESS_SCORE = "Address Score";
    String HEADER_SCORE = "Header Score";
    int NUM_RETRIES_HTTP_CLIENT = 5;
    int RETRY_INTERVAL_MILISEC_HTTP_CLIENT = 2000;

    String HIGHLIGHT_ATTR = "highlight_attributes";
    String FILE_SEPARATOR = FileSystems.getDefault().getSeparator();

}
