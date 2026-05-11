/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_slf4j_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.slf4j.Log4jLoggerFactory;
import org.apache.logging.slf4j.Log4jMDCAdapter;
import org.apache.logging.slf4j.Log4jMarkerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.impl.StaticLoggerBinder;
import org.slf4j.spi.LocationAwareLogger;

public class Log4j_slf4j_implTest {

    @AfterEach
    void clearThreadLocalLoggingState() {
        MDC.clear();
        ThreadContext.clearAll();
    }

    @Test
    void exposesLog4jBackedSlf4jServices() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();

        assertThat(loggerFactory).isInstanceOf(Log4jLoggerFactory.class);
        assertThat(StaticLoggerBinder.getSingleton().getLoggerFactory()).isSameAs(loggerFactory);
        assertThat(StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr())
                .isEqualTo(Log4jLoggerFactory.class.getName());
        assertThat(MarkerFactory.getIMarkerFactory()).isInstanceOf(Log4jMarkerFactory.class);
        assertThat(MDC.getMDCAdapter()).isInstanceOf(Log4jMDCAdapter.class);
    }

    @Test
    void routesFormattedSlf4jMessagesMarkersMdcAndThrowablesToLog4j() {
        try (CapturedLogger capturedLogger = CapturedLogger.create(
                "routesFormattedSlf4jMessagesMarkersMdcAndThrowablesToLog4j",
                Level.TRACE,
                "%level|%logger|%marker|%X{requestId}|%message|%throwable{short.message}%n")) {
            Logger logger = LoggerFactory.getLogger(capturedLogger.getLoggerName());
            Marker marker = MarkerFactory.getMarker("AUDIT");

            try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", "request-42")) {
                logger.info(marker, "processed {} records for {}", 7, "alice");
                logger.error(marker, "operation failed", new IllegalStateException("database unavailable"));
            }

            String output = capturedLogger.getOutput();
            assertThat(output).contains("INFO|" + capturedLogger.getLoggerName()
                    + "|AUDIT|request-42|processed 7 records for alice|");
            assertThat(output).contains("ERROR|" + capturedLogger.getLoggerName()
                    + "|AUDIT|request-42|operation failed|");
            assertThat(output).contains("database unavailable");
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void honorsLog4jLevelConfigurationForSlf4jLevelChecksAndLogging() {
        try (CapturedLogger capturedLogger = CapturedLogger.create(
                "honorsLog4jLevelConfigurationForSlf4jLevelChecksAndLogging",
                Level.WARN,
                "%level|%message%n")) {
            Logger logger = LoggerFactory.getLogger(capturedLogger.getLoggerName());
            Marker marker = MarkerFactory.getMarker("SECURITY");

            assertThat(logger.isTraceEnabled()).isFalse();
            assertThat(logger.isDebugEnabled()).isFalse();
            assertThat(logger.isInfoEnabled()).isFalse();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();
            assertThat(logger.isInfoEnabled(marker)).isFalse();
            assertThat(logger.isWarnEnabled(marker)).isTrue();

            logger.trace("suppressed trace");
            logger.debug("suppressed debug");
            logger.info(marker, "suppressed info");
            logger.warn("visible warning");
            logger.error("visible error");

            assertThat(capturedLogger.getOutput())
                    .doesNotContain("suppressed")
                    .contains("WARN|visible warning")
                    .contains("ERROR|visible error");
        }
    }

    @Test
    void routesSlf4jMarkerHierarchyToLog4jMarkerFilters() {
        try (CapturedLogger capturedLogger = CapturedLogger.create(
                "routesSlf4jMarkerHierarchyToLog4jMarkerFilters",
                Level.TRACE,
                "%message%n",
                MarkerFilter.createFilter("SECURITY", Filter.Result.ACCEPT, Filter.Result.DENY))) {
            Logger logger = LoggerFactory.getLogger(capturedLogger.getLoggerName());
            Marker securityMarker = MarkerFactory.getDetachedMarker("SECURITY");
            Marker loginMarker = MarkerFactory.getDetachedMarker("LOGIN");
            loginMarker.add(securityMarker);

            logger.info(loginMarker, "accepted child marker");
            logger.info(securityMarker, "accepted parent marker");
            logger.info(MarkerFactory.getDetachedMarker("AUDIT"), "rejected unrelated marker");
            logger.info("rejected unmarked message");

            assertThat(capturedLogger.getOutput())
                    .contains("accepted child marker")
                    .contains("accepted parent marker")
                    .doesNotContain("rejected unrelated marker")
                    .doesNotContain("rejected unmarked message");
        }
    }

    @Test
    void supportsSlf4jLocationAwareLoggerCalls() {
        try (CapturedLogger capturedLogger = CapturedLogger.create(
                "supportsSlf4jLocationAwareLoggerCalls",
                Level.TRACE,
                "%level|%marker|%message%n")) {
            Logger logger = LoggerFactory.getLogger(capturedLogger.getLoggerName());
            Marker marker = MarkerFactory.getMarker("LOCATION");

            assertThat(logger).isInstanceOf(LocationAwareLogger.class);
            LocationAwareLogger locationAwareLogger = (LocationAwareLogger) logger;
            locationAwareLogger.log(
                    marker,
                    Log4j_slf4j_implTest.class.getName(),
                    LocationAwareLogger.INFO_INT,
                    "location {} message",
                    new Object[] {"aware"},
                    null);

            assertThat(capturedLogger.getOutput()).contains("INFO|LOCATION|location aware message");
        }
    }

    @Test
    void synchronizesSlf4jMdcOperationsWithLog4jThreadContext() {
        Map<String, String> contextMap = new HashMap<>();
        contextMap.put("tenant", "acme");
        contextMap.put("traceId", "trace-123");

        MDC.setContextMap(contextMap);

        assertThat(MDC.get("tenant")).isEqualTo("acme");
        assertThat(ThreadContext.get("tenant")).isEqualTo("acme");
        assertThat(MDC.getCopyOfContextMap())
                .containsEntry("tenant", "acme")
                .containsEntry("traceId", "trace-123");

        MDC.put("requestId", "request-7");
        assertThat(ThreadContext.getImmutableContext()).containsEntry("requestId", "request-7");

        MDC.remove("tenant");
        assertThat(MDC.get("tenant")).isNull();
        assertThat(ThreadContext.get("tenant")).isNull();

        MDC.clear();
        assertThat(ThreadContext.getImmutableContext()).isEmpty();
    }

    private static final class CapturedLogger implements AutoCloseable {

        private final LoggerContext context;
        private final Configuration configuration;
        private final String loggerName;
        private final StringWriter writer;
        private final WriterAppender appender;

        private CapturedLogger(
                LoggerContext context,
                Configuration configuration,
                String loggerName,
                StringWriter writer,
                WriterAppender appender) {
            this.context = context;
            this.configuration = configuration;
            this.loggerName = loggerName;
            this.writer = writer;
            this.appender = appender;
        }

        static CapturedLogger create(String testName, Level level, String pattern) {
            return create(testName, level, pattern, null);
        }

        static CapturedLogger create(String testName, Level level, String pattern, Filter filter) {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            String loggerName = Log4j_slf4j_implTest.class.getName() + "." + testName;
            StringWriter writer = new StringWriter();
            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern(pattern)
                    .withConfiguration(configuration)
                    .build();
            WriterAppender appender = WriterAppender.newBuilder()
                    .setName(testName + "Appender")
                    .setTarget(writer)
                    .setLayout(layout)
                    .setIgnoreExceptions(false)
                    .build();
            appender.start();

            LoggerConfig loggerConfig = new LoggerConfig(loggerName, level, false);
            loggerConfig.addAppender(appender, level, filter);
            configuration.addLogger(loggerName, loggerConfig);
            context.updateLoggers();

            return new CapturedLogger(context, configuration, loggerName, writer, appender);
        }

        String getLoggerName() {
            return loggerName;
        }

        String getOutput() {
            return writer.toString();
        }

        @Override
        public void close() {
            configuration.removeLogger(loggerName);
            context.updateLoggers();
            appender.stop();
        }
    }
}
