/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class ContainerProviderTest {
    @TempDir
    Path directory;

    @Test
    void createsContainerWithoutApplicationServicesInContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        WebSocketContainer container;
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        try {
            container = ContainerProvider.getWebSocketContainer();
        } finally {
            thread.setContextClassLoader(previous);
        }
        assertThat(container).isInstanceOf(WsWebSocketContainer.class);
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            container.setDefaultMaxSessionIdleTimeout(30000);
            try (Session session = container.connectToServer(endpoint,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/echo"))) {
                session.getBasicRemote().sendText("context independent container");
                assertThat(EmbeddedWebSocketServer.receive(endpoint.messages))
                        .isEqualTo("context independent container");
            }
        } finally {
            ((WsWebSocketContainer) container).destroy();
        }
    }

    @Test
    void discoversContainerAndExchangesMessages() throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        assertThat(container).isInstanceOf(WsWebSocketContainer.class);
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            container.setDefaultMaxSessionIdleTimeout(30000);
            try (Session session = container.connectToServer(endpoint,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/echo"))) {
                session.getBasicRemote().sendText("discovered container");
                assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("discovered container");
            }
        } finally {
            ((WsWebSocketContainer) container).destroy();
        }
    }
}
