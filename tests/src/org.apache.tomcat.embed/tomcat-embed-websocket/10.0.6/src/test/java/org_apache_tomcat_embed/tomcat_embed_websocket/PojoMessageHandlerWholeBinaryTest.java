/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnMessage;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoMessageHandlerWholeBinaryTest {

    @Test
    void binaryDecoderDeliversWholeMessageToPojo() throws Exception {
        BinaryEndpoint endpoint = new BinaryEndpoint();
        WsSession session = openEndpoint(endpoint, BinaryDecoder.class);

        MessageHandler handler = session.getMessageHandlers().iterator().next();
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        asWholeBinaryHandler(handler).onMessage(ByteBuffer.wrap(new byte[] {1, 2, 3}));

        assertThat(endpoint.received).isEqualTo("binary:6");
    }

    @Test
    void binaryStreamDecoderDeliversWholeMessageToPojo() throws Exception {
        StreamEndpoint endpoint = new StreamEndpoint();
        WsSession session = openEndpoint(endpoint, BinaryStreamDecoder.class);

        MessageHandler handler = session.getMessageHandlers().iterator().next();
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        asWholeBinaryHandler(handler).onMessage(ByteBuffer.wrap(new byte[] {4, 5, 6}));

        assertThat(endpoint.received).isEqualTo("stream:15");
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler.Whole<ByteBuffer> asWholeBinaryHandler(MessageHandler handler) {
        return (MessageHandler.Whole<ByteBuffer>) handler;
    }

    private static WsSession openEndpoint(Object pojo, Class<? extends Decoder> decoder)
            throws Exception {
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, List.of(decoder));
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        WsSession session = newSession(config);
        endpoint.onOpen(session, config);
        return session;
    }

    private static WsSession newSession(EndpointConfig endpointConfig) throws Exception {
        return new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null,
                Collections.emptyList(), null, Collections.emptyMap(), false, endpointConfig);
    }

    public static class BinaryEndpoint {
        private String received;

        @OnMessage
        public void onMessage(BinaryPayload payload) {
            received = "binary:" + payload.value;
        }
    }

    public static class StreamEndpoint {
        private String received;

        @OnMessage
        public void onMessage(StreamPayload payload) {
            received = "stream:" + payload.value;
        }
    }

    public static class BinaryPayload {
        private final int value;

        BinaryPayload(int value) {
            this.value = value;
        }
    }

    public static class StreamPayload {
        private final int value;

        StreamPayload(int value) {
            this.value = value;
        }
    }

    public static class BinaryDecoder implements Decoder.Binary<BinaryPayload> {
        @Override
        public BinaryPayload decode(ByteBuffer bytes) throws DecodeException {
            return new BinaryPayload(bytes.get() + bytes.get() + bytes.get());
        }

        @Override
        public boolean willDecode(ByteBuffer bytes) {
            return bytes.remaining() == 3;
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    public static class BinaryStreamDecoder implements Decoder.BinaryStream<StreamPayload> {
        @Override
        public StreamPayload decode(InputStream input) throws DecodeException, IOException {
            return new StreamPayload(input.read() + input.read() + input.read());
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    private static class NoOpEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static class NoOpRemoteEndpoint extends WsRemoteEndpointImplBase {
        @Override
        protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry, ByteBuffer... data) {
            handler.onResult(new SendResult());
        }

        @Override
        protected boolean isMasked() {
            return false;
        }

        @Override
        protected void doClose() {
        }
    }
}
