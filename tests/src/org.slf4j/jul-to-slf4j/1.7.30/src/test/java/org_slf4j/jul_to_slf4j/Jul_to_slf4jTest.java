/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jul_to_slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.ERROR;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

import java.util.ArrayList;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.SubstituteLogger;
import org.slf4j.spi.LocationAwareLogger;

public class Jul_to_slf4jTest {
    private java.util.logging.Logger rootLogger;
    private Handler[] originalHandlers;

    @BeforeEach
    void rememberRootLoggerState() {
        rootLogger = LogManager.getLogManager().getLogger("");
        originalHandlers = rootLogger.getHandlers();
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
    }

    @AfterEach
    void restoreRootLoggerState() {
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        for (Handler handler : originalHandlers) {
            rootLogger.addHandler(handler);
        }
    }

    @Test
    void installAddsABridgeHandlerAndUninstallRemovesOnlyBridgeHandlers() {
        Handler existingHandler = noOpJulHandler();
        rootLogger.addHandler(existingHandler);

        assertThat(SLF4JBridgeHandler.isInstalled()).isFalse();

        SLF4JBridgeHandler.install();

        assertThat(SLF4JBridgeHandler.isInstalled()).isTrue();
        assertThat(rootLogger.getHandlers()).contains(existingHandler);
        assertThat(rootLogger.getHandlers())
                .anySatisfy(handler -> assertThat(handler).isInstanceOf(SLF4JBridgeHandler.class));

        SLF4JBridgeHandler.uninstall();

        assertThat(SLF4JBridgeHandler.isInstalled()).isFalse();
        assertThat(rootLogger.getHandlers()).containsExactly(existingHandler);
    }

    @Test
    void removeHandlersForRootLoggerRemovesEveryInstalledJulHandler() {
        Handler firstHandler = noOpJulHandler();
        Handler secondHandler = noOpJulHandler();
        rootLogger.addHandler(firstHandler);
        rootLogger.addHandler(new SLF4JBridgeHandler());
        rootLogger.addHandler(secondHandler);

        SLF4JBridgeHandler.removeHandlersForRootLogger();

        assertThat(rootLogger.getHandlers()).isEmpty();
        assertThat(SLF4JBridgeHandler.isInstalled()).isFalse();
    }

    @Test
    void publishMapsJulLevelsAndMessageFormattingToPlainSlf4jLogger() {
        ConcurrentLinkedQueue<SubstituteLoggingEvent> capturedEvents = new ConcurrentLinkedQueue<>();
        SubstituteLogger logger = new SubstituteLogger("plain.slf4j.logger", capturedEvents, false);
        FixedLoggerBridgeHandler handler = new FixedLoggerBridgeHandler(logger);
        IllegalStateException failure = new IllegalStateException("plain logger failure");

        handler.publish(logRecord(Level.FINEST, "trace message", failure));
        handler.publish(logRecord(Level.FINE, "debug message", failure));
        handler.publish(localizedLogRecord(Level.INFO, "localized", failure, "orders", 3));
        handler.publish(logRecord(Level.WARNING, "warn message", failure));
        handler.publish(logRecord(Level.SEVERE, "error message", failure));
        handler.publish(null);

        List<SubstituteLoggingEvent> events = new ArrayList<>(capturedEvents);
        assertThat(events).extracting(SubstituteLoggingEvent::getLoggerName)
                .containsExactly("plain.slf4j.logger", "plain.slf4j.logger", "plain.slf4j.logger", "plain.slf4j.logger",
                        "plain.slf4j.logger");
        assertThat(events).extracting(SubstituteLoggingEvent::getLevel)
                .containsExactly(TRACE, DEBUG, INFO, WARN, ERROR);
        assertThat(events).extracting(SubstituteLoggingEvent::getMessage)
                .containsExactly(
                        "trace message", "debug message", "processed 3 orders", "warn message", "error message");
        assertThat(events).extracting(SubstituteLoggingEvent::getThrowable)
                .containsExactly(failure, failure, failure, failure, failure);
    }

    @Test
    void publishUsesLocationAwareLoggerWhenAvailable() {
        CapturingLocationAwareLogger logger = new CapturingLocationAwareLogger("location.aware.logger");
        FixedLoggerBridgeHandler handler = new FixedLoggerBridgeHandler(logger);
        IllegalArgumentException failure = new IllegalArgumentException("location aware failure");

        handler.publish(logRecord(Level.FINEST, "trace location", failure));
        handler.publish(logRecord(Level.FINE, "debug location", failure));
        handler.publish(logRecord(Level.INFO, "info location", failure));
        handler.publish(logRecord(Level.WARNING, "warn location", failure));
        handler.publish(logRecord(Level.SEVERE, "error location", failure));

        assertThat(logger.events).extracting(LocationAwareEvent::level).containsExactly(LocationAwareLogger.TRACE_INT,
                LocationAwareLogger.DEBUG_INT, LocationAwareLogger.INFO_INT, LocationAwareLogger.WARN_INT,
                LocationAwareLogger.ERROR_INT);
        assertThat(logger.events).extracting(LocationAwareEvent::message)
                .containsExactly(
                        "trace location", "debug location", "info location", "warn location", "error location");
        assertThat(logger.events).extracting(LocationAwareEvent::fqcn)
                .containsOnly(java.util.logging.Logger.class.getName());
        assertThat(logger.events).extracting(LocationAwareEvent::throwable).containsOnly(failure);
    }

    private static LogRecord logRecord(Level level, String message, Throwable throwable) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName("jul.source.logger");
        record.setThrown(throwable);
        return record;
    }

    private static LogRecord localizedLogRecord(
            Level level, String messageKey, Throwable throwable, Object... parameters) {
        LogRecord record = logRecord(level, messageKey, throwable);
        record.setResourceBundle(localizationBundle());
        record.setParameters(parameters);
        return record;
    }

    private static ResourceBundle localizationBundle() {
        return new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] { { "localized", "processed {1} {0}" } };
            }
        };
    }

    private static Handler noOpJulHandler() {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    private static final class FixedLoggerBridgeHandler extends SLF4JBridgeHandler {
        private final Logger logger;

        private FixedLoggerBridgeHandler(Logger logger) {
            this.logger = logger;
        }

        @Override
        protected Logger getSLF4JLogger(LogRecord record) {
            return logger;
        }
    }

    private static final class CapturingLocationAwareLogger extends SubstituteLogger implements LocationAwareLogger {
        private final List<LocationAwareEvent> events = new ArrayList<>();

        private CapturingLocationAwareLogger(String name) {
            super(name, new ConcurrentLinkedQueue<>(), false);
        }

        @Override
        public void log(Marker marker, String fqcn, int level, String message, Object[] argArray, Throwable throwable) {
            events.add(new LocationAwareEvent(fqcn, level, message, throwable));
        }
    }

    private static final class LocationAwareEvent {
        private final String fqcn;
        private final int level;
        private final String message;
        private final Throwable throwable;

        private LocationAwareEvent(String fqcn, int level, String message, Throwable throwable) {
            this.fqcn = fqcn;
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }

        private String fqcn() {
            return fqcn;
        }

        private int level() {
            return level;
        }

        private String message() {
            return message;
        }

        private Throwable throwable() {
            return throwable;
        }
    }
}
