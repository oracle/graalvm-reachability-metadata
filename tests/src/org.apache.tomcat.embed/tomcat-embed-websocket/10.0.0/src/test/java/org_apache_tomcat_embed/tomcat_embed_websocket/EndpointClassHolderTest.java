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

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class EndpointClassHolderTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void connectToServerCreatesEndpointWithPublicNoArgConstructor() {
        CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();

        assertThatThrownBy(() -> container.connectToServer(CountingEndpoint.class, config,
                URI.create("http://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(CONSTRUCTIONS).hasValue(1);
    }

    public static class CountingEndpoint extends Endpoint {
        public CountingEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }
}
