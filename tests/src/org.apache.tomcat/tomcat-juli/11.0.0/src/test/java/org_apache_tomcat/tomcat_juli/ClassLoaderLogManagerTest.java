/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_juli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.juli.ClassLoaderLogManager;
import org.apache.juli.WebappProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ClassLoaderLogManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsUrlClassLoaderConfigurationAndCreatesConfiguredHandler() throws Exception {
        Path loggingProperties = temporaryDirectory.resolve("logging.properties");
        Files.writeString(loggingProperties, loggingProperties(), StandardCharsets.UTF_8);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] urls = {temporaryDirectory.toUri().toURL()};
        try (URLClassLoader configurationClassLoader = new URLClassLoader(urls, getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(configurationClassLoader);
            ClassLoaderLogManager logManager = newLogManager();
            try {
                logManager.readConfiguration();

                Logger rootLogger = logManager.getLogger("");
                RecordingHandler handler = findRecordingHandler(rootLogger);
                rootLogger.info("message from URL class loader configuration");

                assertThat(handler.getPublishedRecords()).isEqualTo(1);
            } finally {
                logManager.shutdown();
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    @Test
    void readsWebappClassLoaderConfigurationResource() throws Exception {
        Path loggingProperties = temporaryDirectory.resolve("logging.properties");
        Files.writeString(loggingProperties, webappLoggingProperties(), StandardCharsets.UTF_8);

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] urls = {temporaryDirectory.toUri().toURL()};
        try (WebappLoggingClassLoader webappClassLoader = new WebappLoggingClassLoader(urls,
                getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(webappClassLoader);
            ClassLoaderLogManager logManager = newLogManager();
            try {
                logManager.readConfiguration();

                Logger rootLogger = logManager.getLogger("");
                RecordingHandler handler = findRecordingHandler(rootLogger);
                rootLogger.info("message from webapp class loader configuration");

                assertThat(webappClassLoader.wasLoggingConfigurationRequested()).isTrue();
                assertThat(logManager.getProperty("webapp.name")).isEqualTo("coverage-webapp");
                assertThat(handler.getPublishedRecords()).isEqualTo(1);
            } finally {
                logManager.shutdown();
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    private static ClassLoaderLogManager newLogManager() {
        ClassLoaderLogManager logManager = new ClassLoaderLogManager();
        logManager.setUseShutdownHook(false);
        return logManager;
    }

    private static RecordingHandler findRecordingHandler(Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof RecordingHandler recordingHandler) {
                return recordingHandler;
            }
        }
        throw new AssertionError("Expected a RecordingHandler to be configured");
    }

    private static String loggingProperties() {
        return """
                handlers = %s
                .level = FINE
                sample.category.level = FINEST
                """.formatted(RecordingHandler.class.getName());
    }

    private static String webappLoggingProperties() {
        return loggingProperties() + "webapp.name = ${classloader.webappName}\n";
    }

    public static final class RecordingHandler extends Handler {
        private int publishedRecords;

        public RecordingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            publishedRecords++;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        int getPublishedRecords() {
            return publishedRecords;
        }
    }

    public static final class WebappLoggingClassLoader extends URLClassLoader implements WebappProperties {
        private boolean loggingConfigurationRequested;

        WebappLoggingClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if ("logging.properties".equals(name)) {
                loggingConfigurationRequested = true;
            }
            return super.getResourceAsStream(name);
        }

        @Deprecated
        @Override
        public boolean hasLoggingConfig() {
            return true;
        }

        @Override
        public String getWebappName() {
            return "coverage-webapp";
        }

        @Override
        public String getHostName() {
            return "localhost";
        }

        @Override
        public String getServiceName() {
            return "coverage-service";
        }

        boolean wasLoggingConfigurationRequested() {
            return loggingConfigurationRequested;
        }
    }
}
