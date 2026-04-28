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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;

import org.apache.tomcat.websocket.AsyncChannelWrapper;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoMessageHandlerWholeBaseTest {

    @Test
    public void wholeTextHandlerCreatesConfiguredDecoderWithPublicNoArgConstructor() throws Exception {
        PrefixTextDecoder.reset();
        DecodingEndpoint pojo = new DecodingEndpoint();
        List<Class<? extends Decoder>> decoders = Collections.singletonList(PrefixTextDecoder.class);
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().decoders(decoders).build();
        WsSession session = newSession(endpointConfig);
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, decoders, null);

        endpoint.onOpen(session, endpointConfig);

        assertThat(PrefixTextDecoder.constructorInvocations).isEqualTo(2);
        assertThat(PrefixTextDecoder.initInvocations).isEqualTo(1);
        assertThat(session.getMessageHandlers()).hasSize(1);
        MessageHandler handler = session.getMessageHandlers().iterator().next();
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        @SuppressWarnings("unchecked")
        MessageHandler.Whole<String> wholeMessageHandler = (MessageHandler.Whole<String>) handler;

        wholeMessageHandler.onMessage("decoded:alpha");

        assertThat(PrefixTextDecoder.decodeInvocations).isEqualTo(1);
        assertThat(pojo.payload.value).isEqualTo("alpha");
        assertThat(pojo.session).isSameAs(session);
    }

    private static WsSession newSession(ClientEndpointConfig endpointConfig) throws Exception {
        return new WsSession(new NoOpEndpoint(), new WsRemoteEndpointImplClient(new NoOpAsyncChannel()),
                new WsWebSocketContainer(), URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null,
                Collections.emptyList(), null, Collections.emptyMap(), false, endpointConfig);
    }

    public static class DecodingEndpoint {
        private DecodedPayload payload;
        private Session session;

        @OnMessage
        public void onMessage(DecodedPayload payload, Session session) {
            this.payload = payload;
            this.session = session;
        }
    }

    public static class DecodedPayload {
        private final String value;

        public DecodedPayload(String value) {
            this.value = value;
        }
    }

    public static class PrefixTextDecoder implements Decoder.Text<DecodedPayload> {
        private static int constructorInvocations;
        private static int initInvocations;
        private static int decodeInvocations;

        public PrefixTextDecoder() {
            constructorInvocations++;
        }

        private static void reset() {
            constructorInvocations = 0;
            initInvocations = 0;
            decodeInvocations = 0;
        }

        @Override
        public void init(EndpointConfig config) {
            initInvocations++;
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean willDecode(String message) {
            return message != null && message.startsWith("decoded:");
        }

        @Override
        public DecodedPayload decode(String message) throws DecodeException {
            decodeInvocations++;
            return new DecodedPayload(message.substring("decoded:".length()));
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
