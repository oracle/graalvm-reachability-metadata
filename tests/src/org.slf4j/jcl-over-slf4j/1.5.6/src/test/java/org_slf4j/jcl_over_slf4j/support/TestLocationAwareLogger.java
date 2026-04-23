/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Marker;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

public final class TestLocationAwareLogger extends MarkerIgnoringBase implements LocationAwareLogger {

    private final List<RecordedEvent> events = new ArrayList<>();

    public TestLocationAwareLogger(String name) {
        this.name = name;
    }

    public List<RecordedEvent> getEvents() {
        return Collections.unmodifiableList(this.events);
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void trace(String msg) {
        record(TRACE_INT, msg, null);
    }

    @Override
    public void trace(String format, Object arg) {
        record(TRACE_INT, MessageFormatter.format(format, arg), null);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        record(TRACE_INT, MessageFormatter.format(format, arg1, arg2), null);
    }

    @Override
    public void trace(String format, Object[] arguments) {
        record(TRACE_INT, MessageFormatter.arrayFormat(format, arguments), null);
    }

    @Override
    public void trace(String msg, Throwable t) {
        record(TRACE_INT, msg, t);
    }

    @Override
    public void debug(String msg) {
        record(DEBUG_INT, msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        record(DEBUG_INT, MessageFormatter.format(format, arg), null);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        record(DEBUG_INT, MessageFormatter.format(format, arg1, arg2), null);
    }

    @Override
    public void debug(String format, Object[] arguments) {
        record(DEBUG_INT, MessageFormatter.arrayFormat(format, arguments), null);
    }

    @Override
    public void debug(String msg, Throwable t) {
        record(DEBUG_INT, msg, t);
    }

    @Override
    public void info(String msg) {
        record(INFO_INT, msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        record(INFO_INT, MessageFormatter.format(format, arg), null);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        record(INFO_INT, MessageFormatter.format(format, arg1, arg2), null);
    }

    @Override
    public void info(String format, Object[] arguments) {
        record(INFO_INT, MessageFormatter.arrayFormat(format, arguments), null);
    }

    @Override
    public void info(String msg, Throwable t) {
        record(INFO_INT, msg, t);
    }

    @Override
    public void warn(String msg) {
        record(WARN_INT, msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        record(WARN_INT, MessageFormatter.format(format, arg), null);
    }

    @Override
    public void warn(String format, Object[] arguments) {
        record(WARN_INT, MessageFormatter.arrayFormat(format, arguments), null);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        record(WARN_INT, MessageFormatter.format(format, arg1, arg2), null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        record(WARN_INT, msg, t);
    }

    @Override
    public void error(String msg) {
        record(ERROR_INT, msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        record(ERROR_INT, MessageFormatter.format(format, arg), null);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        record(ERROR_INT, MessageFormatter.format(format, arg1, arg2), null);
    }

    @Override
    public void error(String format, Object[] arguments) {
        record(ERROR_INT, MessageFormatter.arrayFormat(format, arguments), null);
    }

    @Override
    public void error(String msg, Throwable t) {
        record(ERROR_INT, msg, t);
    }

    @Override
    public void log(Marker marker, String fqcn, int level, String message, Throwable throwable) {
        this.events.add(new RecordedEvent(marker, fqcn, level, message, throwable));
    }

    private void record(int level, String message, Throwable throwable) {
        this.events.add(new RecordedEvent(null, TestLocationAwareLogger.class.getName(), level, message, throwable));
    }

    public static final class RecordedEvent {

        private final Marker marker;
        private final String fqcn;
        private final int level;
        private final String message;
        private final Throwable throwable;

        private RecordedEvent(Marker marker, String fqcn, int level, String message, Throwable throwable) {
            this.marker = marker;
            this.fqcn = fqcn;
            this.level = level;
            this.message = message;
            this.throwable = throwable;
        }

        public Marker getMarker() {
            return this.marker;
        }

        public String getFqcn() {
            return this.fqcn;
        }

        public int getLevel() {
            return this.level;
        }

        public String getMessage() {
            return this.message;
        }

        public Throwable getThrowable() {
            return this.throwable;
        }
    }
}
