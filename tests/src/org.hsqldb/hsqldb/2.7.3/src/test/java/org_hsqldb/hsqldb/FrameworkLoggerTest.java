/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.logging.Level;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hsqldb.lib.FrameworkLogger;
import org.junit.jupiter.api.Test;

public class FrameworkLoggerTest {

    @Test
    void delegatesFrameworkAndEndUserMessagesToLog4j() {
        String loggerName = FrameworkLoggerTest.class.getName() + "."
                + UUID.randomUUID().toString().replace("-", "");

        try {
            FrameworkLogger logger = FrameworkLogger.getLog(loggerName);
            RuntimeException exception = new RuntimeException("expected logging exception");

            logger.info("framework message");
            logger.warning("framework warning", exception);
            logger.enduserlog(Level.SEVERE, "end-user message");

            assertThat(FrameworkLogger.getLog(loggerName)).isSameAs(logger);
            assertThat(FrameworkLogger.report()).contains(loggerName);
            assertThat(Log4jBackend.hasLogger(loggerName)).isTrue();
        } finally {
            FrameworkLogger.clearLoggers(loggerName);
            Log4jBackend.removeLogger(loggerName);
        }
    }

    private static final class Log4jBackend {
        private Log4jBackend() {
        }

        private static boolean hasLogger(String loggerName) {
            return LogManager.exists(loggerName) != null;
        }

        private static void removeLogger(String loggerName) {
            Logger logger = LogManager.exists(loggerName);

            if (logger != null) {
                logger.removeAllAppenders();
            }
        }
    }
}
