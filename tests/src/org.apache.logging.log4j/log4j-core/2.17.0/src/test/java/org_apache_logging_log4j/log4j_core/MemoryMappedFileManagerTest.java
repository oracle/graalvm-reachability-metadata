/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.core.appender.MemoryMappedFileAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MemoryMappedFileManagerTest {
    @TempDir
    Path directory;

    @Test
    void writesAndTruncatesAMappedLogFileOnClose() throws Exception {
        Path file = directory.resolve("mapped.log");
        MemoryMappedFileAppender appender = MemoryMappedFileAppender.newBuilder()
                .setName("mapped-log").setFileName(file.toString()).setAppend(false).setRegionLength(4096)
                .setLayout(PatternLayout.newBuilder().withPattern("%message").build()).build();
        assertNotNull(appender);
        appender.start();
        try {
            appender.append(TestSupport.event("mapped content"));
        } finally {
            appender.stop();
        }
        assertEquals("mapped content", Files.readString(file));
    }
}
