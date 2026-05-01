/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.log.Log;
import org.mortbay.log.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LogTest {
    static {
        Log.getLog();
    }

    @Test
    void initializesDefaultLoggerAndProvidesRootLogger() {
        Logger logger = Log.getLog();

        assertThat(logger).isNotNull();
        assertThat(Log.getLogger(null)).isSameAs(logger);
    }

    @Test
    void warnUnwindsInvocationTargetException() {
        Logger previousLogger = Log.getLog();
        CapturingLogger logger = new CapturingLogger();
        IllegalArgumentException nested = new IllegalArgumentException("nested failure");
        InvocationTargetException wrapper = new InvocationTargetException(nested, "wrapper failure");

        try {
            Log.setLog(logger);
            Log.warn(wrapper);
        } finally {
            Log.setLog(previousLogger);
        }

        assertThat(logger.throwableWarnings).contains(wrapper, nested);
        assertThat(logger.messages).anySatisfy(message -> assertThat(message)
                .contains("Nested in java.lang.reflect.InvocationTargetException")
                .contains("wrapper failure"));
    }

    public static final class CapturingLogger implements Logger {
        private final List<String> messages = new ArrayList<>();
        private final List<Throwable> throwableWarnings = new ArrayList<>();
        private boolean debugEnabled;

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public void setDebugEnabled(boolean enabled) {
            debugEnabled = enabled;
        }

        @Override
        public void info(String msg, Object arg0, Object arg1) {
            messages.add(msg);
        }

        @Override
        public void debug(String msg, Throwable th) {
            messages.add(msg);
            if (th != null) {
                throwableWarnings.add(th);
            }
        }

        @Override
        public void debug(String msg, Object arg0, Object arg1) {
            messages.add(msg);
        }

        @Override
        public void warn(String msg, Object arg0, Object arg1) {
            messages.add(msg);
        }

        @Override
        public void warn(String msg, Throwable th) {
            messages.add(msg);
            if (th != null) {
                throwableWarnings.add(th);
            }
        }

        @Override
        public Logger getLogger(String name) {
            return this;
        }
    }
}
