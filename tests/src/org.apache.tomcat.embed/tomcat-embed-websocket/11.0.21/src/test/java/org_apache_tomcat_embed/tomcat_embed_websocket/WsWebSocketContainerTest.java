/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class WsWebSocketContainerTest {
    @TempDir
    Path directory;
    private static final BlockingQueue<HandshakeResponse> HANDSHAKES = new LinkedBlockingQueue<>();

    @Test
    void constructsAnnotationSelectedConfiguratorForHandshake() throws Exception {
        HANDSHAKES.clear();
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            ConfiguredClient endpoint = new ConfiguredClient();
            Session session = server.track(server.client.connectToServer(endpoint, server.uri("/echo")));
            assertThat(EmbeddedWebSocketServer.receive(HANDSHAKES).getHeaders().keySet())
                    .anyMatch(name -> name.equalsIgnoreCase("Upgrade"));
            session.getBasicRemote().sendText("configured handshake");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("configured handshake");
        }
    }

    @Test
    void rejectsNonWebSocketSchemeWithLocalizedDiagnostic() {
        WsWebSocketContainer container = new WsWebSocketContainer();
        try {
            DeploymentException exception = assertThrows(DeploymentException.class,
                    () -> container.connectToServer(new ConfiguredClient(), URI.create("http://localhost/echo")));
            assertThat(exception).hasMessageContaining("http").hasMessageContaining("wss");
        } finally {
            container.destroy();
        }
    }

    @ClientEndpoint(configurator = HandshakeConfigurator.class)
    public static class ConfiguredClient {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void message(String text) {
            messages.add(text);
        }
    }

    public static class HandshakeConfigurator extends ClientEndpointConfig.Configurator {
        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("X-Client", List.of("configured"));
        }

        @Override
        public void afterResponse(HandshakeResponse response) {
            HANDSHAKES.add(response);
        }
    }
}
