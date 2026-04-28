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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.apache.tomcat.websocket.AsyncChannelWrapper;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsRemoteEndpointImplBaseTest {

    @Test
    public void sessionInitializesConfiguredEncoders() throws Exception {
        PayloadTextEncoder.constructed = false;
        PayloadTextEncoder.initialized = false;
        CapturingAsyncChannel channel = new CapturingAsyncChannel();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create()
                .encoders(Collections.singletonList(PayloadTextEncoder.class)).build();

        new WsSession(new NoOpEndpoint(), new WsRemoteEndpointImplClient(channel), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);

        assertThat(PayloadTextEncoder.constructed).isTrue();
        assertThat(PayloadTextEncoder.initialized).isTrue();
    }

    public static class Payload {
        private final String message;

        public Payload(String message) {
            this.message = message;
        }
    }

    public static class PayloadTextEncoder implements Encoder.Text<Payload> {
        private static boolean constructed;
        private static boolean initialized;

        public PayloadTextEncoder() {
            constructed = true;
        }

        @Override
        public void init(EndpointConfig config) {
            initialized = true;
        }

        @Override
        public void destroy() {
        }

        @Override
        public String encode(Payload object) throws EncodeException {
            return object.message;
        }
    }

    private static class NoOpEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static class CapturingAsyncChannel implements AsyncChannelWrapper {
        private final List<byte[]> writes = new ArrayList<>();

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
            byte[] data = new byte[bytes];
            src.get(data);
            writes.add(data);
            return CompletableFuture.completedFuture(bytes);
        }

        @Override
        public <B, A extends B> void write(ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit,
                A attachment, CompletionHandler<Long, B> handler) {
            long bytes = 0;
            for (int i = offset; i < offset + length; i++) {
                bytes += srcs[i].remaining();
                byte[] data = new byte[srcs[i].remaining()];
                srcs[i].get(data);
                writes.add(data);
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
