/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mortbay.util.RolloverFileOutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

public class RolloverFileOutputStreamTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDatedLogFileAndWritesBytes() throws Exception {
        Path rolloverPattern = temporaryDirectory.resolve("rollover-yyyy_mm_dd.log");
        String datedFilename;

        try (RolloverFileOutputStream stream = new RolloverFileOutputStream(
                rolloverPattern.toString(), false, 7, TimeZone.getTimeZone("UTC"))) {
            stream.write("jetty rollover".getBytes(StandardCharsets.UTF_8));
            datedFilename = stream.getDatedFilename();

            assertThat(stream.getRetainDays()).isEqualTo(7);
            assertThat(stream.getFilename()).endsWith("rollover-yyyy_mm_dd.log");
            assertThat(datedFilename).doesNotContain("yyyy_mm_dd");
        }

        Path datedFile = Path.of(datedFilename);
        assertThat(datedFile.getParent()).isEqualTo(temporaryDirectory);
        assertThat(Files.readString(datedFile, StandardCharsets.UTF_8)).isEqualTo("jetty rollover");
    }
}
