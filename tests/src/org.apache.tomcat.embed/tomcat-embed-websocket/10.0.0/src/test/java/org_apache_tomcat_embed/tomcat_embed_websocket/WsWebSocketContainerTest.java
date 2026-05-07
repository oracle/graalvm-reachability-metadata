/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

public class WsWebSocketContainerTest {
    private static final AtomicInteger CONFIGURATOR_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger ANNOTATED_ENDPOINT_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS = new AtomicInteger();
    private static final AtomicInteger PROXY_SELECTIONS = new AtomicInteger();

    @Test
    void annotatedEndpointConnectionInstantiatesEndpointClassAndCustomConfigurator() {
        CONFIGURATOR_CONSTRUCTIONS.set(0);
        ANNOTATED_ENDPOINT_CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();
        try {
            assertConnectionStopsAtProxySelection(() -> container.connectToServer(AnnotatedClientEndpoint.class,
                    URI.create("ws://example.invalid/socket")));

            assertThat(ANNOTATED_ENDPOINT_CONSTRUCTIONS).hasValue(1);
            assertThat(CONFIGURATOR_CONSTRUCTIONS).hasValue(1);
        } finally {
            container.destroy();
        }
    }

    @Test
    void programmaticEndpointConnectionInstantiatesEndpointClass() {
        PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.set(0);
        WsWebSocketContainer container = new WsWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        try {
            assertConnectionStopsAtProxySelection(() -> container.connectToServer(ProgrammaticClientEndpoint.class,
                    config, URI.create("ws://example.invalid/socket")));

            assertThat(PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS).hasValue(1);
        } finally {
            container.destroy();
        }
    }

    @ClientEndpoint(configurator = CountingConfigurator.class)
    public static class AnnotatedClientEndpoint {
        public AnnotatedClientEndpoint() {
            ANNOTATED_ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }
    }

    public static class CountingConfigurator extends ClientEndpointConfig.Configurator {
        public CountingConfigurator() {
            CONFIGURATOR_CONSTRUCTIONS.incrementAndGet();
        }
    }

    private static void assertConnectionStopsAtProxySelection(ThrowingCallable connect) {
        RuntimeException failure = new RuntimeException("stop before network connection");
        ProxySelector originalSelector = ProxySelector.getDefault();
        PROXY_SELECTIONS.set(0);
        ProxySelector.setDefault(new FailingProxySelector(failure));
        try {
            assertThatThrownBy(connect).isSameAs(failure);

            assertThat(PROXY_SELECTIONS).hasValue(1);
        } finally {
            ProxySelector.setDefault(originalSelector);
        }
    }

    public static class ProgrammaticClientEndpoint extends Endpoint {
        public ProgrammaticClientEndpoint() {
            PROGRAMMATIC_ENDPOINT_CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static class FailingProxySelector extends ProxySelector {
        private final RuntimeException failure;

        FailingProxySelector(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public List<Proxy> select(URI uri) {
            PROXY_SELECTIONS.incrementAndGet();
            throw failure;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }
}
