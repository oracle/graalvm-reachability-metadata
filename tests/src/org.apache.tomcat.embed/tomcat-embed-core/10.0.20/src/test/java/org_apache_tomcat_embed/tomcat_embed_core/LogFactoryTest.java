/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(-2)
public class LogFactoryTest {

    @Test
    void createsLoggerWithRequestedName() {
        String loggerName = "factory-created-logger";
        Logger logger = Logger.getLogger(loggerName);
        CapturingHandler handler = new CapturingHandler();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            Log log = LogFactory.getFactory().getInstance(loggerName);
            log.info("ready");

            assertThat(handler.getRecord()).isNotNull();
            assertThat(handler.getRecord().getLoggerName()).isEqualTo(loggerName);
            assertThat(handler.getRecord().getMessage()).isEqualTo("ready");
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
            handler.close();
        }
    }

    public static class TestLog implements Log {
        static {
            Logger rootLogger = Logger.getLogger("");
            for (Handler handler : rootLogger.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setFormatter(new SimpleFormatter());
                }
            }
        }

        private final Logger logger;

        public TestLog() {
            this(TestLog.class.getName());
        }

        public TestLog(String name) {
            logger = Logger.getLogger(name);
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isLoggable(Level.FINE);
        }

        @Override
        public boolean isErrorEnabled() {
            return logger.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isFatalEnabled() {
            return logger.isLoggable(Level.SEVERE);
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isLoggable(Level.INFO);
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isLoggable(Level.FINEST);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isLoggable(Level.WARNING);
        }

        @Override
        public void trace(Object message) {
            logger.finest(String.valueOf(message));
        }

        @Override
        public void trace(Object message, Throwable throwable) {
            logger.log(Level.FINEST, String.valueOf(message), throwable);
        }

        @Override
        public void debug(Object message) {
            logger.fine(String.valueOf(message));
        }

        @Override
        public void debug(Object message, Throwable throwable) {
            logger.log(Level.FINE, String.valueOf(message), throwable);
        }

        @Override
        public void info(Object message) {
            logger.info(String.valueOf(message));
        }

        @Override
        public void info(Object message, Throwable throwable) {
            logger.log(Level.INFO, String.valueOf(message), throwable);
        }

        @Override
        public void warn(Object message) {
            logger.warning(String.valueOf(message));
        }

        @Override
        public void warn(Object message, Throwable throwable) {
            logger.log(Level.WARNING, String.valueOf(message), throwable);
        }

        @Override
        public void error(Object message) {
            logger.severe(String.valueOf(message));
        }

        @Override
        public void error(Object message, Throwable throwable) {
            logger.log(Level.SEVERE, String.valueOf(message), throwable);
        }

        @Override
        public void fatal(Object message) {
            logger.severe(String.valueOf(message));
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
            logger.log(Level.SEVERE, String.valueOf(message), throwable);
        }
    }

    private static final class CapturingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord value) {
            record = value;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        public LogRecord getRecord() {
            return record;
        }
    }
}
