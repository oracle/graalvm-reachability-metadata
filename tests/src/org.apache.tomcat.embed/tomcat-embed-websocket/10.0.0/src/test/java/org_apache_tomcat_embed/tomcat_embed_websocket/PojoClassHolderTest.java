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
import jakarta.websocket.DeploymentException;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class PojoClassHolderTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void connectToServerCreatesAnnotatedPojoWithPublicNoArgConstructor() {
        CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();

        assertThatThrownBy(() -> container.connectToServer(CountingPojoEndpoint.class,
                URI.create("http://example.invalid/socket"))).isInstanceOf(DeploymentException.class);

        assertThat(CONSTRUCTIONS).hasValue(1);
    }

    @ClientEndpoint
    public static class CountingPojoEndpoint {
        public CountingPojoEndpoint() {
            CONSTRUCTIONS.incrementAndGet();
        }
    }
}
