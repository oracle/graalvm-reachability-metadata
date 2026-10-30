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
    void loadsTheExplicitlyConfiguredJavaLoggingSystem() {
        String previousSystem = System.getProperty(LoggingSystem.SYSTEM_PROPERTY);
        String loggerName = "metadata.boot.logging";
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, JavaLoggingSystem.class.getName());
        LoggingSystem system = null;
        try {
            system = LoggingSystem.get(getClass().getClassLoader());
            assertThat(system).isInstanceOf(JavaLoggingSystem.class);
            system.setLogLevel(loggerName, LogLevel.DEBUG);
            assertThat(system.getLoggerConfiguration(loggerName).getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);
            system.setLogLevel(loggerName, LogLevel.WARN);
            assertThat(system.getLoggerConfiguration(loggerName).getConfiguredLevel()).isEqualTo(LogLevel.WARN);
        } finally {
            if (system != null) {
                system.setLogLevel(loggerName, null);
                system.cleanUp();
            }
            if (previousSystem == null) {
                System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
            } else {
                System.setProperty(LoggingSystem.SYSTEM_PROPERTY, previousSystem);
            }
        }
    }
}
