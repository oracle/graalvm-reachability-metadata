/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_log4j12;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4jLoggerAdapterTest {

    @Test
    void loggerFactoryCreatesLog4jAdapterAndEmitsFormattedEvents() {
        try (TestLoggerContext context = TestLoggerContext.create("slf4j-log4j12-adapter-standard")) {
            LocationAwareLogger logger = context.logger();
            IllegalStateException failure = new IllegalStateException("boom");

            logger.trace("trace {}", "message");
            logger.info("info {} {}", new Object[]{"first", "second"});
            logger.error("error message", failure);

            List<LoggingEvent> events = context.events();

            assertThat(logger.getName()).isEqualTo("slf4j-log4j12-adapter-standard");
            assertThat(events).hasSize(3);
            assertThat(events).extracting(LoggingEvent::getLevel)
                    .containsExactly(Level.TRACE, Level.INFO, Level.ERROR);
            assertThat(events).extracting(LoggingEvent::getRenderedMessage)
                    .containsExactly("trace message", "info first second", "error message");
            assertThat(events).extracting(LoggingEvent::getLoggerName)
                    .containsOnly("slf4j-log4j12-adapter-standard");
            assertThat(events.get(2).getThrowableInformation().getThrowable()).isSameAs(failure);
        }
    }

    @Test
    void locationAwareLoggingUsesSuppliedCallerBoundary() {
        try (TestLoggerContext context = TestLoggerContext.create("slf4j-log4j12-adapter-location-aware")) {
            LocationAwareLogger logger = context.logger();
            IllegalArgumentException failure = new IllegalArgumentException("warn");

            LocationAwareInvoker.logWarning(logger, "warn message", failure);

            assertThat(context.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getRenderedMessage()).isEqualTo("warn message");
                assertThat(event.getThrowableInformation().getThrowable()).isSameAs(failure);
            });
        }
    }

    private static final class TestLoggerContext implements AutoCloseable {

        private final Logger log4jLogger;
        private final Level previousLevel;
        private final boolean previousAdditivity;
        private final List<Appender> previousAppenders;
        private final RecordingAppender appender;
        private final LocationAwareLogger logger;

        private TestLoggerContext(String loggerName) {
            this.log4jLogger = Logger.getLogger(loggerName);
            this.previousLevel = this.log4jLogger.getLevel();
            this.previousAdditivity = this.log4jLogger.getAdditivity();
            this.previousAppenders = appenders(this.log4jLogger);
            this.appender = new RecordingAppender();

            this.log4jLogger.removeAllAppenders();
            this.log4jLogger.setAdditivity(false);
            this.log4jLogger.setLevel(Level.TRACE);
            this.log4jLogger.addAppender(this.appender);
            this.logger = (LocationAwareLogger) LoggerFactory.getLogger(loggerName);
        }

        private static TestLoggerContext create(String loggerName) {
            return new TestLoggerContext(loggerName);
        }

        private static List<Appender> appenders(Logger logger) {
            List<Appender> appenders = new ArrayList<>();
            Enumeration<?> existingAppenders = logger.getAllAppenders();
            while (existingAppenders.hasMoreElements()) {
                appenders.add((Appender) existingAppenders.nextElement());
            }
            return appenders;
        }

        private LocationAwareLogger logger() {
            return this.logger;
        }

        private List<LoggingEvent> events() {
            return List.copyOf(this.appender.events);
        }

        @Override
        public void close() {
            this.log4jLogger.removeAppender(this.appender);
            this.log4jLogger.removeAllAppenders();
            for (Appender previousAppender : this.previousAppenders) {
                this.log4jLogger.addAppender(previousAppender);
            }
            this.log4jLogger.setLevel(this.previousLevel);
            this.log4jLogger.setAdditivity(this.previousAdditivity);
        }
    }

    private static final class RecordingAppender extends AppenderSkeleton {

        private final List<LoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(LoggingEvent event) {
            this.events.add(event);
        }

        @Override
        public void close() {
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }

    private static final class LocationAwareInvoker {

        private static void logWarning(LocationAwareLogger logger, String message, Throwable throwable) {
            logger.log(null, LocationAwareInvoker.class.getName(), LocationAwareLogger.WARN_INT, message, null,
                    throwable);
        }
    }
}
