/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.SLF4JLogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SLF4JLocationAwareLogTest {

    @Test
    void factoryCreatesLocationAwareLogThatBridgesEveryJclLevelToJul() {
        try (TestLoggerContext context = TestLoggerContext.create(uniqueLoggerName("levels"))) {
            Log log = new SLF4JLogFactory().getInstance(context.loggerName());
            IllegalStateException failure = new IllegalStateException("boom");

            assertThat(log.isTraceEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isTrue();
            assertThat(log.isInfoEnabled()).isTrue();
            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isErrorEnabled()).isTrue();
            assertThat(log.isFatalEnabled()).isTrue();

            log.trace("trace message");
            log.debug("debug message");
            log.info("info message");
            log.warn("warn message");
            log.error("error message");
            log.fatal("fatal message", failure);

            List<LogRecord> records = context.records();
            assertThat(records).hasSize(6);
            assertThat(records).extracting(LogRecord::getLevel)
                    .containsExactly(Level.FINEST, Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE, Level.SEVERE);
            assertThat(records).extracting(LogRecord::getMessage)
                    .containsExactly("trace message", "debug message", "info message", "warn message", "error message",
                            "fatal message");
            assertThat(records).extracting(LogRecord::getLoggerName).containsOnly(context.loggerName());
            assertThat(records.get(5).getThrown()).isSameAs(failure);
        }
    }

    @Test
    void loggingUsesCallerLocationAndStringValueMessages() {
        try (TestLoggerContext context = TestLoggerContext.create(uniqueLoggerName("caller"))) {
            Log log = new SLF4JLogFactory().getInstance(context.loggerName());
            Object message = new Object() {
                @Override
                public String toString() {
                    return "converted object message";
                }
            };

            log.warn(message);

            assertThat(context.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getMessage()).isEqualTo("converted object message");
                assertThat(record.getSourceClassName()).isEqualTo(SLF4JLocationAwareLogTest.class.getName());
                assertThat(record.getSourceMethodName()).isEqualTo("loggingUsesCallerLocationAndStringValueMessages");
            });
        }
    }

    private static String uniqueLoggerName(String suffix) {
        return SLF4JLocationAwareLogTest.class.getName() + "." + suffix + "." + System.nanoTime();
    }

    private static final class TestLoggerContext implements AutoCloseable {

        private final String loggerName;
        private final Logger julLogger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;
        private final Handler[] previousHandlers;
        private final RecordingHandler handler;

        private TestLoggerContext(String loggerName) {
            this.loggerName = loggerName;
            this.julLogger = Logger.getLogger(loggerName);
            this.previousLevel = this.julLogger.getLevel();
            this.previousUseParentHandlers = this.julLogger.getUseParentHandlers();
            this.previousHandlers = this.julLogger.getHandlers();
            this.handler = new RecordingHandler();

            for (Handler previousHandler : this.previousHandlers) {
                this.julLogger.removeHandler(previousHandler);
            }

            this.julLogger.setUseParentHandlers(false);
            this.julLogger.setLevel(Level.ALL);
            this.handler.setLevel(Level.ALL);
            this.julLogger.addHandler(this.handler);
        }

        private static TestLoggerContext create(String loggerName) {
            return new TestLoggerContext(loggerName);
        }

        private String loggerName() {
            return this.loggerName;
        }

        private List<LogRecord> records() {
            return List.copyOf(this.handler.records);
        }

        @Override
        public void close() {
            this.julLogger.removeHandler(this.handler);
            this.julLogger.setLevel(this.previousLevel);
            this.julLogger.setUseParentHandlers(this.previousUseParentHandlers);
            for (Handler previousHandler : this.previousHandlers) {
                this.julLogger.addHandler(previousHandler);
            }
        }
    }

    private static final class RecordingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            this.records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
