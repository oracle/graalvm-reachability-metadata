/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_logging_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.doxia.logging.Log;
import org.apache.maven.doxia.logging.LogEnabled;
import org.apache.maven.doxia.logging.PlexusLoggerWrapper;
import org.apache.maven.doxia.logging.SystemStreamLog;
import org.codehaus.plexus.logging.Logger;
import org.junit.jupiter.api.Test;

public class Doxia_logging_apiTest {
    private static final Object STREAM_LOCK = new Object();

    @Test
    void systemStreamLogEnablesLevelsAccordingToThreshold() {
        SystemStreamLog log = new SystemStreamLog();

        assertEnabledLevels(log, false, true, true, true);

        log.setLogLevel(Log.LEVEL_DEBUG);
        assertEnabledLevels(log, true, true, true, true);

        log.setLogLevel(Log.LEVEL_INFO);
        assertEnabledLevels(log, false, true, true, true);

        log.setLogLevel(Log.LEVEL_WARN);
        assertEnabledLevels(log, false, false, true, true);

        log.setLogLevel(Log.LEVEL_ERROR);
        assertEnabledLevels(log, false, false, false, true);

        log.setLogLevel(Log.LEVEL_FATAL);
        assertEnabledLevels(log, false, false, false, false);

        log.setLogLevel(Log.LEVEL_DISABLED);
        assertEnabledLevels(log, false, false, false, false);
    }

    @Test
    void systemStreamLogWritesEnabledMessagesAndStackTracesToExpectedStreams() throws Exception {
        CapturedOutput output = captureSystemStreams(() -> {
            SystemStreamLog log = new SystemStreamLog();
            IllegalStateException failure = new IllegalStateException("boom");

            log.setLogLevel(Log.LEVEL_DEBUG);
            log.debug(new StringBuilder("debug-message"));
            log.debug("debug-with-throwable", failure);
            log.info("info-message");
            log.info(failure);
            log.warn("warn-message");
            log.warn("warn-with-throwable", failure);
            log.error("error-message");
            log.error("error-with-throwable", failure);
            log.error(failure);
        });

        assertThat(output.out())
                .contains("[debug] debug-message")
                .contains("[debug] debug-with-throwable")
                .contains("[info] info-message")
                .contains("[info] java.lang.IllegalStateException: boom")
                .contains("[warn] warn-message")
                .contains("[warn] warn-with-throwable")
                .contains("java.lang.IllegalStateException: boom");
        assertThat(output.err())
                .contains("[error] error-message")
                .contains("[error] error-with-throwable")
                .contains("[error] java.lang.IllegalStateException: boom")
                .contains("java.lang.IllegalStateException: boom");
    }

    @Test
    void systemStreamLogDoesNotFormatMessagesForDisabledLevels() throws Exception {
        CapturedOutput output = captureSystemStreams(() -> {
            SystemStreamLog log = new SystemStreamLog();
            ThrowingCharSequence message = new ThrowingCharSequence();

            log.setLogLevel(Log.LEVEL_DISABLED);
            log.debug(message);
            log.info(message);
            log.warn(message);
            log.error(message);
        });

        assertThat(output.out()).isEmpty();
        assertThat(output.err()).isEmpty();
    }

    @Test
    void plexusLoggerWrapperDelegatesThresholdAndEnabledChecks() {
        RecordingLogger logger = new RecordingLogger("root");
        PlexusLoggerWrapper wrapper = new PlexusLoggerWrapper(logger);

        wrapper.setLogLevel(Log.LEVEL_DEBUG);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_DEBUG);
        assertEnabledLevels(wrapper, true, true, true, true);

        wrapper.setLogLevel(Log.LEVEL_INFO);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_INFO);
        assertEnabledLevels(wrapper, false, true, true, true);

        wrapper.setLogLevel(Log.LEVEL_WARN);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_WARN);
        assertEnabledLevels(wrapper, false, false, true, true);

        wrapper.setLogLevel(Log.LEVEL_ERROR);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_ERROR);
        assertEnabledLevels(wrapper, false, false, false, true);

        wrapper.setLogLevel(Log.LEVEL_DISABLED);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_DISABLED);
        assertEnabledLevels(wrapper, false, false, false, false);
    }

    @Test
    void plexusLoggerWrapperDelegatesMessagesThrowablesAndNulls() {
        RecordingLogger logger = new RecordingLogger("root");
        PlexusLoggerWrapper wrapper = new PlexusLoggerWrapper(logger);
        IllegalArgumentException failure = new IllegalArgumentException("invalid");

        wrapper.debug(new StringBuilder("debug-message"));
        wrapper.debug("debug-with-throwable", failure);
        wrapper.debug(failure);
        wrapper.info((CharSequence) null);
        wrapper.info("info-with-throwable", failure);
        wrapper.info(failure);
        wrapper.warn("warn-message");
        wrapper.warn("warn-with-throwable", failure);
        wrapper.warn(failure);
        wrapper.error("error-message");
        wrapper.error("error-with-throwable", failure);
        wrapper.error(failure);

        assertThat(logger.events)
                .extracting(event -> event.level, event -> event.message, event -> event.throwable)
                .containsExactly(
                        tuple("debug", "debug-message", null),
                        tuple("debug", "debug-with-throwable", failure),
                        tuple("debug", "", failure),
                        tuple("info", "", null),
                        tuple("info", "info-with-throwable", failure),
                        tuple("info", "", failure),
                        tuple("warn", "warn-message", null),
                        tuple("warn", "warn-with-throwable", failure),
                        tuple("warn", "", failure),
                        tuple("error", "error-message", null),
                        tuple("error", "error-with-throwable", failure),
                        tuple("error", "", failure));
    }

    @Test
    void logEnabledComponentReceivesAndUsesProvidedLog() {
        RecordingLog log = new RecordingLog();
        LogEnabledComponent component = new LogEnabledComponent();

        component.enableLogging(log);
        component.run("generated message");

        assertThat(log.messages).containsExactly("generated message");
    }

    private static void assertEnabledLevels(
            Log log,
            boolean debugEnabled,
            boolean infoEnabled,
            boolean warnEnabled,
            boolean errorEnabled) {
        assertThat(log.isDebugEnabled()).isEqualTo(debugEnabled);
        assertThat(log.isInfoEnabled()).isEqualTo(infoEnabled);
        assertThat(log.isWarnEnabled()).isEqualTo(warnEnabled);
        assertThat(log.isErrorEnabled()).isEqualTo(errorEnabled);
    }

    private static CapturedOutput captureSystemStreams(CheckedRunnable runnable) throws Exception {
        synchronized (STREAM_LOCK) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            PrintStream capturedOut = new PrintStream(out, true, StandardCharsets.UTF_8.name());
            PrintStream capturedErr = new PrintStream(err, true, StandardCharsets.UTF_8.name());
            try {
                System.setOut(capturedOut);
                System.setErr(capturedErr);
                runnable.run();
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                capturedOut.close();
                capturedErr.close();
            }
            return new CapturedOutput(
                    out.toString(StandardCharsets.UTF_8.name()),
                    err.toString(StandardCharsets.UTF_8.name()));
        }
    }

    private static final class CapturedOutput {
        private final String out;
        private final String err;

        private CapturedOutput(String out, String err) {
            this.out = out;
            this.err = err;
        }

        private String out() {
            return out;
        }

        private String err() {
            return err;
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class ThrowingCharSequence implements CharSequence {
        @Override
        public int length() {
            return 1;
        }

        @Override
        public char charAt(int index) {
            return 'x';
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return this;
        }

        @Override
        public String toString() {
            throw new AssertionError("Disabled log levels must not format messages");
        }
    }

    private static final class RecordingLogger implements Logger {
        private final String name;
        private final List<LoggerEvent> events;
        private int threshold = LEVEL_INFO;

        private RecordingLogger(String name) {
            this(name, new ArrayList<>());
        }

        private RecordingLogger(String name, List<LoggerEvent> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void debug(String message) {
            events.add(new LoggerEvent("debug", message, null));
        }

        @Override
        public void debug(String message, Throwable throwable) {
            events.add(new LoggerEvent("debug", message, throwable));
        }

        @Override
        public boolean isDebugEnabled() {
            return threshold <= LEVEL_DEBUG;
        }

        @Override
        public void info(String message) {
            events.add(new LoggerEvent("info", message, null));
        }

        @Override
        public void info(String message, Throwable throwable) {
            events.add(new LoggerEvent("info", message, throwable));
        }

        @Override
        public boolean isInfoEnabled() {
            return threshold <= LEVEL_INFO;
        }

        @Override
        public void warn(String message) {
            events.add(new LoggerEvent("warn", message, null));
        }

        @Override
        public void warn(String message, Throwable throwable) {
            events.add(new LoggerEvent("warn", message, throwable));
        }

        @Override
        public boolean isWarnEnabled() {
            return threshold <= LEVEL_WARN;
        }

        @Override
        public void error(String message) {
            events.add(new LoggerEvent("error", message, null));
        }

        @Override
        public void error(String message, Throwable throwable) {
            events.add(new LoggerEvent("error", message, throwable));
        }

        @Override
        public boolean isErrorEnabled() {
            return threshold <= LEVEL_ERROR;
        }

        @Override
        public void fatalError(String message) {
            events.add(new LoggerEvent("fatal", message, null));
        }

        @Override
        public void fatalError(String message, Throwable throwable) {
            events.add(new LoggerEvent("fatal", message, throwable));
        }

        @Override
        public boolean isFatalErrorEnabled() {
            return threshold <= LEVEL_FATAL;
        }

        @Override
        public Logger getChildLogger(String childName) {
            return new RecordingLogger(name + "." + childName, events);
        }

        @Override
        public int getThreshold() {
            return threshold;
        }

        @Override
        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class LoggerEvent {
        private final String level;
        private final String message;
        private final Throwable throwable;

        private LoggerEvent(String level, String message, Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
    }

    private static final class LogEnabledComponent implements LogEnabled {
        private Log log;

        @Override
        public void enableLogging(Log log) {
            this.log = log;
        }

        private void run(String message) {
            log.info(message);
        }
    }

    private static final class RecordingLog implements Log {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void setLogLevel(int level) {
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            messages.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            messages.add(content.toString());
        }

        @Override
        public void info(Throwable error) {
            messages.add(error.getMessage());
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}
