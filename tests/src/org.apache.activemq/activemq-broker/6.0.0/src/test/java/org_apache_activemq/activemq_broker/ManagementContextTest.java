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
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

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
    void startsConnectorBackedManagementContext() throws Exception {
        ManagementContext context = new ManagementContext();
        context.setCreateConnector(true);
        context.setConnectorHost("127.0.0.1");
        context.setConnectorPort(availablePort());

        try {
            context.start();
            awaitConnectorStart(context);

            assertThat(context.isConnectorStarted()).isTrue();
        } finally {
            context.stop();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitConnectorStart(ManagementContext context) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20L);
        while (!context.isConnectorStarted() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }
}
