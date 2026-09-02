/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.activemq.broker.jmx.Log4JConfigView;
import org.junit.jupiter.api.Test;

public class Log4JConfigViewTest {

    @Test
    void managesRootAndNamedLog4jLevels() throws Exception {
        Log4JConfigView view = new Log4JConfigView();
        String originalRootLevel = view.getRootLogLevel();
        String loggerName = Log4JConfigViewTest.class.getName() + ".managed";

        assertThat(Log4JConfigView.isLog4JAvailable()).isTrue();
        assertThat(originalRootLevel).isNotBlank();

        try {
            view.setRootLogLevel("WARN");
            view.setLogLevel(loggerName, "DEBUG");

            assertThat(view.getRootLogLevel()).isEqualTo("WARN");
            assertThat(view.getLogLevel(loggerName)).isEqualTo("DEBUG");
            List<String> loggers = view.getLoggers();
            assertThat(loggers).contains(loggerName);
        } finally {
            view.setRootLogLevel(originalRootLevel);
        }
    }
}
