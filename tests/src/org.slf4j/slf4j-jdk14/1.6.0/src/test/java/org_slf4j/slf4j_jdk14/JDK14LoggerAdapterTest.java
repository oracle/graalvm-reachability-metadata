/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_jdk14;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class JDK14LoggerAdapterTest {

    @Test
    void loggerFactoryBridgesSlf4jCallsToJul() {
        try (TestLoggerContext context = TestLoggerContext.create("slf4j-jdk14-adapter-standard")) {
            LocationAwareLogger logger = context.logger();
            IllegalStateException failure = new IllegalStateException("boom");

            logger.trace("trace {}", "message");
            logger.info("info {} {}", new Object[]{"first", "second"});
            logger.error("error message", failure);

            List<LogRecord> records = context.records();

            assertThat(logger.getName()).isEqualTo("slf4j-jdk14-adapter-standard");
            assertThat(records).hasSize(3);
            assertThat(records).extracting(LogRecord::getLevel)
                    .containsExactly(Level.FINEST, Level.INFO, Level.SEVERE);
            assertThat(records).extracting(LogRecord::getMessage)
                    .containsExactly("trace message", "info first second", "error message");
            assertThat(records).extracting(LogRecord::getLoggerName)
                    .containsOnly("slf4j-jdk14-adapter-standard");
            assertThat(records.get(0).getSourceClassName()).isEqualTo(JDK14LoggerAdapterTest.class.getName());
            assertThat(records.get(0).getSourceMethodName()).isEqualTo("loggerFactoryBridgesSlf4jCallsToJul");
            assertThat(records.get(2).getThrown()).isSameAs(failure);
        }
    }

    @Test
    void locationAwareLoggingUsesCallerBoundaryForSourceInformation() {
        try (TestLoggerContext context = TestLoggerContext.create("slf4j-jdk14-adapter-location-aware")) {
            LocationAwareLogger logger = context.logger();
            IllegalArgumentException failure = new IllegalArgumentException("warn");

            LocationAwareInvoker.logWarning(logger, "warn message", failure);

            assertThat(context.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getMessage()).isEqualTo("warn message");
                assertThat(record.getThrown()).isSameAs(failure);
                assertThat(record.getSourceClassName()).isEqualTo(JDK14LoggerAdapterTest.class.getName());
                assertThat(record.getSourceMethodName())
                        .isEqualTo("locationAwareLoggingUsesCallerBoundaryForSourceInformation");
            });
        }
    }

    private static final class TestLoggerContext implements AutoCloseable {

        private final Logger julLogger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;
        private final Handler[] previousHandlers;
        private final RecordingHandler handler;
        private final LocationAwareLogger logger;

        private TestLoggerContext(String loggerName) {
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
            this.logger = (LocationAwareLogger) LoggerFactory.getLogger(loggerName);
        }

        private static TestLoggerContext create(String loggerName) {
            return new TestLoggerContext(loggerName);
        }

        private LocationAwareLogger logger() {
            return this.logger;
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

    private static final class LocationAwareInvoker {

        private static void logWarning(LocationAwareLogger logger, String message, Throwable throwable) {
            logger.log(null, LocationAwareInvoker.class.getName(), LocationAwareLogger.WARN_INT, message, throwable);
        }
    }
}
