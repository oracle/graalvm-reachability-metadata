/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;

import javax.management.MBeanServer;

import org.apache.activemq.broker.jmx.ManagementContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ManagementContextTest {

    @Test
    void locatesPlatformMBeanServer() {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(false);

        MBeanServer mBeanServer = context.findTigerMBeanServer();

        assertThat(mBeanServer).isSameAs(ManagementFactory.getPlatformMBeanServer());
    }

    @Test
    @Timeout(30)
    void createsAndClosesLocalJmxRegistry() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setConnectorHost("127.0.0.1");
        context.setConnectorPort(availablePort());
        context.setCreateConnector(true);

        try {
            MBeanServer mBeanServer = context.getMBeanServer();

            assertThat(mBeanServer).isSameAs(ManagementFactory.getPlatformMBeanServer());
            assertThat(context.isCreateConnector()).isTrue();
        } finally {
            context.stop();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }
}
