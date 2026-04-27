/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_logging.commons_logging_api;

import org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
public class SimpleLogAnonymous1Test {
    private static final String DEFAULT_LOG_LEVEL_PROPERTY = "org.apache.commons.logging.simplelog.defaultlog";

    @Test
    void initializesSimpleLogWithNoThreadContextClassLoader() {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        String previousDefaultLogLevel = System.getProperty(DEFAULT_LOG_LEVEL_PROPERTY);
        Thread.currentThread().setContextClassLoader(null);
        System.setProperty(DEFAULT_LOG_LEVEL_PROPERTY, "debug");

        try {
            SimpleLog log = new SimpleLog("coverage.simplelog.system.resource");

            assertThat(log.isDebugEnabled()).isTrue();
            assertThat(log.isInfoEnabled()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(DEFAULT_LOG_LEVEL_PROPERTY, previousDefaultLogLevel);
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
