/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class WsSciTest {
    @TempDir
    Path directory;
    private static final AtomicInteger CONFIGURATIONS = new AtomicInteger();

    @Test
    void discoversApplicationConfigAndPublishesOnlySelectedEndpoints() throws Exception {
        CONFIGURATIONS.set(0);
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory,
                Set.of(Application.class, SelectedEndpoint.class, ExcludedEndpoint.class))) {
            assertThat(CONFIGURATIONS.get()).isEqualTo(1);
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            Session session = server.track(server.client.connectToServer(endpoint,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/selected")));
            session.getBasicRemote().sendText("chosen");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("selected:chosen");
            assertThrows(DeploymentException.class, () -> server.client.connectToServer(
                    new EmbeddedWebSocketServer.TextReceiver(), ClientEndpointConfig.Builder.create().build(),
                    server.uri("/excluded")));
        }
    }

    public static class Application implements ServerApplicationConfig {
        public Application() {
            CONFIGURATIONS.incrementAndGet();
        }

        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpoints) {
            return Set.of();
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return scanned.contains(SelectedEndpoint.class) ? Set.of(SelectedEndpoint.class) : Set.of();
        }
    }

    @ServerEndpoint("/selected")
    public static class SelectedEndpoint {
        @OnMessage
        public String message(String text) {
            return "selected:" + text;
        }
    }

    @ServerEndpoint("/excluded")
    public static class ExcludedEndpoint {
        @OnMessage
        public String message(String text) {
            return "excluded:" + text;
        }
    }
}
