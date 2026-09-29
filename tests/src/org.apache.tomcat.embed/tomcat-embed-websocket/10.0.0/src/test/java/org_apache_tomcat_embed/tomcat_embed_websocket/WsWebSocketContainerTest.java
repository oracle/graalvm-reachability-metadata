/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsWebSocketContainerTest {
    private static final AtomicInteger CONFIGURATOR_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger ENDPOINT_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS = new AtomicInteger();

    @Test
    void annotatedEndpointConnectionInstantiatesCustomConfigurator() {
        CONFIGURATOR_CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(AnnotatedClientEndpoint.class,
                URI.create("http://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(CONFIGURATOR_CONSTRUCTIONS).hasValue(1);
    }

    @Test
    void annotatedEndpointClassConnectionInvokesPublicConstructor() {
        ENDPOINT_CONSTRUCTIONS.set(0);
        WebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(ConstructingClientEndpoint.class,
                URI.create("ftp://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(ENDPOINT_CONSTRUCTIONS).hasValue(1);
    }

    @Test
    void endpointClassConnectionInvokesPublicConstructor() {
        PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.set(0);
        WebSocketContainer container = new WsWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        assertThatThrownBy(() -> container.connectToServer(ConstructingEndpoint.class, config,
                URI.create("ftp://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS).hasValue(1);
    }

    @ClientEndpoint(configurator = CountingConfigurator.class)
    public static class AnnotatedClientEndpoint {
    }

    @ClientEndpoint
    public static class ConstructingClientEndpoint {
        public ConstructingClientEndpoint() {
            ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }
    }

    public static class ConstructingEndpoint extends Endpoint {
        public ConstructingEndpoint() {
            PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public static class CountingConfigurator extends ClientEndpointConfig.Configurator {
        public CountingConfigurator() {
            CONFIGURATOR_CONSTRUCTIONS.incrementAndGet();
        }
    }
}
