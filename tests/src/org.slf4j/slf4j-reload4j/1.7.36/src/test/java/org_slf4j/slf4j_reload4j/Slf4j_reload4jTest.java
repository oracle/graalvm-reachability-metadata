/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_reload4j;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.LocationAwareLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class Slf4j_reload4jTest {

    @AfterEach
    void clearDiagnosticContext() {
        MDC.clear();
    }

    @Test
    void loggerFactoryCreatesReload4jBackedLoggersWithStableNames() {
        String loggerName = uniqueLoggerName("lookup");

        Logger first = LoggerFactory.getLogger(loggerName);
        Logger second = LoggerFactory.getLogger(loggerName);
        Logger classLogger = LoggerFactory.getLogger(Slf4j_reload4jTest.class);

        assertThat(first).isSameAs(second);
        assertThat(first.getName()).isEqualTo(loggerName);
        assertThat(classLogger.getName()).isEqualTo(Slf4j_reload4jTest.class.getName());
        assertThat(first).isInstanceOf(LocationAwareLogger.class);
    }

    @Test
    void slf4jLoggingMethodsCreateReload4jEventsWithFormattedMessagesAndThrowables() {
        try (LoggerContext context = LoggerContext.create(uniqueLoggerName("events"), Level.ALL)) {
            Logger logger = LoggerFactory.getLogger(context.name());
            Marker auditMarker = MarkerFactory.getMarker("AUDIT");
            IllegalStateException failure = new IllegalStateException("order failed");

            logger.trace("trace {}", "message");
            logger.debug("debug {} {}", "message", 7);
            logger.info("processed {} orders for {}", 3, "alice");
            logger.warn(auditMarker, "marker call for {}", "login");
            logger.error("failed to process {}", "order-7", failure);

            List<LoggingEvent> events = context.events();
            assertThat(events).hasSize(5);
            assertThat(events).extracting(LoggingEvent::getLoggerName).containsOnly(context.name());
            assertThat(events).extracting(LoggingEvent::getLevel)
                    .containsExactly(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
            assertThat(events).extracting(LoggingEvent::getRenderedMessage)
                    .containsExactly(
                            "trace message",
                            "debug message 7",
                            "processed 3 orders for alice",
                            "marker call for login",
                            "failed to process order-7");
            assertThat(events.get(4).getThrowableInformation().getThrowable()).isSameAs(failure);
        }
    }

    @Test
    void enabledChecksAndFilteringFollowReload4jLoggerLevel() {
        try (LoggerContext context = LoggerContext.create(uniqueLoggerName("levels"), Level.WARN)) {
            Logger logger = LoggerFactory.getLogger(context.name());

            assertThat(logger.isTraceEnabled()).isFalse();
            assertThat(logger.isDebugEnabled()).isFalse();
            assertThat(logger.isInfoEnabled()).isFalse();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();

            logger.trace("ignored trace");
            logger.debug("ignored debug");
            logger.info("ignored info");
            logger.warn("visible warning");
            logger.error("visible error");

            assertThat(context.events()).extracting(LoggingEvent::getLevel)
                    .containsExactly(Level.WARN, Level.ERROR);
            assertThat(context.events()).extracting(LoggingEvent::getRenderedMessage)
                    .containsExactly("visible warning", "visible error");
        }
    }

    @Test
    void mdcValuesAreStoredThroughSlf4jAndRenderedByReload4jLayouts() {
        StringWriter writer = new StringWriter();
        String loggerName = uniqueLoggerName("mdc");
        PatternLayout layout = new PatternLayout("%p %c [%X{requestId}/%X{user}] - %m%n");
        WriterAppender appender = new WriterAppender(layout, writer);
        appender.setName("capturing-writer");

        try (LoggerContext context = LoggerContext.create(loggerName, Level.INFO, appender)) {
            Logger logger = LoggerFactory.getLogger(context.name());

            MDC.put("requestId", "req-123");
            MDC.put("user", "bob");
            logger.info("created {}", "invoice");
            MDC.remove("user");
            logger.info("closed {}", "invoice");

            assertThat(MDC.get("requestId")).isEqualTo("req-123");
        }

        assertThat(writer.toString())
                .contains("INFO " + loggerName + " [req-123/bob] - created invoice")
                .contains("INFO " + loggerName + " [req-123/] - closed invoice");
    }

    @Test
    void locationAwareLoggerApiLogsThroughReload4jAdapter() {
        try (LoggerContext context = LoggerContext.create(uniqueLoggerName("location-aware"), Level.ALL)) {
            Logger logger = LoggerFactory.getLogger(context.name());
            LocationAwareLogger locationAwareLogger = (LocationAwareLogger) logger;
            IllegalArgumentException failure = new IllegalArgumentException("location aware failure");

            LocationAwareInvoker.log(locationAwareLogger, failure);

            assertThat(context.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getLoggerName()).isEqualTo(context.name());
                assertThat(event.getRenderedMessage()).isEqualTo("location aware message");
                assertThat(event.getThrowableInformation().getThrowable()).isSameAs(failure);
            });
        }
    }

    @Test
    void rootLoggerNameRoutesSlf4jCallsToReload4jRootLogger() {
        try (RootLoggerContext context = RootLoggerContext.create(Level.INFO)) {
            Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

            assertThat(rootLogger.isDebugEnabled()).isFalse();
            assertThat(rootLogger.isInfoEnabled()).isTrue();

            rootLogger.debug("hidden root debug");
            rootLogger.info("root event {}", 1);

            assertThat(context.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getLoggerName()).isEqualTo(context.name());
                assertThat(event.getRenderedMessage()).isEqualTo("root event 1");
            });
        }
    }

    private static String uniqueLoggerName(String suffix) {
        return Slf4j_reload4jTest.class.getName() + "." + suffix + "." + System.nanoTime();
    }

    private static final class LocationAwareInvoker {

        private static void log(LocationAwareLogger logger, Throwable throwable) {
            logger.log(
                    MarkerFactory.getMarker("LOCATION_AWARE"),
                    LocationAwareInvoker.class.getName(),
                    LocationAwareLogger.INFO_INT,
                    "location aware message",
                    null,
                    throwable);
        }
    }

    private static final class RootLoggerContext implements AutoCloseable {

        private final org.apache.log4j.Logger rootLogger;
        private final Level previousLevel;
        private final List<Appender> previousAppenders;
        private final RecordingAppender recordingAppender;

        private RootLoggerContext(Level level) {
            this.rootLogger = LogManager.getRootLogger();
            this.previousLevel = this.rootLogger.getLevel();
            this.previousAppenders = new ArrayList<>();
            Enumeration<?> appenders = this.rootLogger.getAllAppenders();
            while (appenders.hasMoreElements()) {
                this.previousAppenders.add((Appender) appenders.nextElement());
            }
            this.recordingAppender = new RecordingAppender();

            this.rootLogger.removeAllAppenders();
            this.rootLogger.setLevel(level);
            this.rootLogger.addAppender(this.recordingAppender);
        }

        private static RootLoggerContext create(Level level) {
            return new RootLoggerContext(level);
        }

        private String name() {
            return this.rootLogger.getName();
        }

        private List<LoggingEvent> events() {
            return this.recordingAppender.events();
        }

        @Override
        public void close() {
            this.rootLogger.removeAllAppenders();
            this.recordingAppender.close();
            this.rootLogger.setLevel(this.previousLevel);
            for (Appender appender : this.previousAppenders) {
                this.rootLogger.addAppender(appender);
            }
        }
    }

    private static final class LoggerContext implements AutoCloseable {

        private final org.apache.log4j.Logger reload4jLogger;
        private final Level previousLevel;
        private final boolean previousAdditivity;
        private final RecordingAppender recordingAppender;
        private final List<Appender> appenders;

        private LoggerContext(String loggerName, Level level, List<Appender> appenders) {
            this.reload4jLogger = LogManager.getLogger(loggerName);
            this.previousLevel = this.reload4jLogger.getLevel();
            this.previousAdditivity = this.reload4jLogger.getAdditivity();
            this.recordingAppender = new RecordingAppender();
            this.appenders = appenders;

            this.reload4jLogger.removeAllAppenders();
            this.reload4jLogger.setAdditivity(false);
            this.reload4jLogger.setLevel(level);
            this.reload4jLogger.addAppender(this.recordingAppender);
            for (Appender appender : appenders) {
                this.reload4jLogger.addAppender(appender);
            }
        }

        private static LoggerContext create(String loggerName, Level level) {
            return new LoggerContext(loggerName, level, List.of());
        }

        private static LoggerContext create(String loggerName, Level level, Appender appender) {
            return new LoggerContext(loggerName, level, List.of(appender));
        }

        private String name() {
            return this.reload4jLogger.getName();
        }

        private List<LoggingEvent> events() {
            return this.recordingAppender.events();
        }

        @Override
        public void close() {
            this.reload4jLogger.removeAllAppenders();
            for (Appender appender : this.appenders) {
                appender.close();
            }
            this.reload4jLogger.setLevel(this.previousLevel);
            this.reload4jLogger.setAdditivity(this.previousAdditivity);
        }
    }

    private static final class RecordingAppender extends AppenderSkeleton {

        private final List<LoggingEvent> events = new ArrayList<>();

        private List<LoggingEvent> events() {
            return List.copyOf(this.events);
        }

        @Override
        protected void append(LoggingEvent event) {
            this.events.add(event);
        }

        @Override
        public void close() {
            this.events.clear();
        }

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
