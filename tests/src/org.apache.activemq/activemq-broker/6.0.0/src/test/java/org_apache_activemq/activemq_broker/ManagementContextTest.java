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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 50, unit = TimeUnit.SECONDS)
public class ManagementContextTest {

    @Test
    void createsMBeanServer() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(false);

        try {
            MBeanServer server = context.getMBeanServer();

            assertThat(server).isNotNull();
        } finally {
            context.stop();
        }
    }

    @Test
    void startsRemoteJmxConnector() throws Exception {
        int connectorPort = availablePort();
        ManagementContext context = new ManagementContext();
        context.setConnectorHost("127.0.0.1");
        context.setConnectorPort(connectorPort);
        context.setCreateConnector(true);

        try {
            context.start();
            awaitConnectorStart(context);

            JMXServiceURL serviceUrl = new JMXServiceURL(
                    "service:jmx:rmi:///jndi/rmi://127.0.0.1:" + connectorPort + context.getConnectorPath());
            Map<String, Long> environment = Map.of(
                    "jmx.remote.x.request.waiting.timeout", TimeUnit.SECONDS.toMillis(10));
            try (JMXConnector connector = JMXConnectorFactory.connect(serviceUrl, environment)) {
                assertThat(connector.getMBeanServerConnection().getDefaultDomain())
                        .isEqualTo(context.getMBeanServer().getDefaultDomain());
            }
        } finally {
            context.stop();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static void awaitConnectorStart(ManagementContext context) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!context.isConnectorStarted() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertThat(context.isConnectorStarted()).isTrue();
    }
}
