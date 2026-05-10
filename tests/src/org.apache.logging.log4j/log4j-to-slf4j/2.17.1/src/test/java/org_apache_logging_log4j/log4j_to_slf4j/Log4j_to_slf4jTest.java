/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_to_slf4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.LoggerNameAwareMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.util.StringMap;
import org.apache.logging.slf4j.MDCContextMap;
import org.apache.logging.slf4j.SLF4JLogger;
import org.apache.logging.slf4j.SLF4JLoggerContext;
import org.apache.logging.slf4j.SLF4JLoggerContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.spi.LocationAwareLogger;

public class Log4j_to_slf4jTest {
    private static final String FQCN = Log4j_to_slf4jTest.class.getName();

    @AfterEach
    void clearThreadContext() {
        ThreadContext.clearAll();
        MDC.clear();
    }

    @Test
    void logManagerUsesSlf4jProviderAndCachesLoggersByMessageFactory() {
        assertThat(LogManager.getFactory()).isInstanceOf(SLF4JLoggerContextFactory.class);

        LoggerContext context = LogManager.getContext(false);
        assertThat(context).isInstanceOf(SLF4JLoggerContext.class);
        assertThat(context.getExternalContext()).isNull();

        String loggerName = "bridge.context.default";
        assertThat(context.hasLogger(loggerName)).isFalse();
        ExtendedLogger logger = context.getLogger(loggerName);

        assertThat(logger).isInstanceOf(SLF4JLogger.class);
        assertThat(logger.getName()).isEqualTo(loggerName);
        assertThat(context.hasLogger(loggerName)).isTrue();
        assertThat(context.getLogger(loggerName)).isSameAs(logger);

        String factoryLoggerName = "bridge.context.parameterized";
        assertThat(context.hasLogger(factoryLoggerName, ParameterizedMessageFactory.INSTANCE)).isFalse();
        ExtendedLogger factoryLogger = context.getLogger(factoryLoggerName, ParameterizedMessageFactory.INSTANCE);

        assertThat(factoryLogger).isInstanceOf(SLF4JLogger.class);
        assertThat((Object) factoryLogger.getMessageFactory()).isSameAs(ParameterizedMessageFactory.INSTANCE);
        assertThat(context.hasLogger(factoryLoggerName, ParameterizedMessageFactory.INSTANCE)).isTrue();
        assertThat(context.hasLogger(factoryLoggerName, ParameterizedMessageFactory.class)).isTrue();
        assertThat(context.getLogger(factoryLoggerName, ParameterizedMessageFactory.INSTANCE)).isSameAs(factoryLogger);
    }

    @Test
    void loggerReportsEffectiveLevelFromUnderlyingSlf4jLogger() {
        RecordingLogger delegate = new RecordingLogger("effective.level");
        SLF4JLogger logger = new SLF4JLogger(delegate.getName(), delegate);

        delegate.enableOnly(Slf4jLevel.WARN, Slf4jLevel.ERROR);
        assertThat(logger.getLevel()).isEqualTo(Level.WARN);
        assertThat(logger.isEnabled(Level.WARN, null, "enabled")).isTrue();
        assertThat(logger.isEnabled(Level.INFO, null, "disabled")).isFalse();

        delegate.enableOnly(Slf4jLevel.TRACE, Slf4jLevel.DEBUG, Slf4jLevel.INFO, Slf4jLevel.WARN, Slf4jLevel.ERROR);
        assertThat(logger.getLevel()).isEqualTo(Level.TRACE);
        assertThat(logger.isEnabled(Level.DEBUG, null, (Message) new SimpleMessage("message"), null)).isTrue();
        assertThat(logger.isEnabled(Level.INFO, null, new StringBuilder("chars"), null)).isTrue();
        assertThat(logger.isEnabled(Level.ERROR, null, Integer.valueOf(7), null)).isTrue();
        assertThat(logger.isEnabled(Level.DEBUG, null, "one {}", "argument")).isTrue();
        assertThat(logger.isEnabled(Level.DEBUG, null, "two {} {}", "first", "second")).isTrue();
        assertThat(logger.isEnabled(Level.DEBUG, null, "varargs {}", new Object[] { "value" })).isTrue();

        delegate.enableOnly();
        assertThat(logger.getLevel()).isEqualTo(Level.OFF);
    }

    @Test
    void loggerDispatchesLog4jLevelsToPlainSlf4jLogger() {
        RecordingLogger delegate = new RecordingLogger("plain.dispatch");
        SLF4JLogger logger = new SLF4JLogger(delegate.getName(), delegate);
        RuntimeException error = new RuntimeException("boom");
        Marker marker = MarkerManager.getMarker("PLAIN_MARKER");

        logger.logMessage(FQCN, Level.TRACE, marker, new SimpleMessage("trace message"), null);
        logger.logMessage(FQCN, Level.DEBUG, marker, new SimpleMessage("debug message"), null);
        logger.logMessage(FQCN, Level.INFO, marker, new SimpleMessage("info message"), null);
        logger.logMessage(FQCN, Level.WARN, marker, new SimpleMessage("warn message"), null);
        logger.logMessage(FQCN, Level.ERROR, marker, new SimpleMessage("error message"), error);
        logger.logMessage(FQCN, Level.FATAL, marker, new SimpleMessage("fatal message"), error);

        assertThat(delegate.events).extracting(event -> event.level).containsExactly(
                Slf4jLevel.TRACE,
                Slf4jLevel.DEBUG,
                Slf4jLevel.INFO,
                Slf4jLevel.WARN,
                Slf4jLevel.ERROR,
                Slf4jLevel.ERROR);
        assertThat(delegate.events).extracting(event -> event.message).containsExactly(
                "trace message",
                "debug message",
                "info message",
                "warn message",
                "error message",
                "fatal message");
        assertThat(delegate.events.get(4).throwable).isSameAs(error);
        assertThat(delegate.events.get(5).throwable).isSameAs(error);
    }

    @Test
    void loggerFormatsLog4jMessagesBeforeForwardingToSlf4j() {
        RecordingLogger delegate = new RecordingLogger("formatted.messages");
        SLF4JLogger logger = new SLF4JLogger(delegate.getName(), ParameterizedMessageFactory.INSTANCE, delegate);
        Message message = ParameterizedMessageFactory.INSTANCE.newMessage(
                "Order {} for {} contains {} item(s)", "A-42", "alice", Integer.valueOf(3));

        logger.logMessage(FQCN, Level.INFO, null, message, null);

        assertThat(delegate.events).hasSize(1);
        LogEvent event = delegate.events.get(0);
        assertThat(event.level).isEqualTo(Slf4jLevel.INFO);
        assertThat(event.message).isEqualTo("Order A-42 for alice contains 3 item(s)");
        assertThat(event.throwable).isNull();
    }

    @Test
    void locationAwareLoggerReceivesFqcnMarkerLevelAndLoggerNameAwareMessage() {
        RecordingLocationAwareLogger delegate = new RecordingLocationAwareLogger("location.dispatch");
        SLF4JLogger logger = new SLF4JLogger(delegate.getName(), delegate);
        RuntimeException error = new RuntimeException("location-aware");
        LoggerNameAwareSimpleMessage message = new LoggerNameAwareSimpleMessage("location message");

        logger.logMessage(FQCN, Level.INFO, MarkerManager.getMarker("LOCATION_MARKER"), message, error);

        assertThat(delegate.locationEvents).hasSize(1);
        LocationEvent event = delegate.locationEvents.get(0);
        assertThat(event.fqcn).isEqualTo(FQCN);
        assertThat(event.level).isEqualTo(LocationAwareLogger.INFO_INT);
        assertThat(event.message).isEqualTo("location message");
        assertThat(event.marker.getName()).isEqualTo("LOCATION_MARKER");
        assertThat(event.throwable).isSameAs(error);
        assertThat(event.arguments).isNull();
        assertThat(message.getLoggerName()).isEqualTo("location.dispatch");
        assertThat(logger.getLogger()).isSameAs(delegate);
    }

    @Test
    void markerHierarchyIsConvertedForEnablementChecksAndPlainLogging() {
        RecordingMarkerLogger delegate = new RecordingMarkerLogger("marker.dispatch");
        SLF4JLogger logger = new SLF4JLogger(delegate.getName(), delegate);
        Marker parent = MarkerManager.getMarker("BRIDGE_PARENT_MARKER");
        Marker child = MarkerManager.getMarker("BRIDGE_CHILD_MARKER").addParents(parent);
        RuntimeException error = new RuntimeException("marked");

        assertThat(logger.isEnabled(Level.INFO, child, "marker enabled")).isTrue();

        org.slf4j.Marker enabledMarker = delegate.enabledMarker;
        assertThat(enabledMarker.getName()).isEqualTo("BRIDGE_CHILD_MARKER");
        assertThat(enabledMarker.contains(MarkerFactory.getMarker("BRIDGE_PARENT_MARKER"))).isTrue();

        logger.logMessage(FQCN, Level.INFO, child, new SimpleMessage("marked message"), error);

        assertThat(delegate.markerEvents).hasSize(1);
        MarkerEvent event = delegate.markerEvents.get(0);
        assertThat(event.marker.getName()).isEqualTo("BRIDGE_CHILD_MARKER");
        assertThat(event.marker.contains(MarkerFactory.getMarker("BRIDGE_PARENT_MARKER"))).isTrue();
        assertThat(event.message).isEqualTo("marked message");
        assertThat(event.throwable).isSameAs(error);
    }

    @Test
    void mdcContextMapAdaptsLog4jThreadContextOperationsToSlf4jMdc() {
        ThreadContext.put("requestId", "r-1");
        assertThat(MDC.get("requestId")).isEqualTo("r-1");
        assertThat(ThreadContext.containsKey("requestId")).isTrue();

        Map<String, String> additionalContext = new LinkedHashMap<>();
        additionalContext.put("tenant", "acme");
        additionalContext.put("span", "s-2");
        ThreadContext.putAll(additionalContext);

        assertThat(MDC.getCopyOfContextMap()).containsEntry("requestId", "r-1")
                .containsEntry("tenant", "acme")
                .containsEntry("span", "s-2");
        assertThat(ThreadContext.getImmutableContext()).containsEntry("tenant", "acme");

        ThreadContext.removeAll(Arrays.asList("requestId", "span"));
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("span")).isNull();
        assertThat(MDC.get("tenant")).isEqualTo("acme");
    }

    @Test
    void mdcContextMapExposesCopiesAndReadOnlyStringMap() {
        MDCContextMap contextMap = new MDCContextMap();
        contextMap.clear();
        contextMap.put("alpha", "one");
        contextMap.put("beta", "two");

        assertThat(contextMap.isEmpty()).isFalse();
        assertThat(contextMap.containsKey("alpha")).isTrue();
        assertThat(contextMap.get("beta")).isEqualTo("two");
        assertThat(contextMap.getCopy()).containsEntry("alpha", "one").containsEntry("beta", "two");
        assertThat(contextMap.getImmutableMapOrNull()).containsEntry("alpha", "one");

        StringMap readOnlyContextData = contextMap.getReadOnlyContextData();
        assertThat((Object) readOnlyContextData.getValue("alpha")).isEqualTo("one");
        assertThat((Object) readOnlyContextData.getValue("beta")).isEqualTo("two");

        contextMap.remove("alpha");
        assertThat(contextMap.containsKey("alpha")).isFalse();
        contextMap.removeAll(Arrays.asList("beta"));
        assertThat(contextMap.isEmpty()).isTrue();
    }

    private enum Slf4jLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static class RecordingLogger extends MarkerIgnoringBase {
        private final List<LogEvent> events = new ArrayList<>();
        private EnumSet<Slf4jLevel> enabledLevels = EnumSet.allOf(Slf4jLevel.class);

        RecordingLogger(String name) {
            this.name = name;
        }

        final void enableOnly(Slf4jLevel... levels) {
            enabledLevels = levels.length == 0
                    ? EnumSet.noneOf(Slf4jLevel.class)
                    : EnumSet.copyOf(Arrays.asList(levels));
        }

        @Override
        public boolean isTraceEnabled() {
            return enabledLevels.contains(Slf4jLevel.TRACE);
        }

        @Override
        public void trace(String message) {
            record(Slf4jLevel.TRACE, message, null);
        }

        @Override
        public void trace(String format, Object argument) {
            record(Slf4jLevel.TRACE, format, null);
        }

        @Override
        public void trace(String format, Object firstArgument, Object secondArgument) {
            record(Slf4jLevel.TRACE, format, null);
        }

        @Override
        public void trace(String format, Object... arguments) {
            record(Slf4jLevel.TRACE, format, null);
        }

        @Override
        public void trace(String message, Throwable throwable) {
            record(Slf4jLevel.TRACE, message, throwable);
        }

        @Override
        public boolean isDebugEnabled() {
            return enabledLevels.contains(Slf4jLevel.DEBUG);
        }

        @Override
        public void debug(String message) {
            record(Slf4jLevel.DEBUG, message, null);
        }

        @Override
        public void debug(String format, Object argument) {
            record(Slf4jLevel.DEBUG, format, null);
        }

        @Override
        public void debug(String format, Object firstArgument, Object secondArgument) {
            record(Slf4jLevel.DEBUG, format, null);
        }

        @Override
        public void debug(String format, Object... arguments) {
            record(Slf4jLevel.DEBUG, format, null);
        }

        @Override
        public void debug(String message, Throwable throwable) {
            record(Slf4jLevel.DEBUG, message, throwable);
        }

        @Override
        public boolean isInfoEnabled() {
            return enabledLevels.contains(Slf4jLevel.INFO);
        }

        @Override
        public void info(String message) {
            record(Slf4jLevel.INFO, message, null);
        }

        @Override
        public void info(String format, Object argument) {
            record(Slf4jLevel.INFO, format, null);
        }

        @Override
        public void info(String format, Object firstArgument, Object secondArgument) {
            record(Slf4jLevel.INFO, format, null);
        }

        @Override
        public void info(String format, Object... arguments) {
            record(Slf4jLevel.INFO, format, null);
        }

        @Override
        public void info(String message, Throwable throwable) {
            record(Slf4jLevel.INFO, message, throwable);
        }

        @Override
        public boolean isWarnEnabled() {
            return enabledLevels.contains(Slf4jLevel.WARN);
        }

        @Override
        public void warn(String message) {
            record(Slf4jLevel.WARN, message, null);
        }

        @Override
        public void warn(String format, Object argument) {
            record(Slf4jLevel.WARN, format, null);
        }

        @Override
        public void warn(String format, Object firstArgument, Object secondArgument) {
            record(Slf4jLevel.WARN, format, null);
        }

        @Override
        public void warn(String format, Object... arguments) {
            record(Slf4jLevel.WARN, format, null);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            record(Slf4jLevel.WARN, message, throwable);
        }

        @Override
        public boolean isErrorEnabled() {
            return enabledLevels.contains(Slf4jLevel.ERROR);
        }

        @Override
        public void error(String message) {
            record(Slf4jLevel.ERROR, message, null);
        }

        @Override
        public void error(String format, Object argument) {
            record(Slf4jLevel.ERROR, format, null);
        }

        @Override
        public void error(String format, Object firstArgument, Object secondArgument) {
            record(Slf4jLevel.ERROR, format, null);
        }

        @Override
        public void error(String format, Object... arguments) {
            record(Slf4jLevel.ERROR, format, null);
        }

        @Override
        public void error(String message, Throwable throwable) {
            record(Slf4jLevel.ERROR, message, throwable);
        }

        private void record(Slf4jLevel level, String message, Throwable throwable) {
            events.add(new LogEvent(level, message, throwable));
        }
    }

    private static final class RecordingLocationAwareLogger extends RecordingLogger implements LocationAwareLogger {
        private final List<LocationEvent> locationEvents = new ArrayList<>();

        private RecordingLocationAwareLogger(String name) {
            super(name);
        }

        @Override
        public void log(org.slf4j.Marker marker, String fqcn, int level, String message, Object[] arguments,
                Throwable throwable) {
            locationEvents.add(new LocationEvent(marker, fqcn, level, message, arguments, throwable));
        }
    }

    private static final class RecordingMarkerLogger extends RecordingLogger {
        private final List<MarkerEvent> markerEvents = new ArrayList<>();
        private org.slf4j.Marker enabledMarker;

        private RecordingMarkerLogger(String name) {
            super(name);
        }

        @Override
        public boolean isInfoEnabled(org.slf4j.Marker marker) {
            enabledMarker = marker;
            return isInfoEnabled();
        }

        @Override
        public void info(org.slf4j.Marker marker, String message, Throwable throwable) {
            markerEvents.add(new MarkerEvent(marker, message, throwable));
        }
    }

    private static final class LogEvent {
        private final Slf4jLevel level;
        private final String message;
        private final Throwable throwable;

        private LogEvent(Slf4jLevel level, String message, Throwable throwable) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }
    }

    private static final class LocationEvent {
        private final org.slf4j.Marker marker;
        private final String fqcn;
        private final int level;
        private final String message;
        private final Object[] arguments;
        private final Throwable throwable;

        private LocationEvent(org.slf4j.Marker marker, String fqcn, int level, String message, Object[] arguments,
                Throwable throwable) {
            this.marker = marker;
            this.fqcn = fqcn;
            this.level = level;
            this.message = message;
            this.arguments = arguments;
            this.throwable = throwable;
        }
    }

    private static final class MarkerEvent {
        private final org.slf4j.Marker marker;
        private final String message;
        private final Throwable throwable;

        private MarkerEvent(org.slf4j.Marker marker, String message, Throwable throwable) {
            this.marker = marker;
            this.message = message;
            this.throwable = throwable;
        }
    }

    private static final class LoggerNameAwareSimpleMessage extends SimpleMessage implements LoggerNameAwareMessage {
        private String loggerName;

        private LoggerNameAwareSimpleMessage(String message) {
            super(message);
        }

        @Override
        public void setLoggerName(String loggerName) {
            this.loggerName = loggerName;
        }

        @Override
        public String getLoggerName() {
            return loggerName;
        }
    }
}
