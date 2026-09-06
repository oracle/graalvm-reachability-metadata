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

    private static final String LOGGER_NAME = "org.apache.activemq.metadata.test";

    @Test
    void managesRootAndNamedLoggerLevels() throws Exception {
        Log4JConfigView view = new Log4JConfigView();
        String originalRootLevel = view.getRootLogLevel();

        try {
            view.setRootLogLevel("WARN");
            view.setLogLevel(LOGGER_NAME, "DEBUG");

            assertThat(view.getRootLogLevel()).isEqualTo("WARN");
            assertThat(view.getLogLevel(LOGGER_NAME)).isEqualTo("DEBUG");
            assertThat(view.getLoggers()).contains(LOGGER_NAME);
        } finally {
            view.setRootLogLevel(originalRootLevel);
        }
    }
}
