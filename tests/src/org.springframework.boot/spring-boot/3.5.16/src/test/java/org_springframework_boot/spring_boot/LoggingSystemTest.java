/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.java.JavaLoggingSystem;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingSystemTest {

    private static final String LOGGER_NAME = "org_springframework_boot.spring_boot.audit";

    @AfterEach
    void clearLoggingSystemProperty() {
        System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
    }

    @Test
    void createsLoggingSystemNamedBySystemProperty() {
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, JavaLoggingSystem.class.getName());

        LoggingSystem loggingSystem = LoggingSystem.get(LoggingSystemTest.class.getClassLoader());

        assertThat(loggingSystem).isInstanceOf(JavaLoggingSystem.class);
        loggingSystem.beforeInitialize();
        loggingSystem.setLogLevel(LOGGER_NAME, LogLevel.DEBUG);
        LoggerConfiguration configuration = loggingSystem.getLoggerConfiguration(LOGGER_NAME);
        assertThat(configuration.getConfiguredLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(loggingSystem.getSupportedLogLevels()).contains(LogLevel.INFO, LogLevel.ERROR);
    }

    @Test
    void disablesLoggingSystemWithNoneProperty() {
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);

        LoggingSystem loggingSystem = LoggingSystem.get(LoggingSystemTest.class.getClassLoader());

        assertThat(loggingSystem).isNotInstanceOf(JavaLoggingSystem.class);
        assertThat(loggingSystem.getLoggerConfigurations()).isEmpty();
    }
}
