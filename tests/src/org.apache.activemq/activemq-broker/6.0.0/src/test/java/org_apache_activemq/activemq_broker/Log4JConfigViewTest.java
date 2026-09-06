/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.Log4JConfigView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Log4JConfigViewTest {

    @Test
    void managesLog4jLevelsAndListsConfiguredLoggers() throws Exception {
        Log4JConfigView view = new Log4JConfigView();
        String originalRootLevel = view.getRootLogLevel();

        try {
            view.setRootLogLevel("INFO");
            view.setLogLevel("activemq.dynamic.access", "WARN");

            assertThat(view.getRootLogLevel()).isEqualTo("INFO");
            assertThat(view.getLogLevel("activemq.dynamic.access")).isEqualTo("WARN");
            assertThat(view.getLoggers()).contains("activemq.dynamic.access");
        } finally {
            view.setRootLogLevel(originalRootLevel);
        }
    }
}
