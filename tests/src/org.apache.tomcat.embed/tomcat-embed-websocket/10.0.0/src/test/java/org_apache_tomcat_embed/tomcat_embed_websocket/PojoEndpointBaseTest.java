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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;

import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoEndpointBaseTest {

    @Test
    void lifecycleCallbacksInvokeAnnotatedPojoMethods() throws DeploymentException, InstantiationException {
        ServerEndpointConfigInnerConfiguratorTest.assertFallbackDefaultConfiguratorAvailable();
        InstrumentedEndpoint pojo = new InstrumentedEndpoint();
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, List.of(), null);
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        WsSession session = newSession(config);
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");
        RuntimeException failure = new RuntimeException("boom");

        endpoint.onOpen(session, config);
        endpoint.onClose(session, closeReason);
        endpoint.onError(session, failure);

        assertThat(pojo.openSession).isSameAs(session);
        assertThat(pojo.openConfig).isSameAs(config);
        assertThat(pojo.closeSession).isSameAs(session);
        assertThat(pojo.closeReason).isSameAs(closeReason);
        assertThat(pojo.errorSession).isSameAs(session);
        assertThat(pojo.error).isSameAs(failure);
    }

    private static WsSession newSession(EndpointConfig endpointConfig) throws DeploymentException {
        return new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);
    }

    @ClientEndpoint
    public static class InstrumentedEndpoint {
        private Session openSession;
        private EndpointConfig openConfig;
        private Session closeSession;
        private CloseReason closeReason;
        private Session errorSession;
        private Throwable error;

        @OnOpen
        public void opened(Session session, EndpointConfig config) {
            openSession = session;
            openConfig = config;
        }

        @OnClose
        public void closed(Session session, CloseReason reason) {
            closeSession = session;
            closeReason = reason;
        }

        @OnError
        public void failed(Session session, Throwable throwable) {
            errorSession = session;
            error = throwable;
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
