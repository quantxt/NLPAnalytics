package com.quantxt.io.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(expected = IOException.class)
    public void expectExceptionForNonExistingFile() throws IOException {
        // GIVEN
        temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();

        // WHEN
        FileUtil.writeText("/abc.txt", "test");
    }

    @Test
    public void testWriteTextDefaultEncoding() throws IOException {
        // GIVEN
        File output = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();

        // WHEN
        FileUtil.writeText(output.getPath(), "test");

        // THEN
        Assertions.assertThat(output).hasContent("test").hasExtension("txt");
    }

    @Test
    public void testWriteTextOtherEncoding() throws IOException {
        // GIVEN
        String content = "<abc></abc>";
        File output = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.xml")
                .toFile();

        // WHEN
        FileUtil.writeText(output.getPath(), content,
                StandardCharsets.ISO_8859_1);

        // THEN
        Assertions.assertThat(output).hasContent(content).hasExtension("xml");
    }

    @Test
    public void testWriteLineDefaultEncoding() throws IOException {
        // GIVEN
        String line = "line 1";
        File output = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();

        // WHEN
        FileUtil.writeLines(output.getPath(), Arrays.asList(line));

        // THEN
        Assertions.assertThat(output).hasContent(line).hasExtension("txt");
    }

    @Test
    public void testWriteLineOtherEncoding() throws IOException {
        // GIVEN
        String line = "line other 1";
        File output = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();

        // WHEN
        FileUtil.writeLines(output.getPath(), Arrays.asList(line),
                StandardCharsets.ISO_8859_1);

        // THEN
        Assertions.assertThat(output).hasContent(line).hasExtension("txt");
    }

    @Test
    public void testReadTextDefaultEncoding() throws IOException {
        // GIVEN
        File tempFile = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();
        String line = "hello world";
        FileUtil.writeText(tempFile.getPath(), line);

        // WHEN
        String text = FileUtil.readText(tempFile.getPath());

        // THEN
        assertNotNull(text);
        assertEquals(text.trim(), line);
    }

    @Test
    public void testReadTextOtherEncoding() throws IOException {
        // GIVEN
        File tempFile = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();
        String line = "hello world";
        FileUtil.writeText(tempFile.getPath(), line, StandardCharsets.ISO_8859_1);

        // WHEN
        String text = FileUtil.readText(tempFile.getPath(),
                StandardCharsets.ISO_8859_1);

        // THEN
        assertNotNull(text);
        assertEquals(text.trim(), line);
    }

    @Test
    public void testReadLinesDefaultEncoding() throws IOException {
        // GIVEN
        File tempFile = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();
        String line = "hello world";
        FileUtil.writeText(tempFile.getPath(), line);

        // WHEN
        List<String> lines = FileUtil.readLines(tempFile.getPath());

        // THEN
        assertNotNull(lines);
        assertFalse(lines.isEmpty());
        assertEquals(lines.get(0), line);
    }

    @Test
    public void testReadLinesOtherEncoding() throws IOException {
        // GIVEN
        File tempFile = temporaryFolder.newFolder("temp")
                .toPath()
                .resolve("output.txt")
                .toFile();
        String line = "hello world";
        FileUtil.writeText(tempFile.getPath(), line);

        // WHEN
        List<String> lines = FileUtil.readLines(tempFile.getPath(),
                StandardCharsets.ISO_8859_1);

        // THEN
        assertNotNull(lines);
        assertFalse(lines.isEmpty());
        assertEquals(lines.get(0), line);
    }
}
