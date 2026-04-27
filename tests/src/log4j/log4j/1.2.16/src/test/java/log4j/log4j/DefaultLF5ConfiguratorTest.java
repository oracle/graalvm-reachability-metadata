/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

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
}
