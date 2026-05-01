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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.ClientEndpointHolder;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class WsRemoteEndpointImplBaseTest {

    @Test
    void clientSessionInitializesConfiguredEncodersOnRemoteEndpoint() throws Exception {
        PublicTextEncoder.reset();
        TestRemoteEndpoint remoteEndpoint = new TestRemoteEndpoint();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create()
                .encoders(List.of(PublicTextEncoder.class))
                .build();

        WsSession session = new WsSession(new FixedEndpointHolder(new NoOpEndpoint()), remoteEndpoint,
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);

        assertThat(session.getBasicRemote()).isNotNull();
        assertThat(PublicTextEncoder.constructions).isEqualTo(1);
        assertThat(PublicTextEncoder.initializedWith).isSameAs(endpointConfig);
    }

    public static final class PublicTextEncoder implements Encoder.Text<String> {
        private static int constructions;
        private static EndpointConfig initializedWith;

        public PublicTextEncoder() {
            constructions++;
        }

        private static void reset() {
            constructions = 0;
            initializedWith = null;
        }

        @Override
        public String encode(String object) throws EncodeException {
            return object;
        }

        @Override
        public void init(EndpointConfig config) {
            initializedWith = config;
        }

        @Override
        public void destroy() {
        }
    }

    public static final class NoOpEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    private static final class FixedEndpointHolder implements ClientEndpointHolder {
        private final Endpoint endpoint;

        private FixedEndpointHolder(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public String getClassName() {
            return endpoint.getClass().getName();
        }

        @Override
        public Endpoint getInstance(InstanceManager instanceManager) {
            return endpoint;
        }
    }

    private static final class TestRemoteEndpoint extends WsRemoteEndpointImplBase {
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
