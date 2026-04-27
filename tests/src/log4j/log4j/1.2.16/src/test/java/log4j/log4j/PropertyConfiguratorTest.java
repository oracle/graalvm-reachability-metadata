/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Properties;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyConfiguratorTest {
    private static final String FACTORY_CONSTRUCTED_PROPERTY = "propertyConfiguratorTest.factoryConstructed";
    private static final String FACTORY_TAG_PROPERTY = "propertyConfiguratorTest.factoryTag";
    private static final String FACTORY_LOGGER_NAME_PROPERTY = "propertyConfiguratorTest.factoryLoggerName";
    private static final String FACTORY_CALL_COUNT_PROPERTY = "propertyConfiguratorTest.factoryCallCount";

    @BeforeEach
    void setUp() {
        LogManager.resetConfiguration();
        clearTrackingProperties();
    }

    @AfterEach
    void tearDown() {
        LogManager.resetConfiguration();
        clearTrackingProperties();
    }

    @Test
    void configuresLoggerFactoryFromPropertiesInFreshClassLoader() throws Exception {
        String loggerName = PropertyConfiguratorTest.class.getName() + "." + System.nanoTime();

        try (URLClassLoader isolatedLoader = new URLClassLoader(isolatedClassPath(), ClassLoader.getPlatformClassLoader())) {
            Thread thread = Thread.currentThread();
            ClassLoader previousContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(isolatedLoader);

            try {
                Class<?> propertyConfiguratorClass = Class.forName("org.apache.log4j.PropertyConfigurator", true, isolatedLoader);
                Class<?> logManagerClass = Class.forName("org.apache.log4j.LogManager", true, isolatedLoader);

                Properties properties = new Properties();
                properties.setProperty("log4j.rootLogger", "ERROR");
                properties.setProperty("log4j.loggerFactory", TrackingLoggerFactory.class.getName());
                properties.setProperty("log4j.factory.tag", "configured-by-property-configurator");
                properties.setProperty("log4j.logger." + loggerName, "INFO");
                properties.setProperty("log4j.additivity." + loggerName, "false");

                Method configureMethod = propertyConfiguratorClass.getMethod("configure", Properties.class);
                configureMethod.invoke(null, properties);

                assertThat(System.getProperty(FACTORY_CONSTRUCTED_PROPERTY)).isEqualTo(Boolean.TRUE.toString());
                assertThat(System.getProperty(FACTORY_TAG_PROPERTY)).isEqualTo("configured-by-property-configurator");
                assertThat(System.getProperty(FACTORY_CALL_COUNT_PROPERTY)).isEqualTo("1");
                assertThat(System.getProperty(FACTORY_LOGGER_NAME_PROPERTY)).isEqualTo(loggerName);

                Object configuredLogger = logManagerClass.getMethod("getLogger", String.class).invoke(null, loggerName);
                assertThat(configuredLogger.getClass().getName()).isEqualTo(TrackingLogger.class.getName());
                assertThat(invoke(configuredLogger, "getName")).isEqualTo(loggerName);
                assertThat(String.valueOf(invoke(configuredLogger, "getLevel"))).isEqualTo("INFO");
                assertThat(invoke(configuredLogger, "getAdditivity")).isEqualTo(false);
                assertThat(invoke(configuredLogger, "getFactoryTag")).isEqualTo("configured-by-property-configurator");
            } finally {
                thread.setContextClassLoader(previousContextClassLoader);
            }
        }
    }

    private static URL[] isolatedClassPath() {
        URL testClassesUrl = codeSourceUrl(PropertyConfiguratorTest.class);
        URL libraryClassesUrl = codeSourceUrl(org.apache.log4j.PropertyConfigurator.class);
        if (testClassesUrl.equals(libraryClassesUrl)) {
            return new URL[]{testClassesUrl};
        }
        return new URL[]{testClassesUrl, libraryClassesUrl};
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static void clearTrackingProperties() {
        System.clearProperty(FACTORY_CONSTRUCTED_PROPERTY);
        System.clearProperty(FACTORY_TAG_PROPERTY);
        System.clearProperty(FACTORY_LOGGER_NAME_PROPERTY);
        System.clearProperty(FACTORY_CALL_COUNT_PROPERTY);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    public static final class TrackingLoggerFactory implements LoggerFactory {
        private String tag;

        public TrackingLoggerFactory() {
            System.setProperty(FACTORY_CONSTRUCTED_PROPERTY, Boolean.TRUE.toString());
        }

        @Override
        public Logger makeNewLoggerInstance(String name) {
            System.setProperty(FACTORY_LOGGER_NAME_PROPERTY, name);
            System.setProperty(FACTORY_CALL_COUNT_PROPERTY, Integer.toString(currentCallCount() + 1));
            return new TrackingLogger(name, tag);
        }

        public void setTag(String tag) {
            this.tag = tag;
            System.setProperty(FACTORY_TAG_PROPERTY, tag);
        }

        private static int currentCallCount() {
            return Integer.parseInt(System.getProperty(FACTORY_CALL_COUNT_PROPERTY, "0"));
        }
    }

    public static final class TrackingLogger extends Logger {
        private final String factoryTag;

        private TrackingLogger(String name, String factoryTag) {
            super(name);
            this.factoryTag = factoryTag;
        }

        public String getFactoryTag() {
            return factoryTag;
        }
    }
}
