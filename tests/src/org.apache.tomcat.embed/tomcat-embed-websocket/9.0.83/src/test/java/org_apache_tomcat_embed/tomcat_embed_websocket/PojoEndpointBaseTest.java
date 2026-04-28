/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.apache.tomcat.websocket.AsyncChannelWrapper;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoEndpointBaseTest {

    @Test
    public void clientEndpointInvokesAnnotatedLifecycleMethods() throws Exception {
        AnnotatedWebSocket pojo = new AnnotatedWebSocket();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        WsSession session = newSession(endpointConfig);
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, Collections.emptyList(), null);
        RuntimeException failure = new RuntimeException("boom");
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");

        endpoint.onOpen(session, endpointConfig);
        endpoint.onError(session, failure);
        endpoint.onClose(session, closeReason);

        assertThat(pojo.openSession).isSameAs(session);
        assertThat(pojo.openConfig).isSameAs(endpointConfig);
        assertThat(pojo.errorSession).isSameAs(session);
        assertThat(pojo.errorThrowable).isSameAs(failure);
        assertThat(pojo.closeSession).isSameAs(session);
        assertThat(pojo.closeReason).isSameAs(closeReason);
    }

    private static WsSession newSession(ClientEndpointConfig endpointConfig) throws Exception {
        return new WsSession(new NoOpEndpoint(), new WsRemoteEndpointImplClient(new NoOpAsyncChannel()),
                new WsWebSocketContainer(), URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null,
                Collections.emptyList(), null, Collections.emptyMap(), false, endpointConfig);
    }

    public static class AnnotatedWebSocket {
        private Session openSession;
        private EndpointConfig openConfig;
        private Session errorSession;
        private Throwable errorThrowable;
        private Session closeSession;
        private CloseReason closeReason;

        @OnOpen
        public void onOpen(Session session, EndpointConfig config) {
            this.openSession = session;
            this.openConfig = config;
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            this.errorSession = session;
            this.errorThrowable = throwable;
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            this.closeSession = session;
            this.closeReason = closeReason;
        }
    }

    private static class NoOpEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static class NoOpAsyncChannel implements AsyncChannelWrapper {
        @Override
        public Future<Integer> read(ByteBuffer dst) {
            return CompletableFuture.completedFuture(-1);
        }

        @Override
        public <B, A extends B> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, B> handler) {
            handler.completed(-1, attachment);
        }

        @Override
        public Future<Integer> write(ByteBuffer src) {
            int bytes = src.remaining();
            src.position(src.limit());
            return CompletableFuture.completedFuture(bytes);
        }

        @Override
        public <B, A extends B> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit,
                A attachment, CompletionHandler<Long, B> handler) {
            long bytes = 0;
            for (int i = offset; i < offset + length; i++) {
                bytes += srcs[i].remaining();
                srcs[i].position(srcs[i].limit());
            }
            handler.completed(bytes, attachment);
        }

        @Override
        public void close() {
        }

        @Override
        public Future<Void> handshake() throws SSLException {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public SocketAddress getLocalAddress() throws IOException {
            return null;
        }
    }
}
