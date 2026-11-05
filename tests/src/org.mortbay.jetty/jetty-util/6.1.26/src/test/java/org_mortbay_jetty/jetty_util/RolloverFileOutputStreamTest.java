/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mortbay.util.RolloverFileOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RolloverFileOutputStreamTest {
    @TempDir
    public Path directory;

    @Test
    public void writesToDateExpandedFile() throws Exception {
        Path pattern = directory.resolve("request-yyyy_mm_dd.log");
        Path dated;
        try (RolloverFileOutputStream output = new RolloverFileOutputStream(
                pattern.toString(), true, 7, TimeZone.getTimeZone("UTC"), "yyyy_MM_dd", "HHmmssSSS")) {
            dated = Path.of(output.getDatedFilename());
            assertTrue(dated.getFileName().toString().matches("request-\\d{4}_\\d{2}_\\d{2}\\.log"));
            assertEquals(7, output.getRetainDays());
            assertEquals(pattern.toFile().getCanonicalPath(), output.getFilename());
            output.write("first entry\n".getBytes(StandardCharsets.UTF_8));
            byte[] second = "second entry\n".getBytes(StandardCharsets.UTF_8);
            output.write(second, 0, second.length);
            output.flush();
        }
        assertEquals("first entry\nsecond entry\n", Files.readString(dated));
    }

    @Test
    public void appendsToAnExistingLog() throws Exception {
        Path log = directory.resolve("application.log");
        Files.writeString(log, "previous\n");
        try (RolloverFileOutputStream output = new RolloverFileOutputStream(log.toString(), true, 0)) {
            output.write("next\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("previous\nnext\n", Files.readString(log));
    }
}
