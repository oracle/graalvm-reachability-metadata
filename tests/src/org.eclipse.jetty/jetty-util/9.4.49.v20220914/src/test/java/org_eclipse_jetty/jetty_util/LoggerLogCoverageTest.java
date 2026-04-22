/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.LoggerLog;
import org.eclipse.jetty.util.log.Slf4jLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerLogCoverageTest {
    @Test
    void loggerLogDelegatesThroughReflectedMethods() {
        LoggerLog logger = new LoggerLog(new Slf4jLog("logger-log-coverage"));

        assertThat(logger.getName()).isEqualTo("logger-log-coverage");

        logger.warn("unused", "warn {}", new Object[]{"value"});
        logger.warn("warn", new IllegalStateException("warn"));

        logger.info("unused", "info {}", new Object[]{"value"});
        logger.info("info", new IllegalStateException("info"));

        logger.setDebugEnabled(true);
        assertThat(logger.isDebugEnabled()).isTrue();

        logger.debug("unused", "debug {}", new Object[]{"value"});
        logger.debug("debug", new IllegalStateException("debug"));
        logger.debug("debug-long", 7L);

        Logger child = logger.getLogger("child");
        assertThat(child.getName()).contains("child");
    }
}
