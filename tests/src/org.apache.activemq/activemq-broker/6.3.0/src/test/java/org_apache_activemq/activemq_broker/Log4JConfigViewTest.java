/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.Log4JConfigView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 50, unit = TimeUnit.SECONDS)
public class Log4JConfigViewTest {

    @Test
    void managesLog4jLevelsAndReloadsConfiguration() throws Throwable {
        URL configuration = Log4JConfigViewTest.class.getResource("/log4j2.properties");
        assertThat(configuration).isNotNull();
        String originalConfiguration = System.setProperty("log4j2.configurationFile", configuration.toExternalForm());
        Log4JConfigView view = new Log4JConfigView();
        String loggerName = "org.apache.activemq.metadata.test";

        assertThat(Log4JConfigView.isLog4JAvailable()).isTrue();
        String originalRootLevel = view.getRootLogLevel();
        assertThat(originalRootLevel).isNotBlank();

        try {
            view.setRootLogLevel("DEBUG");
            assertThat(view.getRootLogLevel()).isEqualTo("DEBUG");

            view.setLogLevel(loggerName, "TRACE");
            assertThat(view.getLogLevel(loggerName)).isEqualTo("TRACE");
            assertThat(view.getLoggers()).contains(loggerName);

            view.reloadLog4jProperties();
            assertThat(view.getRootLogLevel()).isNotBlank();
        } finally {
            view.setRootLogLevel(originalRootLevel);
            if (originalConfiguration == null) {
                System.clearProperty("log4j2.configurationFile");
            } else {
                System.setProperty("log4j2.configurationFile", originalConfiguration);
            }
        }
    }
}
