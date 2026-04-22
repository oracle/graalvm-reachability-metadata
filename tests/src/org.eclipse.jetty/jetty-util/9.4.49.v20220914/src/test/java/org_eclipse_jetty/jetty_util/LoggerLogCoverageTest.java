/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.LoggerLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerLogCoverageTest {
    public static class RecordingLogger {
        private final String name;
        private final List<String> events = new ArrayList<>();
        private boolean debugEnabled = true;

        public RecordingLogger(String name) {
            this.name = name;
        }

        public void debug(String message, Throwable thrown) {
            events.add("debug-throwable:" + message + ":" + thrown.getMessage());
        }

        public void debug(String message, Object... arguments) {
            events.add("debug-varargs:" + message + ":" + arguments.length);
        }

        public void info(String message, Throwable thrown) {
            events.add("info-throwable:" + message + ":" + thrown.getMessage());
        }

        public void info(String message, Object... arguments) {
            events.add("info-varargs:" + message + ":" + arguments.length);
        }

        public void warn(String message, Throwable thrown) {
            events.add("warn-throwable:" + message + ":" + thrown.getMessage());
        }

        public void warn(String message, Object... arguments) {
            events.add("warn-varargs:" + message + ":" + arguments.length);
        }

        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        public void setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
        }

        public RecordingLogger getLogger(String loggerName) {
            return new RecordingLogger(loggerName);
        }

        public String getName() {
            return name;
        }
    }

    @Test
    void loggerLogDelegatesThroughReflectedMethods() {
        RecordingLogger recordingLogger = new RecordingLogger("logger-log-coverage");
        LoggerLog logger = new LoggerLog(recordingLogger);

        assertThat(logger.getName()).isEqualTo("logger-log-coverage");

        logger.warn("unused", "warn {}", new Object[]{"value"});
        logger.warn("warn", new IllegalStateException("warn"));

        logger.info("unused", "info {}", new Object[]{"value"});
        logger.info("info", new IllegalStateException("info"));

        logger.setDebugEnabled(true);
        assertThat(logger.isDebugEnabled()).isTrue();

        logger.debug("unused", "debug {}", new Object[]{"value"});
        logger.debug("debug", new IllegalStateException("debug"));

        String stderr = captureStandardError(() -> {
            logger.debug("debug-long", 7L);
            logger.debug("debug-long", Long.MAX_VALUE);
        });

        assertThat(stderr).contains("IllegalArgumentException");
        assertThat(recordingLogger.events)
            .contains("warn-varargs:warn {}:1")
            .contains("warn-throwable:warn:warn")
            .contains("info-varargs:info {}:1")
            .contains("info-throwable:info:info")
            .contains("debug-varargs:debug {}:1")
            .contains("debug-throwable:debug:debug");

        Logger child = logger.getLogger("child");
        assertThat(child.getName()).isEqualTo("logger-log-coverage.child");
    }

    private static String captureStandardError(Runnable action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        try (PrintStream capturedErr = new PrintStream(errBuffer, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedErr);
            action.run();
        } finally {
            System.setErr(originalErr);
        }

        return errBuffer.toString(StandardCharsets.UTF_8);
    }
}
