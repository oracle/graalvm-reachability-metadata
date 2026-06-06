/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.ByteBuffer;
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

public class PojoMessageHandlerWholeBaseTest {

    @Test
    void wholeTextMessageWithDecoderInvokesWrappedPojoHandler() throws DeploymentException {
        PayloadTextDecoder.reset();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .decoders(List.of(PayloadTextDecoder.class))
                .build();
        WsSession session = newSession(config);
        PayloadHandler payloadHandler = new PayloadHandler();

        session.addMessageHandler(payloadHandler);
        MessageHandler wrappedHandler = session.getMessageHandlers().iterator().next();
        asWholeTextHandler(wrappedHandler).onMessage("decoded-value");

        assertThat(wrappedHandler).isNotSameAs(payloadHandler);
        assertThat(PayloadTextDecoder.constructions()).isGreaterThanOrEqualTo(1);
        assertThat(PayloadTextDecoder.initializedWith()).isSameAs(config);
        assertThat(payloadHandler.receivedPayload.value).isEqualTo("decoded-value");
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler.Whole<String> asWholeTextHandler(MessageHandler handler) {
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        return (MessageHandler.Whole<String>) handler;
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

    public static class PayloadTextDecoder implements Decoder.Text<Payload> {
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
        private static final AtomicReference<EndpointConfig> INITIALIZED_WITH = new AtomicReference<>();

        public PayloadTextDecoder() {
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
        public Payload decode(String s) throws DecodeException {
            return new Payload(s);
        }

        @Override
        public boolean willDecode(String s) {
            return true;
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
