/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.ClientEndpointHolder;
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

        WsSession session = new WsSession(new NoOpClientEndpointHolder(), new NoOpRemoteEndpoint(),
                new WsWebSocketContainer(), Collections.<Extension>emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);

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

    private static class NoOpClientEndpointHolder implements ClientEndpointHolder {
        @Override
        public String getClassName() {
            return NoOpEndpoint.class.getName();
        }

        @Override
        public Endpoint getInstance(InstanceManager instanceManager) {
            return new NoOpEndpoint();
        }
    }

    private static class NoOpEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static class NoOpRemoteEndpoint extends WsRemoteEndpointImplBase {
        private final Lock lock = new ReentrantLock();

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

        @Override
        protected Lock getLock() {
            return lock;
        }
    }
}
