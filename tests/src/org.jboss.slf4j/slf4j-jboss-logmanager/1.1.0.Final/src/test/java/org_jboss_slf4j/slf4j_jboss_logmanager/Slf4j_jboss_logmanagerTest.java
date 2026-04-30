/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_slf4j.slf4j_jboss_logmanager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.impl.Slf4jLogger;
import org.slf4j.impl.Slf4jLoggerFactory;
import org.slf4j.impl.Slf4jMDCAdapter;
import org.slf4j.impl.StaticLoggerBinder;
import org.slf4j.impl.StaticMarkerBinder;
import org.slf4j.impl.StaticMDCBinder;
import org.slf4j.spi.LocationAwareLogger;
import org.slf4j.spi.MDCAdapter;

import static org.assertj.core.api.Assertions.assertThat;

public class Slf4j_jboss_logmanagerTest {

    @Test
    void loggerFactoryBindsSlf4jLoggersToJbossLogManagerLoggers() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".factory";

        StaticLoggerBinder binder = StaticLoggerBinder.getSingleton();
        ILoggerFactory factory = binder.getLoggerFactory();
        Logger first = factory.getLogger(loggerName);
        Logger second = LoggerFactory.getLogger(loggerName);
        Logger third = new Slf4jLoggerFactory().getLogger(loggerName);

        assertThat(binder).isSameAs(StaticLoggerBinder.SINGLETON);
        assertThat(StaticLoggerBinder.REQUESTED_API_VERSION).startsWith("1.7");
        assertThat(binder.getLoggerFactoryClassStr()).isEqualTo(Slf4jLoggerFactory.class.getName());
        assertThat(factory).isInstanceOf(Slf4jLoggerFactory.class);
        assertThat(first).isInstanceOf(Slf4jLogger.class);
        assertThat(first.getName()).isEqualTo(loggerName);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        assertThat(LogContext.getLogContext().getLogger(loggerName).getName()).isEqualTo(loggerName);
    }

    @Test
    void loggerLevelChecksAndFormattingOverloadsReachJbossLogManager() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".levels";
        org.jboss.logmanager.Logger jbossLogger = LogContext.getLogContext().getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        try (LoggerScope scope = new LoggerScope(jbossLogger, handler, org.jboss.logmanager.Level.INFO)) {
            Logger logger = LoggerFactory.getLogger(loggerName);

            assertThat(logger.isTraceEnabled()).isFalse();
            assertThat(logger.isDebugEnabled()).isFalse();
            assertThat(logger.isInfoEnabled()).isTrue();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();

            logger.debug("debug message should be filtered");
            assertThat(handler.records()).isEmpty();

            logger.info("user {} logged in from {}", "alice", "127.0.0.1");
            logger.warn("payment {} failed", "p-42", new IllegalStateException("declined"));
            logger.error("plain error", new RuntimeException("boom"));

            assertThat(handler.records()).hasSize(3);
            assertThat(handler.record(0).getLevel()).isEqualTo(org.jboss.logmanager.Level.INFO);
            assertThat(handler.record(0).getMessage()).isEqualTo("user alice logged in from 127.0.0.1");
            assertThat(handler.record(0).getParameters()).containsExactly("alice", "127.0.0.1");

            assertThat(handler.record(1).getLevel()).isEqualTo(org.jboss.logmanager.Level.WARN);
            assertThat(handler.record(1).getMessage()).isEqualTo("payment p-42 failed");
            assertThat(handler.record(1).getThrown()).isInstanceOf(IllegalStateException.class)
                    .hasMessage("declined");

            assertThat(handler.record(2).getLevel()).isEqualTo(org.jboss.logmanager.Level.ERROR);
            assertThat(handler.record(2).getMessage()).isEqualTo("plain error");
            assertThat(handler.record(2).getThrown()).isInstanceOf(RuntimeException.class)
                    .hasMessage("boom");
        }
    }

    @Test
    void allSlf4jLevelsMapToJbossLogManagerLevels() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".allLevels";
        org.jboss.logmanager.Logger jbossLogger = LogContext.getLogContext().getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        try (LoggerScope scope = new LoggerScope(jbossLogger, handler, org.jboss.logmanager.Level.TRACE)) {
            Logger logger = LoggerFactory.getLogger(loggerName);

            assertThat(logger.isTraceEnabled()).isTrue();
            assertThat(logger.isDebugEnabled()).isTrue();
            assertThat(logger.isInfoEnabled()).isTrue();
            assertThat(logger.isWarnEnabled()).isTrue();
            assertThat(logger.isErrorEnabled()).isTrue();

            logger.trace("trace {}", 1);
            logger.debug("debug {}", 2);
            logger.info("info {}", 3);
            logger.warn("warn {}", 4);
            logger.error("error {}", 5);

            assertThat(handler.records()).extracting(LogRecord::getLevel).containsExactly(
                    org.jboss.logmanager.Level.TRACE,
                    org.jboss.logmanager.Level.DEBUG,
                    org.jboss.logmanager.Level.INFO,
                    org.jboss.logmanager.Level.WARN,
                    org.jboss.logmanager.Level.ERROR
            );
            assertThat(handler.records()).extracting(LogRecord::getMessage).containsExactly(
                    "trace 1", "debug 2", "info 3", "warn 4", "error 5"
            );
        }
    }

    @Test
    void locationAwareLoggerUsesExplicitLevelAndLoggerClassName() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".locationAware";
        org.jboss.logmanager.Logger jbossLogger = LogContext.getLogContext().getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        try (LoggerScope scope = new LoggerScope(jbossLogger, handler, org.jboss.logmanager.Level.TRACE)) {
            Logger logger = LoggerFactory.getLogger(loggerName);
            Marker marker = MarkerFactory.getMarker("ignored-marker");
            IllegalArgumentException failure = new IllegalArgumentException("bad input");

            assertThat(logger).isInstanceOf(LocationAwareLogger.class);
            ((LocationAwareLogger) logger).log(
                    marker,
                    Slf4j_jboss_logmanagerTest.class.getName(),
                    LocationAwareLogger.ERROR_INT,
                    "location aware {}",
                    new Object[] { "message" },
                    failure
            );
            ((LocationAwareLogger) logger).log(
                    marker,
                    Slf4j_jboss_logmanagerTest.class.getName(),
                    12345,
                    "unknown level falls back to {}",
                    new Object[] { "debug" },
                    null
            );

            assertThat(handler.records()).hasSize(2);
            ExtLogRecord errorRecord = handler.extRecord(0);
            assertThat(errorRecord.getLevel()).isEqualTo(org.jboss.logmanager.Level.ERROR);
            assertThat(errorRecord.getLoggerClassName()).isEqualTo(Slf4j_jboss_logmanagerTest.class.getName());
            assertThat(errorRecord.getMessage()).isEqualTo("location aware message");
            assertThat(errorRecord.getThrown()).isSameAs(failure);
            assertThat(errorRecord.getParameters()).containsExactly("message");

            ExtLogRecord fallbackRecord = handler.extRecord(1);
            assertThat(fallbackRecord.getLevel()).isEqualTo(org.jboss.logmanager.Level.DEBUG);
            assertThat(fallbackRecord.getMessage()).isEqualTo("unknown level falls back to debug");
        }
    }

    @Test
    void slf4jMdcAdapterSharesContextWithJbossLogManagerMdc() {
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        try {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();

            MDC.put("requestId", "req-1");
            org.jboss.logmanager.MDC.put("tenant", "blue");

            assertThat(MDC.getMDCAdapter()).isInstanceOf(Slf4jMDCAdapter.class);
            assertThat(MDC.get("requestId")).isEqualTo("req-1");
            assertThat(MDC.get("tenant")).isEqualTo("blue");
            assertThat(org.jboss.logmanager.MDC.get("requestId")).isEqualTo("req-1");

            Map<String, String> copiedContext = MDC.getCopyOfContextMap();
            assertThat(copiedContext).containsEntry("requestId", "req-1")
                    .containsEntry("tenant", "blue");
            copiedContext.put("copyOnly", "value");
            assertThat(MDC.get("copyOnly")).isNull();

            Map<String, String> replacementContext = new LinkedHashMap<>();
            replacementContext.put("user", "carol");
            replacementContext.put("nullValue", null);
            replacementContext.put(null, "nullKey");
            MDC.setContextMap(replacementContext);

            assertThat(MDC.get("requestId")).isNull();
            assertThat(MDC.get("tenant")).isNull();
            assertThat(MDC.get("user")).isEqualTo("carol");
            assertThat(MDC.get("nullValue")).isNull();
            assertThat(MDC.getCopyOfContextMap()).containsOnly(Map.entry("user", "carol"));

            MDC.remove("user");
            assertThat(org.jboss.logmanager.MDC.get("user")).isNull();
            MDC.clear();
            assertThat(MDC.getCopyOfContextMap()).isEmpty();
        } finally {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            }
        }
    }

    @Test
    void staticMdcBinderCreatesSlf4jMdcAdapters() {
        StaticMDCBinder binder = StaticMDCBinder.SINGLETON;
        MDCAdapter first = binder.getMDCA();
        MDCAdapter second = binder.getMDCA();

        assertThat(first).isInstanceOf(Slf4jMDCAdapter.class);
        assertThat(second).isInstanceOf(Slf4jMDCAdapter.class);
        assertThat(first).isNotSameAs(second);
        assertThat(binder.getMDCAdapterClassStr()).isEqualTo(Slf4jMDCAdapter.class.getName());
    }

    @Test
    void loggedRecordsCaptureSlf4jMdcContext() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".mdcRecords";
        org.jboss.logmanager.Logger jbossLogger = LogContext.getLogContext().getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        try {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
            try (LoggerScope scope = new LoggerScope(jbossLogger, handler, org.jboss.logmanager.Level.INFO)) {
                Logger logger = LoggerFactory.getLogger(loggerName);

                MDC.put("requestId", "req-42");
                MDC.put("tenant", "green");
                logger.info("processing {}", "invoice");

                assertThat(handler.records()).hasSize(1);
                ExtLogRecord firstRecord = handler.extRecord(0);
                firstRecord.copyMdc();
                assertThat(firstRecord.getMdc("requestId")).isEqualTo("req-42");
                assertThat(firstRecord.getMdc("tenant")).isEqualTo("green");
                assertThat(firstRecord.getMdcCopy()).containsOnly(
                        Map.entry("requestId", "req-42"),
                        Map.entry("tenant", "green")
                );

                MDC.put("requestId", "req-43");
                MDC.remove("tenant");
                logger.info("processed invoice");

                assertThat(handler.records()).hasSize(2);
                ExtLogRecord secondRecord = handler.extRecord(1);
                secondRecord.copyMdc();
                assertThat(secondRecord.getMdc("requestId")).isEqualTo("req-43");
                assertThat(secondRecord.getMdc("tenant")).isNull();
                assertThat(secondRecord.getMdcCopy()).containsOnly(Map.entry("requestId", "req-43"));
            }
        } finally {
            MDC.clear();
            org.jboss.logmanager.MDC.clear();
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            }
        }
    }

    @Test
    void markerLoggingOverloadsIgnoreMarkersAndReachJbossLogManager() {
        String loggerName = Slf4j_jboss_logmanagerTest.class.getName() + ".markerLogging";
        org.jboss.logmanager.Logger jbossLogger = LogContext.getLogContext().getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        try (LoggerScope scope = new LoggerScope(jbossLogger, handler, org.jboss.logmanager.Level.INFO)) {
            Logger logger = LoggerFactory.getLogger(loggerName);
            Marker marker = MarkerFactory.getMarker("audit-event");
            RuntimeException failure = new RuntimeException("marker failure");

            assertThat(logger.isDebugEnabled(marker)).isFalse();
            assertThat(logger.isInfoEnabled(marker)).isTrue();
            assertThat(logger.isWarnEnabled(marker)).isTrue();
            assertThat(logger.isErrorEnabled(marker)).isTrue();

            logger.debug(marker, "filtered marker debug");
            logger.info(marker, "marker {} {}", "order", 7);
            logger.warn(marker, "marker warning", failure);
            logger.error(marker, "marker {} failed", "payment", failure);

            assertThat(handler.records()).hasSize(3);
            assertThat(handler.record(0).getLevel()).isEqualTo(org.jboss.logmanager.Level.INFO);
            assertThat(handler.record(0).getMessage()).isEqualTo("marker order 7");
            assertThat(handler.record(0).getParameters()).containsExactly("order", 7);

            assertThat(handler.record(1).getLevel()).isEqualTo(org.jboss.logmanager.Level.WARN);
            assertThat(handler.record(1).getMessage()).isEqualTo("marker warning");
            assertThat(handler.record(1).getThrown()).isSameAs(failure);

            assertThat(handler.record(2).getLevel()).isEqualTo(org.jboss.logmanager.Level.ERROR);
            assertThat(handler.record(2).getMessage()).isEqualTo("marker payment failed");
            assertThat(handler.record(2).getThrown()).isSameAs(failure);
        }
    }

    @Test
    void markerBinderProvidesBasicMarkerFactory() {
        StaticMarkerBinder binder = StaticMarkerBinder.SINGLETON;
        IMarkerFactory markerFactory = binder.getMarkerFactory();
        Marker parent = markerFactory.getMarker("parent");
        Marker child = MarkerFactory.getDetachedMarker("child");

        parent.add(child);

        assertThat(markerFactory).isInstanceOf(BasicMarkerFactory.class);
        assertThat(binder.getMarkerFactoryClassStr()).isEqualTo(BasicMarkerFactory.class.getName());
        assertThat(markerFactory.getMarker("parent")).isSameAs(parent);
        assertThat(parent.contains(child)).isTrue();
        assertThat(parent.contains("child")).isTrue();
        assertThat(parent.remove(child)).isTrue();
        assertThat(parent.contains(child)).isFalse();
    }

    private static final class LoggerScope implements AutoCloseable {
        private final org.jboss.logmanager.Logger logger;
        private final Level previousLevel;
        private final boolean previousUseParentHandlers;
        private final Handler[] previousHandlers;

        private LoggerScope(
                final org.jboss.logmanager.Logger logger,
                final CapturingHandler handler,
                final Level level
        ) {
            this.logger = logger;
            previousLevel = logger.getLevel();
            previousUseParentHandlers = logger.getUseParentHandlers();
            previousHandlers = logger.getHandlers();
            logger.clearHandlers();
            logger.setUseParentHandlers(false);
            logger.setLevel(level);
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
        }

        @Override
        public void close() {
            for (Handler handler : logger.clearHandlers()) {
                handler.close();
            }
            for (Handler handler : previousHandlers) {
                logger.addHandler(handler);
            }
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        private List<LogRecord> records() {
            return records;
        }

        private LogRecord record(final int index) {
            return records.get(index);
        }

        private ExtLogRecord extRecord(final int index) {
            return ExtLogRecord.wrap(record(index));
        }

        @Override
        public void publish(final LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            records.clear();
        }
    }
}
