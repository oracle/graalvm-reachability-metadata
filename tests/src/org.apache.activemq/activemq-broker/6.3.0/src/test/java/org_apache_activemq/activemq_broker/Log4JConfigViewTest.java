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

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class Log4JConfigViewTest {

    @Test
    void managesLoggerLevelsAndReloadsConfiguration() throws Throwable {
        Log4JConfigView view = new Log4JConfigView();
        String logger = "reachability.broker.management";
        String originalRootLevel = view.getRootLogLevel();
        String originalLoggerLevel = view.getLogLevel(logger);
        try {
            assertThat(Log4JConfigView.isLog4JAvailable()).isTrue();
            assertThat(view.getLoggers()).contains(logger);
            view.setRootLogLevel("warn");
            assertThat(view.getRootLogLevel()).isEqualTo("WARN");
            view.setLogLevel(logger, "debug");
            assertThat(view.getLogLevel(logger)).isEqualTo("DEBUG");
            view.reloadLog4jProperties();
            assertThat(view.getRootLogLevel()).isEqualTo("ERROR");
            assertThat(view.getLogLevel(logger)).isEqualTo("INFO");
            assertThat(view.getLoggers()).contains(logger);
        } finally {
            view.setRootLogLevel(originalRootLevel);
            view.setLogLevel(logger, originalLoggerLevel);
        }
    }
}
