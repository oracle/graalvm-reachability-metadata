/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.ClientEndpointHolder;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoEndpointBaseTest {

    @Test
    void annotatedLifecycleCallbacksAreInvokedByPojoEndpointBase() throws Exception {
        LifecycleEndpoint pojo = new LifecycleEndpoint();
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, Collections.emptyList(), null);
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        WsSession session = newClientSession(endpointConfig);
        Throwable throwable = new IllegalStateException("expected");
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");

        endpoint.onOpen(session, endpointConfig);
        endpoint.onError(session, throwable);
        endpoint.onClose(session, closeReason);

        assertThat(pojo.openSession).isSameAs(session);
        assertThat(pojo.openConfig).isSameAs(endpointConfig);
        assertThat(pojo.errorSession).isSameAs(session);
        assertThat(pojo.errorThrowable).isSameAs(throwable);
        assertThat(pojo.closeSession).isSameAs(session);
        assertThat(pojo.closeReason).isSameAs(closeReason);
    }

    private static WsSession newClientSession(ClientEndpointConfig endpointConfig) throws DeploymentException {
        return new WsSession(new TestClientEndpointHolder(), new WsRemoteEndpointImplClient(null),
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);
    }

    public static final class LifecycleEndpoint {
        private Session openSession;
        private EndpointConfig openConfig;
        private Session errorSession;
        private Throwable errorThrowable;
        private Session closeSession;
        private CloseReason closeReason;

        public LifecycleEndpoint() {
        }

        @OnOpen
        public void onOpen(Session session, EndpointConfig config) {
            openSession = session;
            openConfig = config;
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            errorSession = session;
            errorThrowable = throwable;
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            closeSession = session;
            closeReason = reason;
        }
    }

    private static final class TestClientEndpointHolder implements ClientEndpointHolder {

        @Override
        public String getClassName() {
            return NoOpEndpoint.class.getName();
        }

        @Override
        public Endpoint getInstance(InstanceManager instanceManager) {
            return new NoOpEndpoint();
        }
    }

    public static final class NoOpEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }
}
