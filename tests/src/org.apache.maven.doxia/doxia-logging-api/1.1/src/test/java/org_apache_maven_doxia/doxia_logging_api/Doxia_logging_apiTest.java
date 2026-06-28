/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_logging_api;

import static org.assertj.core.api.Assertions.assertThat;

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
    @Test
    void systemStreamLogEnablesMessagesAtAndAboveConfiguredLevel() {
        SystemStreamLog log = new SystemStreamLog();

        assertThat(enabledLevels(log)).containsExactly("info", "warn", "error");

        log.setLogLevel(Log.LEVEL_DEBUG);
        assertThat(enabledLevels(log)).containsExactly("debug", "info", "warn", "error");

        log.setLogLevel(Log.LEVEL_WARN);
        assertThat(enabledLevels(log)).containsExactly("warn", "error");

        log.setLogLevel(Log.LEVEL_ERROR);
        assertThat(enabledLevels(log)).containsExactly("error");

        log.setLogLevel(Log.LEVEL_DISABLED);
        assertThat(enabledLevels(log)).isEmpty();
    }

    @Test
    void systemStreamLogClampsOutOfRangeLevels() {
        SystemStreamLog log = new SystemStreamLog();

        log.setLogLevel(Integer.MIN_VALUE);
        assertThat(enabledLevels(log)).containsExactly("debug", "info", "warn", "error");

        log.setLogLevel(Log.LEVEL_FATAL);
        assertThat(enabledLevels(log)).isEmpty();

        log.setLogLevel(Integer.MAX_VALUE);
        assertThat(enabledLevels(log)).isEmpty();
    }

    @Test
    void systemStreamLogWritesEnabledStandardMessagesToExpectedStreams() {
        SystemStreamLog log = new SystemStreamLog();
        log.setLogLevel(Log.LEVEL_DEBUG);

        CapturedStreams capturedStreams = captureStreams(() -> {
            log.debug(new StringBuilder("debug-message"));
            log.info("info-message");
            log.warn("warn-message");
            log.error("error-message");
        });

        assertThat(capturedStreams.out()).contains("[debug] debug-message")
                .contains("[info] info-message")
                .contains("[warn] warn-message");
        assertThat(capturedStreams.err()).contains("[error] error-message");
    }

    @Test
    void systemStreamLogSuppressesDisabledMessages() {
        SystemStreamLog log = new SystemStreamLog();
        log.setLogLevel(Log.LEVEL_WARN);

        CapturedStreams capturedStreams = captureStreams(() -> {
            log.debug("hidden-debug");
            log.debug("hidden-debug-with-cause", new IllegalStateException("debug-cause"));
            log.debug(new IllegalStateException("debug-only-cause"));
            log.info("hidden-info");
            log.info("hidden-info-with-cause", new IllegalStateException("info-cause"));
            log.info(new IllegalStateException("info-only-cause"));
        });

        assertThat(capturedStreams.out()).isEmpty();
        assertThat(capturedStreams.err()).isEmpty();
    }

    @Test
    void systemStreamLogWritesThrowableForms() {
        SystemStreamLog log = new SystemStreamLog();
        log.setLogLevel(Log.LEVEL_DEBUG);

        IllegalArgumentException debugCause = new IllegalArgumentException("debug-cause");
        IllegalStateException infoCause = new IllegalStateException("info-cause");
        UnsupportedOperationException warnCause = new UnsupportedOperationException("warn-cause");
        RuntimeException errorCause = new RuntimeException("error-cause");

        CapturedStreams capturedStreams = captureStreams(() -> {
            log.debug("debug-with-cause", debugCause);
            log.info(infoCause);
            log.warn("warn-with-cause", warnCause);
            log.error(errorCause);
        });

        assertThat(capturedStreams.out()).contains("[debug] debug-with-cause")
                .contains("java.lang.IllegalArgumentException: debug-cause")
                .contains("[info] java.lang.IllegalStateException: info-cause")
                .contains("[warn] warn-with-cause")
                .contains("java.lang.UnsupportedOperationException: warn-cause");
        assertThat(capturedStreams.err()).contains("[error] java.lang.RuntimeException: error-cause");
    }

    @Test
    void systemStreamLogWritesDebugAndWarnThrowableOnlyFormsToOutputStream() {
        SystemStreamLog log = new SystemStreamLog();
        log.setLogLevel(Log.LEVEL_DEBUG);
        IllegalArgumentException debugCause = new IllegalArgumentException("debug-only-enabled-cause");
        UnsupportedOperationException warnCause = new UnsupportedOperationException("warn-only-enabled-cause");

        CapturedStreams capturedStreams = captureStreams(() -> {
            log.debug(debugCause);
            log.warn(warnCause);
        });

        assertThat(capturedStreams.out()).contains("[debug]")
                .contains("java.lang.IllegalArgumentException: debug-only-enabled-cause")
                .contains("[warn]")
                .contains("java.lang.UnsupportedOperationException: warn-only-enabled-cause");
        assertThat(capturedStreams.err()).isEmpty();
    }

    @Test
    void systemStreamLogWritesErrorMessagesWithCausesOnlyToErrorStream() {
        SystemStreamLog log = new SystemStreamLog();
        IllegalStateException cause = new IllegalStateException("system-error-cause");

        CapturedStreams capturedStreams = captureStreams(() -> log.error("system-error-message", cause));

        assertThat(capturedStreams.out()).isEmpty();
        assertThat(capturedStreams.err()).contains("system-error-message")
                .contains("java.lang.IllegalStateException: system-error-cause");
    }

    @Test
    void plexusLoggerWrapperDelegatesThresholdsAndEnabledChecks() {
        RecordingPlexusLogger logger = new RecordingPlexusLogger();
        PlexusLoggerWrapper wrapper = new PlexusLoggerWrapper(logger);

        wrapper.setLogLevel(Integer.MIN_VALUE);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_DEBUG);
        assertThat(wrapper.isDebugEnabled()).isTrue();

        wrapper.setLogLevel(Log.LEVEL_INFO);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_INFO);
        assertThat(wrapper.isDebugEnabled()).isFalse();
        assertThat(wrapper.isInfoEnabled()).isTrue();

        wrapper.setLogLevel(Log.LEVEL_WARN);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_WARN);
        assertThat(wrapper.isInfoEnabled()).isFalse();
        assertThat(wrapper.isWarnEnabled()).isTrue();

        wrapper.setLogLevel(Log.LEVEL_ERROR);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_ERROR);
        assertThat(wrapper.isWarnEnabled()).isFalse();
        assertThat(wrapper.isErrorEnabled()).isTrue();

        wrapper.setLogLevel(Log.LEVEL_FATAL);
        assertThat(logger.getThreshold()).isEqualTo(Logger.LEVEL_DISABLED);
        assertThat(wrapper.isErrorEnabled()).isFalse();
    }

    @Test
    void plexusLoggerWrapperDelegatesAllLogMethods() {
        RecordingPlexusLogger logger = new RecordingPlexusLogger();
        PlexusLoggerWrapper wrapper = new PlexusLoggerWrapper(logger);
        IllegalStateException cause = new IllegalStateException("delegated-cause");

        wrapper.debug("debug-message");
        wrapper.debug(new StringBuilder("debug-with-cause"), cause);
        wrapper.debug(cause);
        wrapper.info("info-message");
        wrapper.info("info-with-cause", cause);
        wrapper.info(cause);
        wrapper.warn("warn-message");
        wrapper.warn("warn-with-cause", cause);
        wrapper.warn(cause);
        wrapper.error("error-message");
        wrapper.error("error-with-cause", cause);
        wrapper.error(cause);

        assertThat(logger.events()).containsExactly(
                new LogEvent("debug", "debug-message", null),
                new LogEvent("debug", "debug-with-cause", cause),
                new LogEvent("debug", "", cause),
                new LogEvent("info", "info-message", null),
                new LogEvent("info", "info-with-cause", cause),
                new LogEvent("info", "", cause),
                new LogEvent("warn", "warn-message", null),
                new LogEvent("warn", "warn-with-cause", cause),
                new LogEvent("warn", "", cause),
                new LogEvent("error", "error-message", null),
                new LogEvent("error", "error-with-cause", cause),
                new LogEvent("error", "", cause));
    }

    @Test
    void plexusLoggerWrapperConvertsNullMessagesToEmptyStrings() {
        RecordingPlexusLogger logger = new RecordingPlexusLogger();
        PlexusLoggerWrapper wrapper = new PlexusLoggerWrapper(logger);
        IllegalArgumentException cause = new IllegalArgumentException("null-message-cause");

        wrapper.debug((CharSequence) null);
        wrapper.info(null, cause);
        wrapper.warn((CharSequence) null);
        wrapper.error(null, cause);

        assertThat(logger.events()).containsExactly(
                new LogEvent("debug", "", null),
                new LogEvent("info", "", cause),
                new LogEvent("warn", "", null),
                new LogEvent("error", "", cause));
    }

    @Test
    void logEnabledImplementationsReceiveALogInstance() {
        RecordingLogEnabled component = new RecordingLogEnabled();
        Log log = new SystemStreamLog();

        component.enableLogging(log);

        assertThat(component.log()).isSameAs(log);
    }

    private static List<String> enabledLevels(Log log) {
        List<String> levels = new ArrayList<>();
        if (log.isDebugEnabled()) {
            levels.add("debug");
        }
        if (log.isInfoEnabled()) {
            levels.add("info");
        }
        if (log.isWarnEnabled()) {
            levels.add("warn");
        }
        if (log.isErrorEnabled()) {
            levels.add("error");
        }
        return levels;
    }

    private static CapturedStreams captureStreams(Runnable runnable) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (PrintStream replacementOut = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream replacementErr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(replacementOut);
            System.setErr(replacementErr);
            runnable.run();
            replacementOut.flush();
            replacementErr.flush();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new CapturedStreams(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record CapturedStreams(String out, String err) {
    }

    private record LogEvent(String level, String message, Throwable throwable) {
    }

    private static final class RecordingLogEnabled implements LogEnabled {
        private Log log;

        @Override
        public void enableLogging(Log log) {
            this.log = log;
        }

        private Log log() {
            return log;
        }
    }

    private static final class RecordingPlexusLogger implements Logger {
        private final List<LogEvent> events = new ArrayList<>();
        private int threshold = Logger.LEVEL_INFO;

        private List<LogEvent> events() {
            return events;
        }

        @Override
        public void debug(String message) {
            events.add(new LogEvent("debug", message, null));
        }

        @Override
        public void debug(String message, Throwable throwable) {
            events.add(new LogEvent("debug", message, throwable));
        }

        @Override
        public boolean isDebugEnabled() {
            return threshold <= Logger.LEVEL_DEBUG;
        }

        @Override
        public void info(String message) {
            events.add(new LogEvent("info", message, null));
        }

        @Override
        public void info(String message, Throwable throwable) {
            events.add(new LogEvent("info", message, throwable));
        }

        @Override
        public boolean isInfoEnabled() {
            return threshold <= Logger.LEVEL_INFO;
        }

        @Override
        public void warn(String message) {
            events.add(new LogEvent("warn", message, null));
        }

        @Override
        public void warn(String message, Throwable throwable) {
            events.add(new LogEvent("warn", message, throwable));
        }

        @Override
        public boolean isWarnEnabled() {
            return threshold <= Logger.LEVEL_WARN;
        }

        @Override
        public void error(String message) {
            events.add(new LogEvent("error", message, null));
        }

        @Override
        public void error(String message, Throwable throwable) {
            events.add(new LogEvent("error", message, throwable));
        }

        @Override
        public boolean isErrorEnabled() {
            return threshold <= Logger.LEVEL_ERROR;
        }

        @Override
        public void fatalError(String message) {
            events.add(new LogEvent("fatal", message, null));
        }

        @Override
        public void fatalError(String message, Throwable throwable) {
            events.add(new LogEvent("fatal", message, throwable));
        }

        @Override
        public boolean isFatalErrorEnabled() {
            return threshold <= Logger.LEVEL_FATAL;
        }

        @Override
        public Logger getChildLogger(String name) {
            return this;
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
            return "recording";
        }
    }
}
