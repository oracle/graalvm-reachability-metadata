/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class PojoMessageHandlerWholeBinaryTest {

    @Test
    void wholeBinaryMessageWithBinaryDecoderInvokesWrappedPojoHandler() throws DeploymentException {
        PayloadBinaryDecoder.reset();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .decoders(List.of(PayloadBinaryDecoder.class))
                .build();
        WsSession session = newSession(config);
        PayloadHandler payloadHandler = new PayloadHandler();

        session.addMessageHandler(payloadHandler);
        MessageHandler wrappedHandler = session.getMessageHandlers().iterator().next();
        asWholeBinaryHandler(wrappedHandler).onMessage(ByteBuffer.wrap(bytes("binary-payload")));

        assertThat(wrappedHandler).isNotSameAs(payloadHandler);
        assertThat(PayloadBinaryDecoder.constructions()).isGreaterThanOrEqualTo(2);
        assertThat(PayloadBinaryDecoder.initializedWith()).isSameAs(config);
        assertThat(payloadHandler.receivedPayload.value).isEqualTo("binary-payload");
    }

    @Test
    void wholeBinaryMessageWithBinaryStreamDecoderInvokesWrappedPojoHandler() throws DeploymentException {
        PayloadBinaryStreamDecoder.reset();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .decoders(List.of(PayloadBinaryStreamDecoder.class))
                .build();
        WsSession session = newSession(config);
        PayloadHandler payloadHandler = new PayloadHandler();

        session.addMessageHandler(payloadHandler);
        MessageHandler wrappedHandler = session.getMessageHandlers().iterator().next();
        asWholeBinaryHandler(wrappedHandler).onMessage(ByteBuffer.wrap(bytes("stream-payload")));

        assertThat(wrappedHandler).isNotSameAs(payloadHandler);
        assertThat(PayloadBinaryStreamDecoder.constructions()).isGreaterThanOrEqualTo(2);
        assertThat(PayloadBinaryStreamDecoder.initializedWith()).isSameAs(config);
        assertThat(payloadHandler.receivedPayload.value).isEqualTo("stream-payload");
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler.Whole<ByteBuffer> asWholeBinaryHandler(MessageHandler handler) {
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        return (MessageHandler.Whole<ByteBuffer>) handler;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String string(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.slice();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static WsSession newSession(EndpointConfig endpointConfig) throws DeploymentException {
        return new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);
    }

    public static class Payload {
        private final String value;

        Payload(String value) {
            this.value = value;
        }
    }

    public static class PayloadHandler implements MessageHandler.Whole<Payload> {
        private Payload receivedPayload;

        @Override
        public void onMessage(Payload message) {
            receivedPayload = message;
        }
    }

    public static class PayloadBinaryDecoder implements Decoder.Binary<Payload> {
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
        private static final AtomicReference<EndpointConfig> INITIALIZED_WITH = new AtomicReference<>();

        public PayloadBinaryDecoder() {
            CONSTRUCTIONS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTIONS.set(0);
            INITIALIZED_WITH.set(null);
        }

        static int constructions() {
            return CONSTRUCTIONS.get();
        }

        static EndpointConfig initializedWith() {
            return INITIALIZED_WITH.get();
        }

        @Override
        public Payload decode(ByteBuffer bytes) throws DecodeException {
            return new Payload(string(bytes));
        }

        @Override
        public boolean willDecode(ByteBuffer bytes) {
            return bytes.hasRemaining();
        }

        @Override
        public void init(EndpointConfig config) {
            INITIALIZED_WITH.set(config);
        }

        @Override
        public void destroy() {
        }
    }

    public static class PayloadBinaryStreamDecoder implements Decoder.BinaryStream<Payload> {
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
        private static final AtomicReference<EndpointConfig> INITIALIZED_WITH = new AtomicReference<>();

        public PayloadBinaryStreamDecoder() {
            CONSTRUCTIONS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTIONS.set(0);
            INITIALIZED_WITH.set(null);
        }

        static int constructions() {
            return CONSTRUCTIONS.get();
        }

        static EndpointConfig initializedWith() {
            return INITIALIZED_WITH.get();
        }

        @Override
        public Payload decode(InputStream is) throws DecodeException, IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[32];
            int read = is.read(buffer);
            while (read != -1) {
                output.write(buffer, 0, read);
                read = is.read(buffer);
            }
            return new Payload(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }

        @Override
        public void init(EndpointConfig config) {
            INITIALIZED_WITH.set(config);
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
