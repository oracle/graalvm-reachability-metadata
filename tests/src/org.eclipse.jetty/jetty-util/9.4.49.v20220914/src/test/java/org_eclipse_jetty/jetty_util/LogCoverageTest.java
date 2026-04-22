/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StdErrLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogCoverageTest {
    @Test
    void logInitializesAndCanAttemptParentDelegation() {
        Log.initialized();
        Logger original = Log.getRootLogger();
        Logger logger = Log.getLogger(LogCoverageTest.class);

        assertThat(original).isInstanceOf(StdErrLog.class);
        assertThat(logger).isNotNull();

        try {
            Log.setLogToParent("log-coverage-parent");

            assertThat(Log.getRootLogger()).isNotNull();
        } finally {
            Log.setLog(original);
        }
    }
}
