/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.shade.io.netty.channel.ReflectiveChannelFactory;
import org.apache.pulsar.shade.io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

public class ReflectiveChannelFactoryTest {
    @Test
    void createsUsableChannelFromPublicNoArgConstructor() {
        ReflectiveChannelFactory<EmbeddedChannel> factory = new ReflectiveChannelFactory<>(EmbeddedChannel.class);
        EmbeddedChannel channel = factory.newChannel();
        try {
            assertThat(channel.isOpen()).isTrue();
            assertThat(channel.parent()).isNull();
            assertThat(channel.config()).isNotNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void opensBrokerConnectionThroughReflectivelyCreatedClientChannel() throws Exception {
        try (ServerSocket broker = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            broker.setSoTimeout(10_000);
            String serviceUrl = "pulsar://" + broker.getInetAddress().getHostAddress() + ":" + broker.getLocalPort();
            try (PulsarClient client = PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .operationTimeout(5, TimeUnit.SECONDS)
                    .build()) {
                client.newProducer().topic("persistent://public/default/channel-factory").createAsync();
                try (Socket connection = broker.accept()) {
                    assertThat(connection.isConnected()).isTrue();
                }
            }
        }
    }
}
