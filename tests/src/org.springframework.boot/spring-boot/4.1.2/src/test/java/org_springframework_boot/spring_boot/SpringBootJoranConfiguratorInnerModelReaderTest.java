/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootJoranConfiguratorInnerModelReaderTest {
    @Test
    void restoresLoggerLevelsFromAheadOfTimeConfiguration() {
        String previous = System.getProperty("spring.aot.enabled");
        LogbackLoggingSystem logging = new LogbackLoggingSystem(getClass().getClassLoader());
        LogLevel rootLevel = logging.getLoggerConfiguration(LoggingSystem.ROOT_LOGGER_NAME).getConfiguredLevel();
        try {
            System.setProperty("spring.aot.enabled", "true");
            logging.beforeInitialize();
            logging.initialize(new LoggingInitializationContext(new StandardEnvironment()), null, null);

            assertThat(logging.getLoggerConfiguration("consumer.aot.orders").getConfiguredLevel())
                    .isEqualTo(LogLevel.DEBUG);
            assertThat(logging.getLoggerConfiguration(LoggingSystem.ROOT_LOGGER_NAME).getConfiguredLevel())
                    .isEqualTo(LogLevel.ERROR);
        } finally {
            logging.cleanUp();
            logging.setLogLevel("consumer.aot.orders", null);
            logging.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, rootLevel);
            if (previous == null) {
                System.clearProperty("spring.aot.enabled");
            } else {
                System.setProperty("spring.aot.enabled", previous);
            }
        }
    }
}
