package com.quantxt.io.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Utility class for text file read/write operations
 *
 * @author dejani
 *
 */
public class FileUtil {

    public final static Charset ENCODING = StandardCharsets.UTF_8;
    public final static String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Read all lines for a given file and return them as List.
     * It is used default encoding {@link StandardCharsets#UTF_8}.
     *
     * @param fileName represents file location
     *
     * @return the lines from the file as a List of String
     *
     * @throws IOException Throw IOException
     */
    public static List<String> readLines(String fileName)
            throws IOException {
        return readLines(fileName, ENCODING);
    }

    /**
     * Read all lines for a given file and return them as List with provided encoding.
     *
     * @param fileName represents file location
     * @param encoding represents Charset used for decoding
     *
     * @return the lines from the file as a List of String
     *
     * @throws IOException Throw IOException
     */
    public static List<String> readLines(String fileName, Charset encoding)
            throws IOException {
        Path path = Paths.get(fileName);
        return Files.readAllLines(path, ENCODING);
    }

    /**
     * Read content of file and return as text.
     * It is used default encoding {@link StandardCharsets#UTF_8}.
     *
     * @param fileName represents file location
     *
     * @return text from the file as String
     *
     * @throws IOException Throw IOException
     */
    public static String readText(String fileName) throws IOException {
        return readText(fileName, ENCODING);
    }

    /**
     * Read content of file and return as text using given encoding.
     *
     * @param fileName represents file location
     * @param encoding represents Charset used for decoding
     *
     * @return text from the file as String
     *
     * @throws IOException Throw IOException
     */
    public static String readText(String fileName, Charset encoding)
            throws IOException {
        Path path = Paths.get(fileName);
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path, encoding)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(LINE_SEPARATOR);
            }
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * Write given list of lines into file.
     * It is used default encoding {@link StandardCharsets#UTF_8}.
     *
     * @param fileName represents file location
     * @param lines to be written in file
     *
     * @throws IOException Throw IOException
     */
    public static void writeLines(String fileName, List<String> lines)
            throws IOException {
        writeLines(fileName, lines, ENCODING);
    }

    /**
     * Write given list of lines into file.
     *
     * @param fileName represents file location
     * @param lines to be written in file
     * @param encoding represents Charset use for encoding
     *
     * @throws IOException Throw IOException
     */
    public static void writeLines(String fileName, List<String> lines,
            Charset encoding)
            throws IOException {
        Path path = Paths.get(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(path, encoding)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Write given text into file.
     * It is used default encoding {@link StandardCharsets#UTF_8}.
     *
     * @param fileName represents file location
     * @param text to be written in file
     *
     * @throws IOException Throw IOException
     */
    public static void writeText(String fileName, String text)
            throws IOException {
        writeText(fileName, text, ENCODING);
    }

    /**
     * Write given text into file.
     *
     * @param fileName represents file location
     * @param text to be written in file
     * @param encoding represents Charset use for encoding
     *
     * @throws IOException Throw IOException
     */
    public static void writeText(String fileName, String text, Charset encoding)
            throws IOException {
        Path path = Paths.get(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(path, encoding)) {
            writer.write(text);
        }
    }

}
