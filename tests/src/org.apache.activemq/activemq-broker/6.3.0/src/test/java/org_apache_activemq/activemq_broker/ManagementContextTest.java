/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.jmx.ManagementContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class ManagementContextTest {

    @Test
    void discoversThePlatformManagementServer() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(false);
        try {
            context.start();
            MBeanServer server = context.findTigerMBeanServer();
            assertThat(server).isSameAs(ManagementFactory.getPlatformMBeanServer());
            assertThat(server.isRegistered(new ObjectName("java.lang:type=Runtime"))).isTrue();
        } finally {
            context.stop();
        }
    }

    @Test
    void exposesManagementAttributesOverRemoteConnector() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(true);
        context.setConnectorHost("127.0.0.1");
        int port = availablePort();
        context.setConnectorPort(port);
        try {
            context.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!context.isConnectorStarted() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(context.isConnectorStarted()).isTrue();
            JMXServiceURL url = new JMXServiceURL(
                    "service:jmx:rmi:///jndi/rmi://127.0.0.1:" + port + "/jmxrmi");
            try (JMXConnector connector = JMXConnectorFactory.newJMXConnector(url, null)) {
                connector.connect();
                Object name = connector.getMBeanServerConnection().getAttribute(
                        new ObjectName("java.lang:type=Runtime"), "Name");
                assertThat(name).isEqualTo(ManagementFactory.getRuntimeMXBean().getName());
            }
        } finally {
            context.stop();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
