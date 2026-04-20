/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Slf4j_apiTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void loggerFactoryProvidesStableNamedAndClassLoggers() {
        ILoggerFactory firstFactory = LoggerFactory.getILoggerFactory();
        ILoggerFactory secondFactory = LoggerFactory.getILoggerFactory();
        Logger namedLogger = LoggerFactory.getLogger("structured-logger");
        Logger sameNamedLogger = LoggerFactory.getLogger("structured-logger");
        Logger classLogger = LoggerFactory.getLogger(Slf4j_apiTest.class);
        Marker marker = MarkerFactory.getMarker("AUDIT");

        assertThat(firstFactory).isSameAs(secondFactory);
        assertThat(namedLogger).isSameAs(sameNamedLogger);
        assertThat(namedLogger.getName()).isEqualTo("structured-logger");
        assertThat(classLogger.getName()).isEqualTo(Slf4j_apiTest.class.getName());

        namedLogger.trace("trace {}", "value");
        namedLogger.debug("debug {} {}", "left", "right");
        namedLogger.info("info {} {}", new Object[]{"first", "second"});
        namedLogger.warn("warn message");
        namedLogger.error(marker, "error {}", "value");
    }

    @Test
    void markerIgnoringBaseDelegatesMarkerOverloadsToConcreteLoggerMethods() {
        RecordingLogger logger = new RecordingLogger("delegating-logger");
        Marker marker = MarkerFactory.getMarker("SECURITY");
        IllegalStateException failure = new IllegalStateException("boom");

        assertThat(logger.isTraceEnabled(marker)).isTrue();
        assertThat(logger.isDebugEnabled(marker)).isTrue();
        assertThat(logger.isInfoEnabled(marker)).isTrue();
        assertThat(logger.isWarnEnabled(marker)).isTrue();
        assertThat(logger.isErrorEnabled(marker)).isTrue();

        logger.trace(marker, "trace {}", "value");
        logger.debug(marker, "debug {} {}", "left", "right");
        logger.info(marker, "info {} {}", new Object[]{"first", "second"});
        logger.warn(marker, "warn message", failure);
        logger.error(marker, "error {}", "value");

        List<LogEntry> entries = logger.getEntries();

        assertThat(entries).hasSize(5);
        assertThat(entries.get(0).level).isEqualTo("TRACE");
        assertThat(entries.get(0).formattedMessage).isEqualTo("trace value");
        assertThat(entries.get(1).level).isEqualTo("DEBUG");
        assertThat(entries.get(1).formattedMessage).isEqualTo("debug left right");
        assertThat(entries.get(2).level).isEqualTo("INFO");
        assertThat(entries.get(2).formattedMessage).isEqualTo("info first second");
        assertThat(entries.get(3).level).isEqualTo("WARN");
        assertThat(entries.get(3).formattedMessage).isEqualTo("warn message");
        assertThat(entries.get(3).throwable).isSameAs(failure);
        assertThat(entries.get(4).level).isEqualTo("ERROR");
        assertThat(entries.get(4).formattedMessage).isEqualTo("error value");
        assertThat(logger.toString()).contains("delegating-logger");
    }

    @Test
    void markerFactorySupportsManagedAndDetachedMarkers() {
        IMarkerFactory markerFactory = MarkerFactory.getIMarkerFactory();
        Marker managedMarker = MarkerFactory.getMarker("HTTP");
        Marker sameManagedMarker = MarkerFactory.getMarker("HTTP");
        Marker detachedMarker = MarkerFactory.getDetachedMarker("DETACHED");
        Marker childMarker = MarkerFactory.getDetachedMarker("CHILD");

        managedMarker.add(childMarker);

        List<String> childNames = new ArrayList<>();
        for (Iterator<?> iterator = managedMarker.iterator(); iterator.hasNext();) {
            Marker nextMarker = (Marker) iterator.next();
            childNames.add(nextMarker.getName());
        }

        assertThat(managedMarker).isSameAs(sameManagedMarker);
        assertThat(detachedMarker.getName()).isEqualTo("DETACHED");
        assertThat(markerFactory.exists("HTTP")).isTrue();
        assertThat(markerFactory.exists("DETACHED")).isFalse();
        assertThat(managedMarker.contains(childMarker)).isTrue();
        assertThat(managedMarker.contains("CHILD")).isTrue();
        assertThat(childNames).containsExactly("CHILD");
        assertThat(managedMarker.remove(childMarker)).isTrue();
        assertThat(managedMarker.contains("CHILD")).isFalse();
        assertThat(markerFactory.detachMarker("HTTP")).isTrue();
        assertThat(markerFactory.exists("HTTP")).isFalse();
    }

    @Test
    void markersSupportRecursiveContainmentEqualityAndCyclePrevention() {
        Marker parentMarker = MarkerFactory.getDetachedMarker("PARENT");
        Marker childMarker = MarkerFactory.getDetachedMarker("CHILD");
        Marker sameNamedChildMarker = MarkerFactory.getDetachedMarker("CHILD");
        Marker grandchildMarker = MarkerFactory.getDetachedMarker("GRANDCHILD");

        childMarker.add(grandchildMarker);
        parentMarker.add(childMarker);
        parentMarker.add(sameNamedChildMarker);
        grandchildMarker.add(parentMarker);

        List<String> directChildNames = new ArrayList<>();
        for (Iterator<?> iterator = parentMarker.iterator(); iterator.hasNext();) {
            Marker nextMarker = (Marker) iterator.next();
            directChildNames.add(nextMarker.getName());
        }

        assertThat(parentMarker.hasChildren()).isTrue();
        assertThat(childMarker.hasChildren()).isTrue();
        assertThat(parentMarker.contains(grandchildMarker)).isTrue();
        assertThat(parentMarker.contains("GRANDCHILD")).isTrue();
        assertThat(childMarker).isEqualTo(sameNamedChildMarker);
        assertThat(childMarker.hashCode()).isEqualTo(sameNamedChildMarker.hashCode());
        assertThat(directChildNames).containsExactly("CHILD");
        assertThat(grandchildMarker.contains(parentMarker)).isFalse();
    }

    @Test
    void mdcAcceptsContextOperationsAndRejectsNullKeys() {
        Map<String, String> replacementContext = new LinkedHashMap<>();

        MDC.put("requestId", "42");
        MDC.put("route", "/health");

        replacementContext.put("requestId", "84");
        replacementContext.put("user", "tester");
        MDC.setContextMap(replacementContext);
        MDC.remove("user");
        MDC.clear();

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNull();
        assertThatThrownBy(() -> MDC.put(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key parameter cannot be null");
        assertThatThrownBy(() -> MDC.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key parameter cannot be null");
        assertThatThrownBy(() -> MDC.remove(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key parameter cannot be null");
    }

    @Test
    void messageFormatterHandlesArraysEscapingAndNullValues() {
        String singleArgument = MessageFormatter.format("Hello {}", "world");
        String twoArguments = MessageFormatter.format("{} {}", "alpha", "beta");
        String escapedDelimiter = MessageFormatter.arrayFormat("Escaped \\{} and {}", new Object[]{"value"});
        String nullArgument = MessageFormatter.arrayFormat("Null {}", new Object[]{null});
        String primitiveArrayArgument = MessageFormatter.arrayFormat("Array {}", new Object[]{new int[]{1, 2}});

        assertThat(singleArgument).isEqualTo("Hello world");
        assertThat(twoArguments).isEqualTo("alpha beta");
        assertThat(escapedDelimiter).isEqualTo("Escaped {} and value");
        assertThat(nullArgument).isEqualTo("Null null");
        assertThat(primitiveArrayArgument).isEqualTo("Array [1, 2]");
    }

    @Test
    void messageFormatterRecursivelyFormatsNestedAndSelfReferentialObjectArrays() {
        Object[] selfReferentialArray = new Object[1];
        Object[] nestedArray = new Object[]{new Object[]{"alpha", new int[]{1, 2}}, "omega"};

        selfReferentialArray[0] = selfReferentialArray;

        String nestedArrayMessage = MessageFormatter.arrayFormat("Nested {}", new Object[]{nestedArray});
        String selfReferentialArrayMessage = MessageFormatter.arrayFormat("Self {}", new Object[]{selfReferentialArray});

        assertThat(nestedArrayMessage).isEqualTo("Nested [[alpha, [1, 2]], omega]");
        assertThat(selfReferentialArrayMessage).isEqualTo("Self [[...]]");
    }

    private static final class RecordingLogger extends MarkerIgnoringBase {

        private final List<LogEntry> entries = new ArrayList<>();

        private RecordingLogger(String name) {
            this.name = name;
        }

        private List<LogEntry> getEntries() {
            return Collections.unmodifiableList(this.entries);
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public void trace(String msg) {
            recordPlain("TRACE", msg, null);
        }

        @Override
        public void trace(String format, Object arg) {
            recordFormatted("TRACE", MessageFormatter.format(format, arg), null);
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
            recordFormatted("TRACE", MessageFormatter.format(format, arg1, arg2), null);
        }

        @Override
        public void trace(String format, Object[] arguments) {
            recordFormatted("TRACE", MessageFormatter.arrayFormat(format, arguments), null);
        }

        @Override
        public void trace(String msg, Throwable t) {
            recordPlain("TRACE", msg, t);
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void debug(String msg) {
            recordPlain("DEBUG", msg, null);
        }

        @Override
        public void debug(String format, Object arg) {
            recordFormatted("DEBUG", MessageFormatter.format(format, arg), null);
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
            recordFormatted("DEBUG", MessageFormatter.format(format, arg1, arg2), null);
        }

        @Override
        public void debug(String format, Object[] arguments) {
            recordFormatted("DEBUG", MessageFormatter.arrayFormat(format, arguments), null);
        }

        @Override
        public void debug(String msg, Throwable t) {
            recordPlain("DEBUG", msg, t);
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(String msg) {
            recordPlain("INFO", msg, null);
        }

        @Override
        public void info(String format, Object arg) {
            recordFormatted("INFO", MessageFormatter.format(format, arg), null);
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
            recordFormatted("INFO", MessageFormatter.format(format, arg1, arg2), null);
        }

        @Override
        public void info(String format, Object[] arguments) {
            recordFormatted("INFO", MessageFormatter.arrayFormat(format, arguments), null);
        }

        @Override
        public void info(String msg, Throwable t) {
            recordPlain("INFO", msg, t);
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void warn(String msg) {
            recordPlain("WARN", msg, null);
        }

        @Override
        public void warn(String format, Object arg) {
            recordFormatted("WARN", MessageFormatter.format(format, arg), null);
        }

        @Override
        public void warn(String format, Object[] arguments) {
            recordFormatted("WARN", MessageFormatter.arrayFormat(format, arguments), null);
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
            recordFormatted("WARN", MessageFormatter.format(format, arg1, arg2), null);
        }

        @Override
        public void warn(String msg, Throwable t) {
            recordPlain("WARN", msg, t);
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void error(String msg) {
            recordPlain("ERROR", msg, null);
        }

        @Override
        public void error(String format, Object arg) {
            recordFormatted("ERROR", MessageFormatter.format(format, arg), null);
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
            recordFormatted("ERROR", MessageFormatter.format(format, arg1, arg2), null);
        }

        @Override
        public void error(String format, Object[] arguments) {
            recordFormatted("ERROR", MessageFormatter.arrayFormat(format, arguments), null);
        }

        @Override
        public void error(String msg, Throwable t) {
            recordPlain("ERROR", msg, t);
        }

        private void recordPlain(String level, String message, Throwable throwable) {
            this.entries.add(new LogEntry(level, message, throwable));
        }

        private void recordFormatted(String level, String formattedMessage, Throwable throwable) {
            this.entries.add(new LogEntry(level, formattedMessage, throwable));
        }
    }

    private static final class LogEntry {

        private final String level;
        private final String formattedMessage;
        private final Throwable throwable;

        private LogEntry(String level, String formattedMessage, Throwable throwable) {
            this.level = level;
            this.formattedMessage = formattedMessage;
            this.throwable = throwable;
        }
    }
}
