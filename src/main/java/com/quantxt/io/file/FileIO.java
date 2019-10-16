package com.quantxt.io.file;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.quantxt.io.Reader;
import com.quantxt.io.Writer;

public class FileIO implements Writer<FileData>, Reader<String, String> {

    private static Logger log = LoggerFactory.getLogger(FileIO.class);

    @Override
    public String read(String source) {
        String text = null;
        try {
            text = FileUtil.readText(source);
        } catch (IOException e) {
            log.error("Error reading file {}", source, e);
        }
        return text;
    }

    @Override
    public void write(FileData fileData) {
        try {
            FileUtil.writeText(fileData.getFileName(), fileData.getText());
        } catch (IOException e) {
            log.error("Error writing text to file {}",
                    fileData.getFileName(), e);
        }
    }

}
