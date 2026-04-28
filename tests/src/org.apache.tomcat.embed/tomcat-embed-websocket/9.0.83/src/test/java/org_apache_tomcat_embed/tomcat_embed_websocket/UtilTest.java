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
import javax.websocket.Session;

import org.apache.tomcat.websocket.AsyncChannelWrapper;
import org.apache.tomcat.websocket.DecoderEntry;
import org.apache.tomcat.websocket.Util;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class UtilTest {

    @Test
    public void getDecodersInstantiatesDecoderWithGenericArrayTarget() throws Exception {
        StringArrayTextDecoder.constructed = false;
        List<Class<? extends Decoder>> decoders = Collections.singletonList(StringArrayTextDecoder.class);

        List<DecoderEntry> entries = Util.getDecoders(decoders);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getClazz()).isEqualTo(String[].class);
        assertThat(entries.get(0).getDecoderClazz()).isEqualTo(StringArrayTextDecoder.class);
        assertThat(StringArrayTextDecoder.constructed).isTrue();
    }

    @Test
    public void addWholeByteArrayHandlerUsesPublicOnMessageBridgeMethod() throws Exception {
        WsSession session = newSession();
        WholeByteArrayHandler handler = new WholeByteArrayHandler();

        session.addMessageHandler(byte[].class, handler);

        assertThat(session.getMessageHandlers()).hasSize(1);
        MessageHandler messageHandler = session.getMessageHandlers().iterator().next();
        assertThat(messageHandler).isInstanceOf(MessageHandler.Whole.class);
        @SuppressWarnings("unchecked")
        MessageHandler.Whole<ByteBuffer> wholeMessageHandler = (MessageHandler.Whole<ByteBuffer>) messageHandler;
        wholeMessageHandler.onMessage(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
        assertThat(handler.message).containsExactly(new byte[] { 1, 2, 3 });
    }

    @Test
    public void addPartialByteArrayHandlerUsesPublicPartialOnMessageBridgeMethod() throws Exception {
        WsSession session = newSession();
        PartialByteArrayHandler handler = new PartialByteArrayHandler();

        session.addMessageHandler(byte[].class, handler);

        assertThat(session.getMessageHandlers()).hasSize(1);
        MessageHandler messageHandler = session.getMessageHandlers().iterator().next();
        assertThat(messageHandler).isInstanceOf(MessageHandler.Partial.class);
        @SuppressWarnings("unchecked")
        MessageHandler.Partial<ByteBuffer> partialMessageHandler = (MessageHandler.Partial<ByteBuffer>) messageHandler;
        partialMessageHandler.onMessage(ByteBuffer.wrap(new byte[] { 4, 5, 6 }), true);
        assertThat(handler.message).containsExactly(new byte[] { 4, 5, 6 });
        assertThat(handler.last).isTrue();
    }

    private static WsSession newSession() throws Exception {
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        return new WsSession(new NoOpEndpoint(), new WsRemoteEndpointImplClient(new NoOpAsyncChannel()),
                new WsWebSocketContainer(), URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null,
                Collections.emptyList(), null, Collections.emptyMap(), false, endpointConfig);
    }

    public abstract static class GenericArrayTextDecoder<T> implements Decoder.Text<T[]> {
        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean willDecode(String message) {
            return message != null;
        }
    }

    public static class StringArrayTextDecoder extends GenericArrayTextDecoder<String> {
        private static boolean constructed;

        public StringArrayTextDecoder() {
            constructed = true;
        }

        @Override
        public String[] decode(String message) throws DecodeException {
            return message.split(",");
        }
    }

    public static class WholeByteArrayHandler implements MessageHandler.Whole<byte[]> {
        private byte[] message;

        @Override
        public void onMessage(byte[] message) {
            this.message = message;
        }
    }

    public static class PartialByteArrayHandler implements MessageHandler.Partial<byte[]> {
        private byte[] message;
        private boolean last;

        @Override
        public void onMessage(byte[] message, boolean last) {
            this.message = message;
            this.last = last;
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
