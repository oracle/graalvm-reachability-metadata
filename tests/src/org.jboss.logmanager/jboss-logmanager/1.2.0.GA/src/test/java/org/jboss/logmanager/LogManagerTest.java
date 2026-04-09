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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogManagerTest {

    private static final String CONFIGURATOR_PROPERTY = "org.jboss.logmanager.configurator";
    private static final String CONFIGURATION_LOCATOR_PROPERTY = "org.jboss.logmanager.configurationLocator";

    @AfterEach
    void resetState() {
        TrackingConfigurator.constructorCalls.set(0);
        TrackingConfigurator.lastConfiguration.set(null);
        TrackingConfigurationLocator.constructorCalls.set(0);
    }

    @Test
    void readConfigurationUsesThreadContextClassLoaderForConfigurator() throws Exception {
        final String payload = "logger configuration from tccl";
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final String originalConfigurator = System.getProperty(CONFIGURATOR_PROPERTY);

        try {
            thread.setContextClassLoader(TrackingConfigurator.class.getClassLoader());
            System.setProperty(CONFIGURATOR_PROPERTY, TrackingConfigurator.class.getName());

            new LogManager().readConfiguration(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));

            assertThat(TrackingConfigurator.constructorCalls.get()).isEqualTo(1);
            assertThat(TrackingConfigurator.lastConfiguration.get()).isEqualTo(payload);
        } finally {
            restoreProperty(CONFIGURATOR_PROPERTY, originalConfigurator);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void readConfigurationFallsBackToLogManagerClassLoaderWhenThreadContextClassLoaderIsNull() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final String originalConfigurator = System.getProperty(CONFIGURATOR_PROPERTY);
        final String originalConfigurationLocator = System.getProperty(CONFIGURATION_LOCATOR_PROPERTY);

        try {
            thread.setContextClassLoader(null);
            System.setProperty(CONFIGURATION_LOCATOR_PROPERTY, TrackingConfigurationLocator.class.getName());
            System.setProperty(CONFIGURATOR_PROPERTY, TrackingConfigurator.class.getName());

            new LogManager().readConfiguration();

            assertThat(TrackingConfigurationLocator.constructorCalls.get()).isEqualTo(1);
            assertThat(TrackingConfigurator.constructorCalls.get()).isEqualTo(1);
            assertThat(TrackingConfigurator.lastConfiguration.get()).isEqualTo(TrackingConfigurationLocator.CONFIGURATION);
        } finally {
            restoreProperty(CONFIGURATOR_PROPERTY, originalConfigurator);
            restoreProperty(CONFIGURATION_LOCATOR_PROPERTY, originalConfigurationLocator);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    public static final class TrackingConfigurator implements Configurator {

        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicReference<String> lastConfiguration = new AtomicReference<>();

        public TrackingConfigurator() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void configure(final InputStream inputStream) throws IOException {
            lastConfiguration.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static final class TrackingConfigurationLocator implements ConfigurationLocator {

        private static final String CONFIGURATION = "logger configuration from fallback class loader";
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        public TrackingConfigurationLocator() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public InputStream findConfiguration() {
            return new ByteArrayInputStream(CONFIGURATION.getBytes(StandardCharsets.UTF_8));
        }
    }
}
