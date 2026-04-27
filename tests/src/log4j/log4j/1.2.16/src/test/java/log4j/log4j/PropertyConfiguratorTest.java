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

    @BeforeEach
    void setUp() {
        LogManager.resetConfiguration();
        TrackingLoggerFactory.reset();
    }

    @AfterEach
    void tearDown() {
        LogManager.resetConfiguration();
        TrackingLoggerFactory.reset();
    }

    @Test
    void configuresLoggerFactoryFromPropertiesInFreshClassLoader() throws Exception {
        String loggerName = PropertyConfiguratorTest.class.getName() + "." + System.nanoTime();

        try (URLClassLoader isolatedLoader = new URLClassLoader(isolatedClassPath(), ClassLoader.getPlatformClassLoader())) {
            Class<?> propertyConfiguratorClass = Class.forName("org.apache.log4j.PropertyConfigurator", true, isolatedLoader);
            Class<?> trackingLoggerFactoryClass = Class.forName(TrackingLoggerFactory.class.getName(), true, isolatedLoader);
            Class<?> trackingLoggerClass = Class.forName(TrackingLogger.class.getName(), true, isolatedLoader);
            Class<?> logManagerClass = Class.forName("org.apache.log4j.LogManager", true, isolatedLoader);

            Properties properties = new Properties();
            properties.setProperty("log4j.rootLogger", "ERROR");
            properties.setProperty("log4j.loggerFactory", trackingLoggerFactoryClass.getName());
            properties.setProperty("log4j.factory.tag", "configured-by-property-configurator");
            properties.setProperty("log4j.logger." + loggerName, "INFO");
            properties.setProperty("log4j.additivity." + loggerName, "false");

            Method configureMethod = propertyConfiguratorClass.getMethod("configure", Properties.class);
            configureMethod.invoke(null, properties);

            Object lastFactoryInstance = invokeStatic(trackingLoggerFactoryClass, "getLastInstance");
            assertThat(lastFactoryInstance).isNotNull();
            assertThat(invoke(lastFactoryInstance, "getTag")).isEqualTo("configured-by-property-configurator");
            assertThat(invokeStatic(trackingLoggerFactoryClass, "getMakeNewLoggerInstanceCallCount")).isEqualTo(1);
            assertThat(invokeStatic(trackingLoggerFactoryClass, "getLastLoggerName")).isEqualTo(loggerName);

            Object configuredLogger = logManagerClass.getMethod("getLogger", String.class).invoke(null, loggerName);
            assertThat(trackingLoggerClass.isInstance(configuredLogger)).isTrue();
            assertThat(invoke(configuredLogger, "getName")).isEqualTo(loggerName);
            assertThat(String.valueOf(invoke(configuredLogger, "getLevel"))).isEqualTo("INFO");
            assertThat(invoke(configuredLogger, "getAdditivity")).isEqualTo(false);
            assertThat(invoke(configuredLogger, "getFactoryTag")).isEqualTo("configured-by-property-configurator");
        }
    }

    private static URL[] isolatedClassPath() {
        URL testClassesUrl = codeSourceUrl(PropertyConfiguratorTest.class);
        URL libraryClassesUrl = codeSourceUrl(org.apache.log4j.PropertyConfigurator.class);
        if (testClassesUrl.equals(libraryClassesUrl)) {
            return new URL[] { testClassesUrl };
        }
        return new URL[] { testClassesUrl, libraryClassesUrl };
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static Object invokeStatic(Class<?> type, String methodName) throws Exception {
        Method method = type.getMethod(methodName);
        return method.invoke(null);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    public static final class TrackingLoggerFactory implements LoggerFactory {
        private static TrackingLoggerFactory lastInstance;
        private static int makeNewLoggerInstanceCallCount;
        private static String lastLoggerName;

        private String tag;

        public TrackingLoggerFactory() {
            lastInstance = this;
        }

        @Override
        public Logger makeNewLoggerInstance(String name) {
            makeNewLoggerInstanceCallCount++;
            lastLoggerName = name;
            return new TrackingLogger(name, tag);
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public static TrackingLoggerFactory getLastInstance() {
            return lastInstance;
        }

        public static int getMakeNewLoggerInstanceCallCount() {
            return makeNewLoggerInstanceCallCount;
        }

        public static String getLastLoggerName() {
            return lastLoggerName;
        }

        private static void reset() {
            lastInstance = null;
            makeNewLoggerInstanceCallCount = 0;
            lastLoggerName = null;
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
