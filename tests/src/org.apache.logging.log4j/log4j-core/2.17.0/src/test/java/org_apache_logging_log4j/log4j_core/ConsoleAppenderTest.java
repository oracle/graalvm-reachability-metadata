/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock(Resources.SYSTEM_OUT)
public class ConsoleAppenderTest {
    @Test
    void followsTheApplicationsCurrentOutputStream() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream original = System.out;
        ConsoleAppender appender = ConsoleAppender.newBuilder().setName("following-console").setFollow(true)
                .setLayout(PatternLayout.newBuilder().withPattern("%level:%message").build()).build();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            appender.start();
            appender.append(TestSupport.event("console message"));
        } finally {
            System.setOut(original);
            appender.stop();
        }
        assertEquals("INFO:console message", bytes.toString(StandardCharsets.UTF_8));
    }
}
