/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootJoranConfiguratorInnerModelReaderTest {
    @Test
    void restoresBuildTimeLoggingConfigurationAndResolvesRuntimeEnvironmentProperties() {
        String previous = System.getProperty("spring.aot.enabled");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of("deployment.region", "west")));
        LogbackLoggingSystem system = new LogbackLoggingSystem(getClass().getClassLoader());
        try {
            System.setProperty("spring.aot.enabled", "true");
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(environment), null, null);

            assertThat(system.getLoggerConfiguration("ROOT").getConfiguredLevel()).isEqualTo(LogLevel.WARN);
            assertThat(system.getLoggerConfiguration("consumer.aot").getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            assertThat(context.getProperty("region")).isEqualTo("west");
        } finally {
            system.getShutdownHandler().run();
            system.cleanUp();
            if (previous == null) {
                System.clearProperty("spring.aot.enabled");
            } else {
                System.setProperty("spring.aot.enabled", previous);
            }
        }
    }
}
