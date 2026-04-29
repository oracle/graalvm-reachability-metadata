/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_juli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Test;

public class DirectJDKLogTest {
    @Test
    void createsLogAndPublishesMessagesThroughJdkLogging() {
        Log log = LogFactory.getLog("coverage.direct-jdk-log");

        exerciseLoggingMethods(log);
    }

    private static void exerciseLoggingMethods(Log log) {
        String loggerName = "coverage.direct-jdk-log";
        Logger logger = Logger.getLogger(loggerName);
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        RecordingHandler handler = new RecordingHandler();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            log.trace("trace message");
            log.debug("debug message");
            log.info("info message");
            log.warn("warn message");
            log.error("error message", new IllegalStateException("error cause"));
            log.fatal("fatal message", new IllegalArgumentException("fatal cause"));

            assertThat(log.isTraceEnabled()).isTrue();
            assertThat(log.isDebugEnabled()).isTrue();
            assertThat(log.isInfoEnabled()).isTrue();
            assertThat(log.isWarnEnabled()).isTrue();
            assertThat(log.isErrorEnabled()).isTrue();
            assertThat(log.isFatalEnabled()).isTrue();
            assertThat(handler.getPublishedMessages()).containsExactly(
                    "trace message",
                    "debug message",
                    "info message",
                    "warn message",
                    "error message",
                    "fatal message");
        } finally {
            logger.removeHandler(handler);
            handler.close();
            logger.setUseParentHandlers(previousUseParentHandlers);
            logger.setLevel(previousLevel);
        }
    }

    private static final class RecordingHandler extends Handler {
        private final List<String> publishedMessages = new ArrayList<>();

        RecordingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            publishedMessages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> getPublishedMessages() {
            return publishedMessages;
        }
    }
}
