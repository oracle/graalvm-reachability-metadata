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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.impl.SimpleLogger;
import org.slf4j.impl.SimpleLoggerFactory;

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
            Thread.currentThread().setContextClassLoader(new PropertiesClassLoader(originalContextClassLoader));
            Logger configuredLogger = new SimpleLoggerFactory().getLogger("simple.logger.configuration.context");
            assertThat(configuredLogger.isTraceEnabled()).isTrue();

            resetSimpleLoggerInitialization();
            Thread.currentThread().setContextClassLoader(null);
            Logger defaultLogger = new SimpleLoggerFactory().getLogger("simple.logger.configuration.system");
            assertThat(defaultLogger.isInfoEnabled()).isTrue();
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

        Field configParams = SimpleLogger.class.getDeclaredField("CONFIG_PARAMS");
        configParams.setAccessible(true);
        configParams.set(null, null);
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
