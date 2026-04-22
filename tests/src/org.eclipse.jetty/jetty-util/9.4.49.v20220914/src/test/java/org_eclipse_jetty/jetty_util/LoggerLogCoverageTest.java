/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.LoggerLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerLogCoverageTest {
    public static class RecordingLogger {
        private final String name;
        private boolean debugEnabled;

        public RecordingLogger(String name) {
            this.name = name;
        }

        public void debug(String message, Throwable thrown) {
        }

        public void debug(String message, Object... arguments) {
        }

        public void info(String message, Throwable thrown) {
        }

        public void info(String message, Object... arguments) {
        }

        public void warn(String message, Throwable thrown) {
        }

        public void warn(String message, Object... arguments) {
        }

        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        public void setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
        }

        public RecordingLogger getLogger(String loggerName) {
            return new RecordingLogger(loggerName);
        }

        public String getName() {
            return name;
        }
    }

    @Test
    void loggerLogDelegatesThroughReflectedMethods() {
        LoggerLog logger = new LoggerLog(new RecordingLogger("logger-log-coverage"));

        assertThat(logger.getName()).isEqualTo("logger-log-coverage");

        logger.warn("unused", "warn {}", new Object[]{"value"});
        logger.warn("warn", new IllegalStateException("warn"));

        logger.info("unused", "info {}", new Object[]{"value"});
        logger.info("info", new IllegalStateException("info"));

        logger.setDebugEnabled(true);
        assertThat(logger.isDebugEnabled()).isTrue();

        logger.debug("unused", "debug {}", new Object[]{"value"});
        logger.debug("debug", new IllegalStateException("debug"));
        logger.debug("debug-long", 7L);

        Logger child = logger.getLogger("child");
        assertThat(child.getName()).isEqualTo("logger-log-coverage.child");
    }
}
