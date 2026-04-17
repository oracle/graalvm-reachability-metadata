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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LogManagerTest {

    private static final String CONFIGURATION_LOCATOR_PROPERTY = "org.jboss.logmanager.configurationLocator";
    private static final String CONFIGURATOR_PROPERTY = "org.jboss.logmanager.configurator";
    private static final String LOCATED_CONFIGURATION = "logger.level=INFO\n";

    private ClassLoader originalContextClassLoader;
    private String originalConfigurationLocator;
    private String originalConfigurator;

    @BeforeEach
    void captureEnvironment() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalConfigurationLocator = System.getProperty(CONFIGURATION_LOCATOR_PROPERTY);
        originalConfigurator = System.getProperty(CONFIGURATOR_PROPERTY);
        resetTrackingState();
    }

    @AfterEach
    void restoreEnvironment() {
        restoreProperty(CONFIGURATION_LOCATOR_PROPERTY, originalConfigurationLocator);
        restoreProperty(CONFIGURATOR_PROPERTY, originalConfigurator);
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        resetTrackingState();
    }

    @Test
    void readConfigurationUsesContextClassLoaderToInstantiateConfiguredClasses() throws Exception {
        System.setProperty(CONFIGURATION_LOCATOR_PROPERTY, ContextClassLoaderLocator.class.getName());
        System.setProperty(CONFIGURATOR_PROPERTY, ContextClassLoaderConfigurator.class.getName());
        Thread.currentThread().setContextClassLoader(LogManagerTest.class.getClassLoader());

        final LogManager logManager = new LogManager();
        logManager.readConfiguration();

        assertThat(ContextClassLoaderLocator.instancesCreated.get()).isEqualTo(1);
        assertThat(ContextClassLoaderConfigurator.instancesCreated.get()).isEqualTo(1);
        assertThat(ContextClassLoaderConfigurator.configuredText.get()).isEqualTo(LOCATED_CONFIGURATION);
    }

    private static void restoreProperty(final String propertyName, final String propertyValue) {
        if (propertyValue == null) {
            System.clearProperty(propertyName);
            return;
        }
        System.setProperty(propertyName, propertyValue);
    }

    private static void resetTrackingState() {
        ContextClassLoaderLocator.instancesCreated.set(0);
        ContextClassLoaderConfigurator.instancesCreated.set(0);
        ContextClassLoaderConfigurator.configuredText.set(null);
    }

    public static final class ContextClassLoaderLocator implements ConfigurationLocator {

        static final AtomicInteger instancesCreated = new AtomicInteger();

        public ContextClassLoaderLocator() {
            instancesCreated.incrementAndGet();
        }

        @Override
        public InputStream findConfiguration() {
            return new ByteArrayInputStream(LOCATED_CONFIGURATION.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final class ContextClassLoaderConfigurator implements Configurator {

        static final AtomicInteger instancesCreated = new AtomicInteger();
        static final AtomicReference<String> configuredText = new AtomicReference<>();

        public ContextClassLoaderConfigurator() {
            instancesCreated.incrementAndGet();
        }

        @Override
        public void configure(final InputStream inputStream) throws IOException {
            configuredText.set(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

}
