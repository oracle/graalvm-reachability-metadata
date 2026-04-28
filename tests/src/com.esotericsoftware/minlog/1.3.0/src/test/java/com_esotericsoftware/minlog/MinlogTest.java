/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.minlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.minlog.Log;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MinlogTest {
    private RecordingLogger logger;

    @BeforeEach
    void setUp() {
        logger = new RecordingLogger();
        Log.setLogger(logger);
        Log.INFO();
    }

    @AfterEach
    void tearDown() {
        Log.setLogger(new Log.Logger());
        Log.INFO();
    }

    @Test
    void defaultInfoLevelEnablesInfoWarnAndErrorOnly() {
        Log.error("database", "connection refused");
        Log.warn("cache", "entry expired");
        Log.info("service", "started");
        Log.debug("service", "debug details");
        Log.trace("service", "trace details");

        assertThat(logger.messages()).containsExactly("connection refused", "entry expired", "started");
        assertThat(logger.levels()).containsExactly(Log.LEVEL_ERROR, Log.LEVEL_WARN, Log.LEVEL_INFO);
        assertThat(Log.ERROR).isTrue();
        assertThat(Log.WARN).isTrue();
        assertThat(Log.INFO).isTrue();
        assertThat(Log.DEBUG).isFalse();
        assertThat(Log.TRACE).isFalse();
    }

    @Test
    void namedLevelMethodsExposeExpectedEnabledFlags() {
        Log.NONE();
        assertFlags(false, false, false, false, false);

        Log.ERROR();
        assertFlags(true, false, false, false, false);

        Log.WARN();
        assertFlags(true, true, false, false, false);

        Log.INFO();
        assertFlags(true, true, true, false, false);

        Log.DEBUG();
        assertFlags(true, true, true, true, false);

        Log.TRACE();
        assertFlags(true, true, true, true, true);
    }

    @Test
    void setAcceptsPublicLevelConstants() {
        Log.set(Log.LEVEL_NONE);
        assertFlags(false, false, false, false, false);

        Log.set(Log.LEVEL_ERROR);
        assertFlags(true, false, false, false, false);

        Log.set(Log.LEVEL_WARN);
        assertFlags(true, true, false, false, false);

        Log.set(Log.LEVEL_INFO);
        assertFlags(true, true, true, false, false);

        Log.set(Log.LEVEL_DEBUG);
        assertFlags(true, true, true, true, false);

        Log.set(Log.LEVEL_TRACE);
        assertFlags(true, true, true, true, true);
    }

    @Test
    void traceLevelDispatchesEverySeverityWithCategoryMessageAndThrowable() {
        IllegalArgumentException exception = new IllegalArgumentException("bad input");

        Log.TRACE();
        Log.error("database", "write failed", exception);
        Log.warn("cache", "near capacity", exception);
        Log.info("service", "ready", exception);
        Log.debug("service", "request details", exception);
        Log.trace("wire", "frame decoded", exception);

        assertThat(logger.records)
                .extracting(record -> record.level)
                .containsExactly(Log.LEVEL_ERROR, Log.LEVEL_WARN, Log.LEVEL_INFO, Log.LEVEL_DEBUG, Log.LEVEL_TRACE);
        assertThat(logger.records)
                .extracting(record -> record.category)
                .containsExactly("database", "cache", "service", "service", "wire");
        assertThat(logger.records)
                .extracting(record -> record.message)
                .containsExactly("write failed", "near capacity", "ready", "request details", "frame decoded");
        assertThat(logger.records)
                .extracting(record -> record.throwable)
                .containsExactly(exception, exception, exception, exception, exception);
    }

    @Test
    void overloadsWithoutAllArgumentsPassNullsToLogger() {
        RuntimeException exception = new RuntimeException("boom");

        Log.TRACE();
        Log.error("error message");
        Log.warn("warn category", "warn message");
        Log.info("info message", exception);
        Log.debug("debug category", "debug message", exception);
        Log.trace("trace message", exception);

        assertThat(logger.records).containsExactly(
                new LogRecord(Log.LEVEL_ERROR, null, "error message", null),
                new LogRecord(Log.LEVEL_WARN, "warn category", "warn message", null),
                new LogRecord(Log.LEVEL_INFO, null, "info message", exception),
                new LogRecord(Log.LEVEL_DEBUG, "debug category", "debug message", exception),
                new LogRecord(Log.LEVEL_TRACE, null, "trace message", exception));
    }

    @Test
    void setLoggerReplacesActiveSinkForSubsequentMessages() {
        RecordingLogger firstLogger = logger;
        RecordingLogger secondLogger = new RecordingLogger();

        Log.info("startup complete");
        Log.setLogger(secondLogger);
        Log.warn("configuration", "using fallback");

        assertThat(firstLogger.records).containsExactly(new LogRecord(Log.LEVEL_INFO, null, "startup complete", null));
        assertThat(secondLogger.records)
                .containsExactly(new LogRecord(Log.LEVEL_WARN, "configuration", "using fallback", null));
    }

    @Test
    void thresholdPreventsDisabledMessagesFromReachingLogger() {
        Log.ERROR();
        Log.error("visible");
        Log.warn("hidden warning");
        Log.info("hidden info");
        Log.debug("hidden debug");
        Log.trace("hidden trace");

        assertThat(logger.records).containsExactly(new LogRecord(Log.LEVEL_ERROR, null, "visible", null));

        logger.records.clear();
        Log.NONE();
        Log.error("hidden error");
        Log.warn("hidden warning");
        Log.info("hidden info");
        Log.debug("hidden debug");
        Log.trace("hidden trace");

        assertThat(logger.records).isEmpty();
    }

    @Test
    void defaultLoggerPrintsFormattedMessageAndStackTraceToSystemOut() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IllegalStateException exception = new IllegalStateException("broken");

        try {
            System.setOut(new PrintStream(output));
            Log.setLogger(new Log.Logger());
            Log.ERROR();

            Log.error("network", "send failed", exception);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();
        assertThat(text).contains(" ERROR:");
        assertThat(text).contains("[network]");
        assertThat(text).contains("send failed");
        assertThat(text).contains("java.lang.IllegalStateException: broken");
    }

    @Test
    void loggerLogCanBeUsedDirectlyWithCustomPrintSink() {
        CapturingPrintLogger printLogger = new CapturingPrintLogger();

        printLogger.log(Log.LEVEL_DEBUG, null, "diagnostic detail", null);

        assertThat(printLogger.lines).hasSize(1);
        assertThat(printLogger.lines.get(0)).matches("\\d{2}:\\d{2} DEBUG: diagnostic detail");
    }

    private static void assertFlags(boolean error, boolean warn, boolean info, boolean debug, boolean trace) {
        assertThat(Log.ERROR).isEqualTo(error);
        assertThat(Log.WARN).isEqualTo(warn);
        assertThat(Log.INFO).isEqualTo(info);
        assertThat(Log.DEBUG).isEqualTo(debug);
        assertThat(Log.TRACE).isEqualTo(trace);
    }

    private static final class RecordingLogger extends Log.Logger {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void log(int level, String category, String message, Throwable throwable) {
            records.add(new LogRecord(level, category, message, throwable));
        }

        private List<Integer> levels() {
            List<Integer> levels = new ArrayList<>();
            for (LogRecord record : records) {
                levels.add(record.level);
            }
            return levels;
        }

        private List<String> messages() {
            List<String> messages = new ArrayList<>();
            for (LogRecord record : records) {
                messages.add(record.message);
            }
            return messages;
        }
    }

    private static final class CapturingPrintLogger extends Log.Logger {
        private final List<String> lines = new ArrayList<>();

        @Override
        protected void print(String message) {
            lines.add(message);
        }
    }

    private static final class LogRecord {
        private final int level;
        private final String category;
        private final String message;
        private final Throwable throwable;

        private LogRecord(int level, String category, String message, Throwable throwable) {
            this.level = level;
            this.category = category;
            this.message = message;
            this.throwable = throwable;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof LogRecord)) {
                return false;
            }
            LogRecord that = (LogRecord) object;
            return level == that.level
                    && Objects.equals(category, that.category)
                    && Objects.equals(message, that.message)
                    && throwable == that.throwable;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, category, message, System.identityHashCode(throwable));
        }
    }
}
