/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.core.appender.MemoryMappedFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class MemoryMappedFileManagerTest {
    private static final int REGION_LENGTH = 256;

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(20)
    void closeReleasesMappedBufferAndTruncatesFileToWrittenBytes() throws IOException {
        final Path logFile = temporaryDirectory.resolve("memory-mapped.log");
        final String message = "written through a memory mapped Log4j manager";
        final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        final MemoryMappedFileManager manager = MemoryMappedFileManager.getFileManager(logFile.toString(), false,
                true, REGION_LENGTH, logFile.toUri().toString(), null);

        try {
            assertThat(manager).isNotNull();
            assertThat(manager.getRegionLength()).isEqualTo(REGION_LENGTH);
            manager.writeBytes(bytes, 0, bytes.length);
            manager.flush();
        } finally {
            if (manager != null) {
                manager.close();
            }
        }

        assertThat(Files.readString(logFile, StandardCharsets.UTF_8)).isEqualTo(message);
    }
}
