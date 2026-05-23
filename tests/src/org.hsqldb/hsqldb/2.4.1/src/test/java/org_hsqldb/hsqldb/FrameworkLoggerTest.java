/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.hsqldb.lib.FrameworkLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FrameworkLoggerTest {
    @BeforeAll
    static void configureLoggingFallback() {
        System.setProperty("hsqldb.reconfig_logging", "false");
    }

    @Test
    void log4jBackedLoggerWritesStandardAndEndUserMessages() {
        String loggerName = getClass().getName() + '.' + Long.toUnsignedString(System.nanoTime());

        Logger log4jLogger = Logger.getLogger(loggerName);

        log4jLogger.info("direct log4j message");
        assertThat(log4jLogger.getName()).isEqualTo(loggerName);
        assertThat(Level.toLevel("INFO")).isEqualTo(Level.INFO);

        FrameworkLogger.clearLoggers(loggerName);

        try {
            FrameworkLogger logger = FrameworkLogger.getLog(loggerName);

            logger.info("standard application message");
            logger.log(java.util.logging.Level.WARNING, "application message with throwable",
                    new IllegalStateException("expected"));
            logger.enduserlog(java.util.logging.Level.INFO, "end user message");

            assertThat(FrameworkLogger.report()).contains(loggerName);
        } finally {
            FrameworkLogger.clearLoggers(loggerName);
        }
    }
}
