/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.java.JavaLoggingSystem;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingSystemTest {
    @Test
    void selectsExplicitLoggingSystemAndConfiguresLogger() {
        String original = System.getProperty(LoggingSystem.SYSTEM_PROPERTY);
        Logger logger = Logger.getLogger("boot.consumer.orders");
        Level originalLevel = logger.getLevel();
        try {
            System.setProperty(LoggingSystem.SYSTEM_PROPERTY, JavaLoggingSystem.class.getName());
            LoggingSystem system = LoggingSystem.get(getClass().getClassLoader());
            try {
                system.setLogLevel(logger.getName(), LogLevel.DEBUG);

                assertThat(system).isInstanceOf(JavaLoggingSystem.class);
                assertThat(system.getLoggerConfiguration(logger.getName()).getConfiguredLevel())
                        .isEqualTo(LogLevel.DEBUG);
                assertThat(logger.getLevel()).isEqualTo(Level.FINE);
            } finally {
                system.cleanUp();
            }
        } finally {
            logger.setLevel(originalLevel);
            if (original == null) {
                System.clearProperty(LoggingSystem.SYSTEM_PROPERTY);
            } else {
                System.setProperty(LoggingSystem.SYSTEM_PROPERTY, original);
            }
        }
    }
}
