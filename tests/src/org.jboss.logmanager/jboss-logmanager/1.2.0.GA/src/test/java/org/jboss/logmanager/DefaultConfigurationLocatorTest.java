/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultConfigurationLocatorTest {

    private static final String LOGGING_CONFIGURATION_PROPERTY = "logging.configuration";
    private static final String LOGGING_PROPERTIES = "logging.properties";
    private static final String CLASSPATH_CONFIGURATION = "logger.level=INFO\n";

    private ClassLoader originalContextClassLoader;
    private String originalLoggingConfiguration;

    @BeforeEach
    void captureEnvironment() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalLoggingConfiguration = System.getProperty(LOGGING_CONFIGURATION_PROPERTY);
    }

    @AfterEach
    void restoreEnvironment() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        restoreProperty(LOGGING_CONFIGURATION_PROPERTY, originalLoggingConfiguration);
    }

    @Test
    void findConfigurationUsesContextClassLoaderResource() throws IOException {
        System.clearProperty(LOGGING_CONFIGURATION_PROPERTY);
        final TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                DefaultConfigurationLocatorTest.class.getClassLoader(),
                CLASSPATH_CONFIGURATION
        );
        Thread.currentThread().setContextClassLoader(trackingClassLoader);

        try (InputStream inputStream = new DefaultConfigurationLocator().findConfiguration()) {
            assertThat(trackingClassLoader.requestedResourceName).isEqualTo(LOGGING_PROPERTIES);
            assertThat(inputStream).isNotNull();
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(CLASSPATH_CONFIGURATION);
        }
    }

    private static void restoreProperty(final String propertyName, final String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
            return;
        }
        System.setProperty(propertyName, propertyValue);
    }

    private static final class TrackingClassLoader extends ClassLoader {

        private final byte[] configurationBytes;
        private String requestedResourceName;

        private TrackingClassLoader(final ClassLoader parent, final String configuration) {
            super(parent);
            configurationBytes = configuration.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            requestedResourceName = name;
            if (LOGGING_PROPERTIES.equals(name)) {
                return new ByteArrayInputStream(configurationBytes);
            }
            return super.getResourceAsStream(name);
        }
    }
}
