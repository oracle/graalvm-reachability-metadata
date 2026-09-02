/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.broker.jmx.Log4JConfigView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

public class Log4JConfigViewTest {

    @Test
    @ResourceLock("log4j-configuration")
    void readsAndUpdatesLog4jConfiguration() throws Exception {
        Log4JConfigView view = new Log4JConfigView();
        String loggerName = Log4JConfigViewTest.class.getName() + ".managed";
        String originalRootLevel = view.getRootLogLevel();

        assertThat(Log4JConfigView.isLog4JAvailable()).isTrue();
        assertThat(originalRootLevel).isNotBlank();

        try {
            view.setRootLogLevel("WARN");
            view.setLogLevel(loggerName, "DEBUG");

            assertThat(view.getRootLogLevel()).isEqualTo("WARN");
            assertThat(view.getLogLevel(loggerName)).isEqualTo("DEBUG");
            assertThat(view.getLoggers()).contains(loggerName);
        } finally {
            view.setRootLogLevel(originalRootLevel);
        }
    }
}
