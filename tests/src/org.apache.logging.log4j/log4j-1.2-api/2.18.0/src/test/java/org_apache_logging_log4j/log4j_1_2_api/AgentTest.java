/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import com.sun.jdmk.comm.HtmlAdapterServer;
import org.apache.log4j.jmx.Agent;
import org.junit.jupiter.api.Test;

public class AgentTest {

    @Test
    void startsHtmlAdapterServerThroughAgent() {
        List<MBeanServer> existingServers = MBeanServerFactory.findMBeanServer(null);
        HtmlAdapterServer.reset();

        try {
            new Agent().start();

            assertThat(HtmlAdapterServer.wasStarted()).isTrue();
        } finally {
            releaseServersCreatedAfter(existingServers);
        }
    }

    private static void releaseServersCreatedAfter(List<MBeanServer> existingServers) {
        List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
        for (MBeanServer server : servers) {
            if (!existingServers.contains(server)) {
                MBeanServerFactory.releaseMBeanServer(server);
            }
        }
    }
}
