/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_api;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.SubstituteLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class SubstituteLoggerTest {

    @Test
    void invokesEventAwareDelegateLogMethod() {
        SubstituteLogger substituteLogger = new SubstituteLogger("test-logger", new ConcurrentLinkedQueue<>(), false);
        RecordingEventAwareLogger delegate = new RecordingEventAwareLogger("delegate");
        SubstituteLoggingEvent event = new SubstituteLoggingEvent();
        event.setLevel(Level.INFO);
        event.setLogger(substituteLogger);
        event.setLoggerName(substituteLogger.getName());
        event.setMessage("message");

        substituteLogger.setDelegate(delegate);

        assertThat(substituteLogger.isDelegateEventAware()).isTrue();

        substituteLogger.log(event);

        assertThat(delegate.loggedEvent).isSameAs(event);
    }

    public static final class RecordingEventAwareLogger extends MarkerIgnoringBase {

        private LoggingEvent loggedEvent;

        public RecordingEventAwareLogger(String name) {
            this.name = name;
        }

        public void log(LoggingEvent event) {
            this.loggedEvent = event;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String msg) {
        }

        @Override
        public void trace(String format, Object arg) {
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
        }

        @Override
        public void trace(String format, Object... arguments) {
        }

        @Override
        public void trace(String msg, Throwable t) {
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String msg) {
        }

        @Override
        public void debug(String format, Object arg) {
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
        }

        @Override
        public void debug(String format, Object... arguments) {
        }

        @Override
        public void debug(String msg, Throwable t) {
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(String msg) {
        }

        @Override
        public void info(String format, Object arg) {
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
        }

        @Override
        public void info(String format, Object... arguments) {
        }

        @Override
        public void info(String msg, Throwable t) {
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(String msg) {
        }

        @Override
        public void warn(String format, Object arg) {
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
        }

        @Override
        public void warn(String format, Object... arguments) {
        }

        @Override
        public void warn(String msg, Throwable t) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(String msg) {
        }

        @Override
        public void error(String format, Object arg) {
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
        }

        @Override
        public void error(String format, Object... arguments) {
        }

        @Override
        public void error(String msg, Throwable t) {
        }
    }
}
