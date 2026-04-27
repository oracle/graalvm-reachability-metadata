/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
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
    void configuresTheRootLoggerWithTheBundledLf5AppenderWhenTheResourceIsAvailable() throws Exception {
        try {
            DefaultLF5Configurator.configure();
            assertLf5AppenderConfigured();
        } catch (IOException exception) {
            assertThat(exception)
                    .hasMessageContaining("/org/apache/log4j/lf5/config/defaultconfig.properties");
        }
    }

    @Test
    void resolvesItsOwnClassThroughTheSyntheticClassLookup() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(DefaultLF5Configurator.class, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                DefaultLF5Configurator.class,
                "class$",
                MethodType.methodType(Class.class, String.class)
        );

        assertThat((Class<?>) classLookup.invokeExact(DefaultLF5Configurator.class.getName()))
                .isSameAs(DefaultLF5Configurator.class);
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

    private static void assertLf5AppenderConfigured() {
        List<Appender> appenders = Collections.list(LogManager.getRootLogger().getAllAppenders());
        assertThat(appenders)
                .singleElement()
                .isInstanceOfSatisfying(LF5Appender.class, appender -> {
                    assertThat(appender.getName()).isEqualTo("A1");
                    assertThat(appender.getLogBrokerMonitor()).isNotNull();
                });
    }
}
