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
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
public class LogFactoryTest {

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

    @Test
    void createsLoggerForRequestedName() {
        String loggerName = "factory-created-logger";
        Logger julLogger = Logger.getLogger(loggerName);
        Level originalLevel = julLogger.getLevel();

        try {
            julLogger.setLevel(Level.INFO);

            Log log = LogFactory.getLog(loggerName);

            assertThat(log.isInfoEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isFalse();
        } finally {
            julLogger.setLevel(originalLevel);
        }
    }
}
