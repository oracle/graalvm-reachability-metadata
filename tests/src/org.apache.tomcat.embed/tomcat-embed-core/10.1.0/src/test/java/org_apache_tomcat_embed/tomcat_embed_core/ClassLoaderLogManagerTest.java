/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.WebappProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderLogManagerTest {

    @Test
    void readsConfigurationFromContextUrlClassLoader() throws Exception {
        URL configuration = ClassLoaderLogManagerTest.class.getResource("/classloader-logging.properties");
        assertThat(configuration).isNotNull();

        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        try (LoggingConfigurationClassLoader classLoader = new LoggingConfigurationClassLoader(configuration)) {
            String level = readConfigurationProperty(manager, classLoader, "integration.logger.level");

            assertThat(level).isEqualTo("FINE");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void readsConfigurationFromContextWebappClassLoader() throws Exception {
        URL configuration = ClassLoaderLogManagerTest.class.getResource("/classloader-logging.properties");
        assertThat(configuration).isNotNull();

        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        try (WebappLoggingConfigurationClassLoader classLoader =
                new WebappLoggingConfigurationClassLoader(configuration)) {
            String level = readConfigurationProperty(manager, classLoader, "integration.logger.level");

            assertThat(level).isEqualTo("FINE");
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void createsHandlersDeclaredInLoggingConfiguration() throws Exception {
        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        byte[] configuration = "handlers=java.util.logging.ConsoleHandler\n"
                .getBytes(StandardCharsets.UTF_8);

        try {
            manager.readConfiguration(new ByteArrayInputStream(configuration));
            Logger rootLogger = manager.getLogger("");

            assertThat(rootLogger).isNotNull();
            assertThat(rootLogger.getHandlers()).hasSize(1);
            assertThat(rootLogger.getHandlers()[0]).isInstanceOf(ConsoleHandler.class);
        } finally {
            manager.shutdown();
        }
    }

    private static String readConfigurationProperty(
            ClassLoaderLogManager manager, ClassLoader classLoader, String propertyName) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            manager.readConfiguration();
            return manager.getProperty(propertyName);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static class LoggingConfigurationClassLoader extends URLClassLoader {
        private final URL configuration;

        private LoggingConfigurationClassLoader(URL configuration) {
            super(new URL[0], null);
            this.configuration = configuration;
        }

        @Override
        public URL findResource(String name) {
            if ("logging.properties".equals(name)) {
                return configuration;
            }
            return null;
        }
    }

    private static final class WebappLoggingConfigurationClassLoader extends LoggingConfigurationClassLoader
            implements WebappProperties {

        private WebappLoggingConfigurationClassLoader(URL configuration) {
            super(configuration);
        }

        @Override
        public String getWebappName() {
            return "integration-webapp";
        }

        @Override
        public String getHostName() {
            return "localhost";
        }

        @Override
        public String getServiceName() {
            return "integration-service";
        }

        @Override
        public boolean hasLoggingConfig() {
            return true;
        }
    }
}
