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
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.LoggingEvent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class JDBCAppenderTest {

    @Test
    void setDriverLoadsAvailableClassWithoutReportingAnError() {
        RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        JDBCAppender appender = new JDBCAppender();
        appender.setErrorHandler(errorHandler);

        try {
            appender.setDriver(String.class.getName());
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
            return;
        }

        assertThat(errorHandler.message).isNull();
        assertThat(errorHandler.exception).isNull();
    }

    @Test
    void setDriverReportsMissingDriverClassThroughAppenderErrorHandler() {
        RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        JDBCAppender appender = new JDBCAppender();
        appender.setErrorHandler(errorHandler);

        try {
            appender.setDriver("ch_qos_reload4j.reload4j.MissingJdbcDriver");
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
            return;
        }

        assertThat(errorHandler.message).isEqualTo("Failed to load driver");
        assertThat(errorHandler.exception).isInstanceOf(ClassNotFoundException.class);
        assertThat(errorHandler.errorCode).isEqualTo(ErrorCode.GENERIC_FAILURE);
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class RecordingErrorHandler implements ErrorHandler {
        private String message;
        private Exception exception;
        private int errorCode;

        @Override
        public void setLogger(Logger logger) {
        }

        @Override
        public void error(String message, Exception exception, int errorCode) {
            this.message = message;
            this.exception = exception;
            this.errorCode = errorCode;
        }

        @Override
        public void error(String message) {
            this.message = message;
        }

        @Override
        public void error(String message, Exception exception, int errorCode, LoggingEvent event) {
            error(message, exception, errorCode);
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
