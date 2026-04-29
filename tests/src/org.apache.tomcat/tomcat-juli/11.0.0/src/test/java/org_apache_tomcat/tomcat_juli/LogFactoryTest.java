/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_juli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(1)
public class LogFactoryTest {
    @Test
    void createsServiceLoadedLogByName() {
        Log log = LogFactory.getLog("coverage.service-loaded-log");

        assertThat(log).isInstanceOf(ServiceLoadedLog.class);
        assertThat(((ServiceLoadedLog) log).getName()).isEqualTo("coverage.service-loaded-log");
    }

    @Test
    void createsServiceLoadedLogFromClassName() {
        Log log = LogFactory.getFactory().getInstance(LogFactoryTest.class);

        assertThat(log).isInstanceOf(ServiceLoadedLog.class);
        assertThat(((ServiceLoadedLog) log).getName()).isEqualTo(LogFactoryTest.class.getName());
    }

    public static final class ServiceLoadedLog implements Log {
        private final String name;
        private final Logger logger;

        public ServiceLoadedLog() {
            this(ServiceLoadedLog.class.getName());
        }

        public ServiceLoadedLog(String name) {
            this.name = name;
            logger = Logger.getLogger(name);
        }

        String getName() {
            return name;
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
            return logger.isLoggable(Level.FINER);
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isLoggable(Level.WARNING);
        }

        @Override
        public void trace(Object message) {
            trace(message, null);
        }

        @Override
        public void trace(Object message, Throwable throwable) {
            log(Level.FINER, message, throwable);
        }

        @Override
        public void debug(Object message) {
            debug(message, null);
        }

        @Override
        public void debug(Object message, Throwable throwable) {
            log(Level.FINE, message, throwable);
        }

        @Override
        public void info(Object message) {
            info(message, null);
        }

        @Override
        public void info(Object message, Throwable throwable) {
            log(Level.INFO, message, throwable);
        }

        @Override
        public void warn(Object message) {
            warn(message, null);
        }

        @Override
        public void warn(Object message, Throwable throwable) {
            log(Level.WARNING, message, throwable);
        }

        @Override
        public void error(Object message) {
            error(message, null);
        }

        @Override
        public void error(Object message, Throwable throwable) {
            log(Level.SEVERE, message, throwable);
        }

        @Override
        public void fatal(Object message) {
            fatal(message, null);
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
            log(Level.SEVERE, message, throwable);
        }

        private void log(Level level, Object message, Throwable throwable) {
            if (throwable == null) {
                logger.log(level, String.valueOf(message));
            } else {
                logger.log(level, String.valueOf(message), throwable);
            }
        }
    }
}
