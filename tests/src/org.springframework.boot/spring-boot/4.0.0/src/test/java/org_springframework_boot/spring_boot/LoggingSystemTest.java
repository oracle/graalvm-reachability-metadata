/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.java.JavaLoggingSystem;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingSystemTest {
    @Test
    void selectsAndConfiguresAnExplicitLoggingImplementation() {
        String previous = System.getProperty(LoggingSystem.SYSTEM_PROPERTY);
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, JavaLoggingSystem.class.getName());
        LoggingSystem system = null;
        String logger = "reachability.boot.logging";
        try {
            system = LoggingSystem.get(getClass().getClassLoader());
            assertThat(system).isInstanceOf(JavaLoggingSystem.class);
            system.setLogLevel(logger, LogLevel.WARN);
            assertThat(system.getLoggerConfiguration(logger).getConfiguredLevel()).isEqualTo(LogLevel.WARN);
            system.setLogLevel(logger, LogLevel.TRACE);
            assertThat(system.getLoggerConfiguration(logger).getEffectiveLevel()).isEqualTo(LogLevel.TRACE);
        } finally {
            if (system != null) {
                system.setLogLevel(logger, null);
                system.cleanUp();
            }
            if (previous == null) {
                System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
            } else {
                System.setProperty(LoggingSystem.SYSTEM_PROPERTY, previous);
            }
        }
    }
}
