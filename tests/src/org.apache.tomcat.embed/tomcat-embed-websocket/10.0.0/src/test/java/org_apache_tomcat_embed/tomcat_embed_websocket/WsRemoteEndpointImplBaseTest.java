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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;

import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsRemoteEndpointImplBaseTest {

    private static final AtomicBoolean ENCODER_CONSTRUCTED = new AtomicBoolean();
    private static final AtomicReference<EndpointConfig> ENCODER_INITIALIZED_WITH = new AtomicReference<>();

    @Test
    void setEncodersInstantiatesConfiguredEncoderWithPublicConstructor() throws Exception {
        ENCODER_CONSTRUCTED.set(false);
        ENCODER_INITIALIZED_WITH.set(null);
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create()
                .encoders(List.of(MessageTextEncoder.class))
                .build();

        WsSession session = new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);

        assertThat(session).isNotNull();
        assertThat(ENCODER_CONSTRUCTED).isTrue();
        assertThat(ENCODER_INITIALIZED_WITH).hasValue(endpointConfig);
    }

    public static class Message {
    }

    public static class MessageTextEncoder implements Encoder.Text<Message> {
        public MessageTextEncoder() {
            ENCODER_CONSTRUCTED.set(true);
        }

        @Override
        public String encode(Message message) throws EncodeException {
            return "encoded";
        }

        @Override
        public void init(EndpointConfig config) {
            ENCODER_INITIALIZED_WITH.set(config);
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
