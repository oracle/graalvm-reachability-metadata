/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.OnMessage;
import javax.websocket.Session;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.ClientEndpointHolder;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoMessageHandlerPartialBaseTest {

    @Test
    void partialTextHandlerInvokesAnnotatedPojoMethodForEveryFragment() throws Exception {
        PartialTextEndpoint pojo = new PartialTextEndpoint();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo, Collections.emptyList(), null);
        WsSession session = newClientSession(endpointConfig);

        endpoint.onOpen(session, endpointConfig);
        MessageHandler messageHandler = session.getMessageHandlers().iterator().next();

        assertThat(messageHandler).isInstanceOf(MessageHandler.Partial.class);
        @SuppressWarnings("unchecked")
        MessageHandler.Partial<String> partialHandler = (MessageHandler.Partial<String>) messageHandler;
        partialHandler.onMessage("alpha", false);
        partialHandler.onMessage("omega", true);

        assertThat(pojo.fragments).containsExactly("alpha", "omega");
        assertThat(pojo.lastFlags).containsExactly(Boolean.FALSE, Boolean.TRUE);
        assertThat(pojo.sessions).containsExactly(session, session);
    }

    private static WsSession newClientSession(ClientEndpointConfig endpointConfig) throws DeploymentException {
        return new WsSession(new TestClientEndpointHolder(), new WsRemoteEndpointImplClient(null),
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);
    }

    public static final class PartialTextEndpoint {
        private final List<String> fragments = new ArrayList<>();
        private final List<Boolean> lastFlags = new ArrayList<>();
        private final List<Session> sessions = new ArrayList<>();

        public PartialTextEndpoint() {
        }

        @OnMessage
        public void onMessage(String fragment, boolean last, Session session) {
            fragments.add(fragment);
            lastFlags.add(Boolean.valueOf(last));
            sessions.add(session);
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
