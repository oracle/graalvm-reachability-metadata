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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderLogManagerTest {

    @Test
    void readsConfigurationFromContextUrlClassLoader() throws Exception {
        URL configuration = ClassLoaderLogManagerTest.class.getResource("/classloader-logging.properties");
        assertThat(configuration).isNotNull();

        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        try (URLClassLoader classLoader = new LoggingConfigurationClassLoader(configuration)) {
            thread.setContextClassLoader(classLoader);
            manager.readConfiguration();

            assertThat(manager.getProperty("integration.logger.level")).isEqualTo("FINE");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
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

    private static final class LoggingConfigurationClassLoader extends URLClassLoader {
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
}
