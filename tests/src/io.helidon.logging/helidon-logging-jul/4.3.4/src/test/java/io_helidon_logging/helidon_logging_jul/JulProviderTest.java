/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_logging.helidon_logging_jul;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.logging.jul.JulProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JulProviderTest {
    private static final String DISABLE_CONFIG_PROPERTY = "io.helidon.logging.config.disabled";
    private static final String LOGGING_CLASS_PROPERTY = "java.util.logging.config.class";
    private static final String LOGGING_FILE_PROPERTY = "java.util.logging.config.file";
    private static final String CONFIGURED_LOGGER_NAME = JulProviderTest.class.getName() + ".configured";

    @Test
    void initializationFindsLoggingTestPropertiesOnContextClassPath() {
        String originalDisableConfig = System.getProperty(DISABLE_CONFIG_PROPERTY);
        String originalLoggingClass = System.getProperty(LOGGING_CLASS_PROPERTY);
        String originalLoggingFile = System.getProperty(LOGGING_FILE_PROPERTY);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Logger configuredLogger = Logger.getLogger(CONFIGURED_LOGGER_NAME);
        Level originalLevel = configuredLogger.getLevel();
        TrackingLoggingClassLoader loggingClassLoader = new TrackingLoggingClassLoader(originalClassLoader);

        try {
            System.clearProperty(DISABLE_CONFIG_PROPERTY);
            System.clearProperty(LOGGING_CLASS_PROPERTY);
            System.clearProperty(LOGGING_FILE_PROPERTY);
            Thread.currentThread().setContextClassLoader(loggingClassLoader);

            new JulProvider().initialization();

            assertThat(loggingClassLoader.requestedResources()).contains("logging-test.properties");
            assertThat(configuredLogger.getLevel()).isEqualTo(Level.FINEST);
        } finally {
            restoreProperty(DISABLE_CONFIG_PROPERTY, originalDisableConfig);
            restoreProperty(LOGGING_CLASS_PROPERTY, originalLoggingClass);
            restoreProperty(LOGGING_FILE_PROPERTY, originalLoggingFile);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            LogManager.getLogManager().reset();
            configuredLogger.setLevel(originalLevel);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static final class TrackingLoggingClassLoader extends ClassLoader {
        private final List<String> requestedResources = new ArrayList<>();

        private TrackingLoggingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResources.add(name);
            if ("logging-test.properties".equals(name)) {
                String configuration = CONFIGURED_LOGGER_NAME + ".level=FINEST\n";
                return new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }

        private List<String> requestedResources() {
            return requestedResources;
        }
    }
}
