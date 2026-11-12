/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class WsRemoteEndpointImplBaseTest {
    @TempDir
    Path directory;

    @Test
    void constructsAndInitializesEncoderForObjectMessages() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .encoders(List.of(OrderEncoder.class)).build();
            config.getUserProperties().put("prefix", "order:");
            Session session = server.track(server.client.connectToServer(endpoint, config, server.uri("/echo")));
            session.getBasicRemote().sendObject(new Order(17));
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("order:17");
        }
    }

    public record Order(int id) {
    }

    public static class OrderEncoder implements Encoder.Text<Order> {
        private String prefix;

        @Override
        public void init(EndpointConfig config) {
            prefix = (String) config.getUserProperties().get("prefix");
        }

        @Override
        public String encode(Order order) {
            return prefix + order.id();
        }
    }
}
