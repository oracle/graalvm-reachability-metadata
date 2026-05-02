/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_slf4j.slf4j_jboss_logmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.LocationAwareLogger;

public class Slf4j_jboss_logmanagerTest {
    private static final String LOGGER_PREFIX = Slf4j_jboss_logmanagerTest.class.getName();

    @Test
    void loggerFactoryCreatesCachedJbossLogManagerBackedLoggers() {
        String loggerName = LOGGER_PREFIX + ".factory";
        CapturingHandler handler = new CapturingHandler();
        LoggerState state = installCapturingHandler(loggerName, handler);
        try {
            assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                    .isEqualTo("org.slf4j.impl.Slf4jLoggerFactory");

            org.slf4j.Logger firstLogger = LoggerFactory.getLogger(loggerName);
            org.slf4j.Logger secondLogger = LoggerFactory.getLogger(loggerName);

            assertThat(secondLogger).isSameAs(firstLogger);
            assertThat(firstLogger.getName()).isEqualTo(loggerName);
            assertThat(firstLogger.isTraceEnabled()).isTrue();
            assertThat(firstLogger.isDebugEnabled()).isTrue();
            assertThat(firstLogger.isInfoEnabled()).isTrue();
            assertThat(firstLogger.isWarnEnabled()).isTrue();
            assertThat(firstLogger.isErrorEnabled()).isTrue();

            IllegalStateException failure = new IllegalStateException("boom");
            firstLogger.trace("trace message");
            firstLogger.debug("debug {} {}", "alpha", 7);
            firstLogger.info("info {}", "bravo");
            firstLogger.warn("warn {}", "charlie");
            firstLogger.error("error {}", "delta", failure);

            assertThat(handler.records()).extracting(LogRecord::getLevel)
                    .containsExactly(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
            assertThat(handler.records()).extracting(LogRecord::getMessage)
                    .containsExactly("trace message", "debug alpha 7", "info bravo", "warn charlie", "error delta");

            ExtLogRecord debugRecord = handler.records().get(1);
            assertThat(debugRecord.getLoggerName()).isEqualTo(loggerName);
            assertThat(debugRecord.getLoggerClassName()).isEqualTo("org.slf4j.impl.Slf4jLogger");
            assertThat(debugRecord.getFormattedMessage()).isEqualTo("debug alpha 7");
            assertThat(debugRecord.getParameters()).containsExactly("alpha", 7);

            ExtLogRecord errorRecord = handler.records().get(4);
            assertThat(errorRecord.getThrown()).isSameAs(failure);
            assertThat(errorRecord.getParameters()).containsExactly("delta", failure);
        } finally {
            state.restore();
        }
    }

    @Test
    void disabledLevelsAreNotPublishedToJbossLogManager() {
        String loggerName = LOGGER_PREFIX + ".levels";
        CapturingHandler handler = new CapturingHandler();
        LoggerState state = installCapturingHandler(loggerName, handler);
        try {
            state.logger().setLevel(Level.WARN);
            org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);

            assertThat(logger.isTraceEnabled()).isFalse();
            assertThat(logger.isDebugEnabled()).isFalse();
            assertThat(logger.isInfoEnabled()).isFalse();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();

            logger.trace("hidden trace");
            logger.debug("hidden debug");
            logger.info("hidden info");
            logger.warn("visible warn");
            logger.error("visible error");

            assertThat(handler.records()).extracting(LogRecord::getLevel).containsExactly(Level.WARN, Level.ERROR);
            assertThat(handler.records()).extracting(LogRecord::getMessage)
                    .containsExactly("visible warn", "visible error");
        } finally {
            state.restore();
        }
    }

    @Test
    void locationAwareLoggerUsesProvidedCallerNameAndSlf4jFormatting() {
        String loggerName = LOGGER_PREFIX + ".locationAware";
        CapturingHandler handler = new CapturingHandler();
        LoggerState state = installCapturingHandler(loggerName, handler);
        try {
            org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);
            assertThat(logger).isInstanceOf(LocationAwareLogger.class);

            Marker marker = MarkerFactory.getMarker(LOGGER_PREFIX + ".marker.locationAware");
            RuntimeException thrown = new RuntimeException("location-aware failure");
            ((LocationAwareLogger) logger).log(
                    marker,
                    "com.example.Caller",
                    LocationAwareLogger.INFO_INT,
                    "processed {} records for {}",
                    new Object[] {3, "tenant-a"},
                    thrown);

            assertThat(handler.records()).hasSize(1);
            ExtLogRecord record = handler.records().get(0);
            assertThat(record.getLevel()).isEqualTo(Level.INFO);
            assertThat(record.getMessage()).isEqualTo("processed 3 records for tenant-a");
            assertThat(record.getLoggerClassName()).isEqualTo("com.example.Caller");
            assertThat(record.getThrown()).isSameAs(thrown);
            assertThat(record.getParameters()).containsExactly(3, "tenant-a");
        } finally {
            state.restore();
        }
    }

    @Test
    void mdcOperationsAreBackedByJbossLogManagerMdc() {
        MDC.clear();
        org.jboss.logmanager.MDC.clear();
        try {
            MDC.put("requestId", "req-1");
            MDC.put("tenant", "acme");

            assertThat(MDC.get("requestId")).isEqualTo("req-1");
            assertThat(org.jboss.logmanager.MDC.get("requestId")).isEqualTo("req-1");
            assertThat(MDC.getCopyOfContextMap()).containsOnly(entry("requestId", "req-1"), entry("tenant", "acme"));

            Map<String, String> copy = MDC.getCopyOfContextMap();
            copy.put("requestId", "mutated");
            assertThat(MDC.get("requestId")).isEqualTo("req-1");

            MDC.setContextMap(Map.of("session", "s-1", "user", "alice"));
            assertThat(MDC.get("requestId")).isNull();
            assertThat(org.jboss.logmanager.MDC.copy()).containsOnly(entry("session", "s-1"), entry("user", "alice"));

            MDC.remove("session");
            assertThat(org.jboss.logmanager.MDC.get("session")).isNull();
            assertThat(MDC.getCopyOfContextMap()).containsOnly(entry("user", "alice"));
        } finally {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
        }
    }

    @Test
    void logRecordsIncludeSlf4jMdcContext() {
        MDC.clear();
        org.jboss.logmanager.MDC.clear();
        String loggerName = LOGGER_PREFIX + ".mdcRecord";
        CapturingHandler handler = new CapturingHandler();
        LoggerState state = installCapturingHandler(loggerName, handler);
        try {
            MDC.put("requestId", "req-2");
            MDC.put("tenant", "acme");

            org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);
            logger.info("processing request");

            assertThat(handler.records()).hasSize(1);
            ExtLogRecord record = handler.records().get(0);
            assertThat(record.getMdc("requestId")).isEqualTo("req-2");
            assertThat(record.getMdcCopy()).containsOnly(entry("requestId", "req-2"), entry("tenant", "acme"));

            MDC.put("requestId", "req-3");
            logger.info("processing next request");

            assertThat(handler.records()).hasSize(2);
            ExtLogRecord nextRecord = handler.records().get(1);
            assertThat(nextRecord.getMdc("requestId")).isEqualTo("req-3");
            assertThat(nextRecord.getMdcCopy()).containsOnly(entry("requestId", "req-3"), entry("tenant", "acme"));
        } finally {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
            state.restore();
        }
    }

    @Test
    void mdcCloseableScopesValuesAndRemovesThemWhenClosed() {
        MDC.clear();
        org.jboss.logmanager.MDC.clear();
        String loggerName = LOGGER_PREFIX + ".mdcCloseable";
        CapturingHandler handler = new CapturingHandler();
        LoggerState state = installCapturingHandler(loggerName, handler);
        try {
            org.slf4j.Logger logger = LoggerFactory.getLogger(loggerName);

            try (MDC.MDCCloseable closeable = MDC.putCloseable("operation", "import")) {
                assertThat(closeable).isNotNull();
                assertThat(MDC.get("operation")).isEqualTo("import");
                assertThat(org.jboss.logmanager.MDC.get("operation")).isEqualTo("import");

                logger.info("inside scoped operation");

                assertThat(handler.records()).hasSize(1);
                ExtLogRecord scopedRecord = handler.records().get(0);
                assertThat(scopedRecord.getMdc("operation")).isEqualTo("import");
                assertThat(scopedRecord.getMdcCopy()).containsOnly(entry("operation", "import"));
            }

            assertThat(MDC.get("operation")).isNull();
            assertThat(org.jboss.logmanager.MDC.get("operation")).isNull();

            logger.info("outside scoped operation");

            assertThat(handler.records()).hasSize(2);
            ExtLogRecord unscopedRecord = handler.records().get(1);
            assertThat(unscopedRecord.getMdc("operation")).isNull();
            assertThat(unscopedRecord.getMdcCopy()).doesNotContainKey("operation");
        } finally {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
            state.restore();
        }
    }

    @Test
    void markerFactoryProvidesBasicNamedAndDetachedMarkers() {
        String markerPrefix = LOGGER_PREFIX + ".marker.";
        Marker parent = MarkerFactory.getMarker(markerPrefix + "parent");
        Marker sameParent = MarkerFactory.getMarker(markerPrefix + "parent");
        Marker child = MarkerFactory.getDetachedMarker(markerPrefix + "child");

        parent.add(child);

        assertThat(sameParent).isSameAs(parent);
        assertThat(parent.getName()).isEqualTo(markerPrefix + "parent");
        assertThat(child.getName()).isEqualTo(markerPrefix + "child");
        assertThat(parent.hasReferences()).isTrue();
        assertThat(parent.contains(child)).isTrue();
        assertThat(parent.contains(markerPrefix + "child")).isTrue();
        assertThatThrownBy(() -> parent.contains((String) null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static LoggerState installCapturingHandler(String loggerName, CapturingHandler handler) {
        Logger logger = LogContext.getLogContext().getLogger(loggerName);
        LoggerState state = new LoggerState(
                logger,
                logger.getLevel(),
                logger.getUseParentHandlers(),
                logger.getHandlers());
        logger.clearHandlers();
        logger.setUseParentHandlers(false);
        logger.setLevel(java.util.logging.Level.ALL);
        handler.setLevel(java.util.logging.Level.ALL);
        logger.addHandler(handler);
        return state;
    }

    private static final class LoggerState {
        private final Logger logger;
        private final java.util.logging.Level level;
        private final boolean useParentHandlers;
        private final Handler[] handlers;

        private LoggerState(
                Logger logger,
                java.util.logging.Level level,
                boolean useParentHandlers,
                Handler[] handlers) {
            this.logger = logger;
            this.level = level;
            this.useParentHandlers = useParentHandlers;
            this.handlers = handlers;
        }

        private Logger logger() {
            return logger;
        }

        private void restore() {
            for (Handler handler : logger.clearHandlers()) {
                handler.close();
            }
            logger.setHandlers(handlers);
            logger.setLevel(level);
            logger.setUseParentHandlers(useParentHandlers);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<ExtLogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(ExtLogRecord.wrap(record));
            }
        }

        @Override
        public void flush() {
            // Records are kept in memory and do not require flushing.
        }

        @Override
        public void close() {
            records.clear();
        }

        private List<ExtLogRecord> records() {
            return records;
        }
    }
}
