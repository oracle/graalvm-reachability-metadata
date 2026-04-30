/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.slf4j_simple;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.simple.SimpleLogger;
import org.slf4j.simple.SimpleLoggerConfiguration;
import org.slf4j.simple.SimpleLoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLoggerConfigurationAnonymous1Test {

    private static final String CONFIGURATION_FILE = "simplelogger.properties";
    private static final String TRACE_CONFIGURATION = SimpleLogger.DEFAULT_LOG_LEVEL_KEY + "=trace\n";

    @Test
    void loadsConfigurationUsingContextAndSystemClassLoaders() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        String originalDefaultLevel = System.getProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY);
        try {
            System.clearProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY);

            resetSimpleLoggerInitialization();
            Thread.currentThread().setContextClassLoader(null);
            Logger defaultLogger = new SimpleLoggerFactory().getLogger("simple.logger.configuration.system");
            assertThat(defaultLogger.isInfoEnabled()).isTrue();
            assertThat(defaultLogger.isTraceEnabled()).isFalse();

            resetSimpleLoggerInitialization();
            Thread.currentThread().setContextClassLoader(new PropertiesClassLoader(originalContextClassLoader));
            Logger configuredLogger = new SimpleLoggerFactory().getLogger("simple.logger.configuration.context");
            assertThat(configuredLogger.isTraceEnabled()).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            restoreSystemProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, originalDefaultLevel);
            resetSimpleLoggerInitialization();
        }
    }

    private static void resetSimpleLoggerInitialization() throws Exception {
        Field initialized = SimpleLogger.class.getDeclaredField("INITIALIZED");
        initialized.setAccessible(true);
        initialized.setBoolean(null, false);

        SimpleLoggerConfiguration configParams = getConfigParams();
        if (configParams != null) {
            clearConfigurationProperties(configParams);
            resetDefaultLogLevel(configParams);
        }
    }

    private static SimpleLoggerConfiguration getConfigParams() throws Exception {
        Field configParams = SimpleLogger.class.getDeclaredField("CONFIG_PARAMS");
        configParams.setAccessible(true);
        return (SimpleLoggerConfiguration) configParams.get(null);
    }

    private static void clearConfigurationProperties(SimpleLoggerConfiguration configParams) throws Exception {
        Field properties = SimpleLoggerConfiguration.class.getDeclaredField("properties");
        properties.setAccessible(true);
        ((Properties) properties.get(configParams)).clear();
    }

    private static void resetDefaultLogLevel(SimpleLoggerConfiguration configParams) throws Exception {
        Field defaultLogLevelDefault = SimpleLoggerConfiguration.class.getDeclaredField("DEFAULT_LOG_LEVEL_DEFAULT");
        defaultLogLevelDefault.setAccessible(true);

        Field defaultLogLevel = SimpleLoggerConfiguration.class.getDeclaredField("defaultLogLevel");
        defaultLogLevel.setAccessible(true);
        defaultLogLevel.setInt(configParams, defaultLogLevelDefault.getInt(null));
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class PropertiesClassLoader extends ClassLoader {
        PropertiesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (CONFIGURATION_FILE.equals(name)) {
                return new ByteArrayInputStream(TRACE_CONFIGURATION.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }
    }
}
