/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.jdbc.JDBCAppender;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

public class JDBCAppenderTest {
    @Test
    void loadsConfiguredDriverClassName() {
        JDBCAppender appender = new JDBCAppender();
        RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        appender.setErrorHandler(errorHandler);

        appender.setDriver(Reload4jJdbcAppenderTestDriver.class.getName());

        assertThat(errorHandler.errorCount).isZero();
    }

    private static final class RecordingErrorHandler implements ErrorHandler {
        private int errorCount;

        @Override
        public void setLogger(Logger logger) {
        }

        @Override
        public void error(String message, Exception exception, int errorCode) {
            errorCount++;
        }

        @Override
        public void error(String message) {
            errorCount++;
        }

        @Override
        public void error(String message, Exception exception, int errorCode, LoggingEvent event) {
            errorCount++;
        }

        @Override
        public void setAppender(Appender appender) {
        }

        @Override
        public void setBackupAppender(Appender appender) {
        }

        @Override
        public void activateOptions() {
        }
    }
}

final class Reload4jJdbcAppenderTestDriver {
    private Reload4jJdbcAppenderTestDriver() {
    }
}
