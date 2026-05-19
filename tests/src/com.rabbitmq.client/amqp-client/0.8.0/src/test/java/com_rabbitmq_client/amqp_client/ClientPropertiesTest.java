/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import com.rabbitmq.client.amqp.AmqpException;
import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClientPropertiesTest {

    @Test
    void connectionAttemptInitializesDefaultClientProperties() throws IOException {
        int unavailablePort = unavailableLocalPort();

        try (Environment environment = new AmqpEnvironmentBuilder().build()) {
            assertThatThrownBy(() -> environment.connectionBuilder()
                    .host(InetAddress.getLoopbackAddress().getHostAddress())
                    .port(unavailablePort)
                    .recovery().activated(false)
                    .connectionBuilder()
                    .build())
                    .isInstanceOf(AmqpException.class);
        }
    }

    private static int unavailableLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
