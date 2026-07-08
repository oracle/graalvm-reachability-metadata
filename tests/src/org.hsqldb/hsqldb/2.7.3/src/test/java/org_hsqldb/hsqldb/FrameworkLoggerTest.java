/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;

import org.hsqldb.lib.FrameworkLogger;
import org.junit.jupiter.api.Test;

public class FrameworkLoggerTest {
    @Test
    void delegatesToLog4jWhenAvailable() {
        String category = FrameworkLoggerTest.class.getName() + ".log4j";

        FrameworkLogger.clearLoggers(category);

        try {
            FrameworkLogger logger = FrameworkLogger.getLog(category);

            assertThat(FrameworkLogger.getLog(category)).isSameAs(logger);

            logger.log(Level.INFO, "Framework logger message");
            logger.enduserlog(Level.INFO, "End-user logger message");

            assertThat(FrameworkLogger.report()).contains(category);
        } finally {
            FrameworkLogger.clearLoggers(category);
        }
    }
}
