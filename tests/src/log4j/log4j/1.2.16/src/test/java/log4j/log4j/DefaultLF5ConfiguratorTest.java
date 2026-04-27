/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.LogManager;
import org.apache.log4j.lf5.DefaultLF5Configurator;
import org.apache.log4j.lf5.LF5Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultLF5ConfiguratorTest {

    @BeforeEach
    void setUp() {
        resetConfiguration();
    }

    @AfterEach
    void tearDown() {
        resetConfiguration();
    }

    @Test
    void configuresTheRootLoggerWithTheBundledLf5Appender() throws Exception {
        DefaultLF5Configurator.configure();

        List<Appender> appenders = Collections.list(LogManager.getRootLogger().getAllAppenders());
        assertThat(appenders)
                .singleElement()
                .isInstanceOfSatisfying(LF5Appender.class, appender -> {
                    assertThat(appender.getName()).isEqualTo("A1");
                    assertThat(appender.getLogBrokerMonitor()).isNotNull();
                });
    }

    @Test
    void configuresTheRootLoggerWhenLoadedInAnIsolatedClassLoader() throws Exception {
        try (URLClassLoader isolatedLoader = new URLClassLoader(
                new URL[] { codeSourceUrl(DefaultLF5Configurator.class) },
                ClassLoader.getPlatformClassLoader())) {
            Class<?> configuratorClass = Class.forName(DefaultLF5Configurator.class.getName(), true, isolatedLoader);
            Class<?> logManagerClass = Class.forName(LogManager.class.getName(), true, isolatedLoader);
            Class<?> categoryClass = Class.forName("org.apache.log4j.Category", true, isolatedLoader);

            try {
                configuratorClass.getMethod("configure").invoke(null);

                List<Object> appenders = getIsolatedRootAppenders(logManagerClass, categoryClass);
                assertThat(appenders)
                        .singleElement()
                        .extracting(appender -> appender.getClass().getName())
                        .isEqualTo(LF5Appender.class.getName());
            } finally {
                disposeIsolatedLf5Monitor(logManagerClass, categoryClass);
                logManagerClass.getMethod("resetConfiguration").invoke(null);
            }
        }
    }

    private static void resetConfiguration() {
        disposeLf5Monitor();
        LogManager.resetConfiguration();
    }

    private static void disposeLf5Monitor() {
        List<Appender> appenders = Collections.list(LogManager.getRootLogger().getAllAppenders());
        for (Appender appender : appenders) {
            if (appender instanceof LF5Appender lf5Appender && lf5Appender.getLogBrokerMonitor() != null) {
                lf5Appender.getLogBrokerMonitor().dispose();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getIsolatedRootAppenders(Class<?> logManagerClass, Class<?> categoryClass) throws Exception {
        Object rootLogger = logManagerClass.getMethod("getRootLogger").invoke(null);
        Enumeration<Object> appenders = (Enumeration<Object>) categoryClass.getMethod("getAllAppenders").invoke(rootLogger);
        return Collections.list(appenders);
    }

    private static void disposeIsolatedLf5Monitor(Class<?> logManagerClass, Class<?> categoryClass) throws Exception {
        for (Object appender : getIsolatedRootAppenders(logManagerClass, categoryClass)) {
            if (LF5Appender.class.getName().equals(appender.getClass().getName())) {
                Object monitor = appender.getClass().getMethod("getLogBrokerMonitor").invoke(appender);
                if (monitor != null) {
                    monitor.getClass().getMethod("dispose").invoke(monitor);
                }
            }
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }
}
