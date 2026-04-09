/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DefaultConfigurationLocatorTest {

    private static final String LOGGING_CONFIGURATION_PROPERTY = "logging.configuration";

    @Test
    void findConfigurationUsesThreadContextClassLoaderResourceFirst() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final String originalLoggingConfiguration = System.getProperty(LOGGING_CONFIGURATION_PROPERTY);

        try {
            System.clearProperty(LOGGING_CONFIGURATION_PROPERTY);
            thread.setContextClassLoader(DefaultConfigurationLocatorTest.class.getClassLoader());

            try (InputStream inputStream = new DefaultConfigurationLocator().findConfiguration()) {
                assertThat(readConfiguration(inputStream)).isEqualTo("context class loader configuration");
            }
        } finally {
            restoreProperty(originalLoggingConfiguration);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void findConfigurationFallsBackToLocatorPackageResourceWhenThreadContextClassLoaderIsNull() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final String originalLoggingConfiguration = System.getProperty(LOGGING_CONFIGURATION_PROPERTY);

        try {
            System.clearProperty(LOGGING_CONFIGURATION_PROPERTY);
            thread.setContextClassLoader(null);

            try (InputStream inputStream = new DefaultConfigurationLocator().findConfiguration()) {
                assertThat(readConfiguration(inputStream)).isEqualTo("locator package configuration");
            }
        } finally {
            restoreProperty(originalLoggingConfiguration);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreProperty(final String value) {
        if (value == null) {
            System.clearProperty(LOGGING_CONFIGURATION_PROPERTY);
            return;
        }
        System.setProperty(LOGGING_CONFIGURATION_PROPERTY, value);
    }

    private static String readConfiguration(final InputStream inputStream) throws IOException {
        assertThat(inputStream).isNotNull();
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
}
