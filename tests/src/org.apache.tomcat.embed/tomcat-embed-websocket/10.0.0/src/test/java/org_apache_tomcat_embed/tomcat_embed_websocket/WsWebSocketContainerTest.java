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

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsWebSocketContainerTest {
    private static final AtomicInteger CONFIGURATOR_CONSTRUCTIONS = new AtomicInteger();

    @Test
    void annotatedEndpointConnectionInstantiatesCustomConfigurator() {
        CONFIGURATOR_CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(AnnotatedClientEndpoint.class,
                URI.create("http://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(CONFIGURATOR_CONSTRUCTIONS).hasValue(1);
    }

    @ClientEndpoint(configurator = CountingConfigurator.class)
    public static class AnnotatedClientEndpoint {
    }

    public static class CountingConfigurator extends ClientEndpointConfig.Configurator {
        public CountingConfigurator() {
            CONFIGURATOR_CONSTRUCTIONS.incrementAndGet();
        }
    }
}
