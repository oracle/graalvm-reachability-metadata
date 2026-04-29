/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.hsqldb.lib.FrameworkLogger;
import org.junit.jupiter.api.Test;

public class FrameworkLoggerTest {
    @Test
    void logsMessagesThroughDetectedLoggingBackend() {
        String loggerName = FrameworkLoggerTest.class.getName() + ".dynamicAccess";
        RuntimeException failure = new RuntimeException("details for the logging backend");
        Logger backendLogger = Logger.getLogger(loggerName);

        assertThat(backendLogger.getName()).isEqualTo(loggerName);

        FrameworkLogger.clearLoggers(loggerName);

        FrameworkLogger logger = FrameworkLogger.getLog(loggerName);
        logger.info("informational message for the configured backend");
        logger.warning("warning message for the configured backend");
        logger.log(Level.SEVERE, "severe message for the configured backend", failure);
        logger.privlog(Level.FINER, "caller-adjusted message for the configured backend", null, 1,
                FrameworkLoggerTest.class);
        logger.enduserlog(Level.INFO, "end-user message for the configured backend");

        assertThat(FrameworkLogger.report()).contains(loggerName);

        FrameworkLogger.clearLoggers(loggerName);

        assertThat(FrameworkLogger.report()).doesNotContain(loggerName);
    }
}
