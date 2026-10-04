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
    void reportsUnavailableOptionalLog4jConfiguration() throws Exception {
        Log4JConfigView view = new Log4JConfigView();

        assertThat(Log4JConfigView.isLog4JAvailable()).isFalse();
        assertThat(view.getRootLogLevel()).isNull();
        assertThat(view.getLoggers()).isEmpty();
        assertThat(view.getLogLevel("org.apache.activemq.metadata.test")).isNull();
    }
}
