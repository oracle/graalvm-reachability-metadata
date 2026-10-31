/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OnStartupTriggeringPolicyTest {
    @TempDir
    Path directory;

    @Test
    void keepsANewLogFileAndDoesNotRollOnIndividualEvents() throws Exception {
        Path file = directory.resolve("active.log");
        OnStartupTriggeringPolicy policy = OnStartupTriggeringPolicy.createPolicy(1);
        RollingFileAppender appender = RollingFileAppender.newBuilder().setName("startup-rolling")
                .withFileName(file.toString()).withFilePattern(directory.resolve("archive-%i.log").toString())
                .withPolicy(policy).setLayout(PatternLayout.newBuilder().withPattern("%m").build()).build();
        assertNotNull(appender);
        appender.start();
        try {
            appender.append(TestSupport.event("new session"));
            assertFalse(policy.isTriggeringEvent(TestSupport.event("later event")));
        } finally {
            appender.stop();
        }
        assertEquals("new session", Files.readString(file));
        assertFalse(Files.exists(directory.resolve("archive-1.log")));
    }
}
