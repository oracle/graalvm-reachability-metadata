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
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
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

public class PojoMessageHandlerWholeBaseTest {

    @Test
    void wholeTextHandlerCreatesDecoderAndInvokesAnnotatedPojoMethod() throws Exception {
        DecodingEndpoint pojo = new DecodingEndpoint();
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        PojoEndpointClient endpoint = new PojoEndpointClient(pojo,
                Collections.singletonList(PrefixTextDecoder.class), null);
        WsSession session = newClientSession(endpointConfig);

        endpoint.onOpen(session, endpointConfig);
        MessageHandler messageHandler = session.getMessageHandlers().iterator().next();

        assertThat(messageHandler).isInstanceOf(MessageHandler.Whole.class);
        @SuppressWarnings("unchecked")
        MessageHandler.Whole<String> wholeHandler = (MessageHandler.Whole<String>) messageHandler;
        wholeHandler.onMessage("decode:payload");

        assertThat(pojo.receivedText.value).isEqualTo("payload");
        assertThat(pojo.receivedSession).isSameAs(session);
    }

    private static WsSession newClientSession(ClientEndpointConfig endpointConfig) throws DeploymentException {
        return new WsSession(new TestClientEndpointHolder(), new WsRemoteEndpointImplClient(null),
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);
    }

    public static final class DecodingEndpoint {
        private DecodedText receivedText;
        private Session receivedSession;

        public DecodingEndpoint() {
        }

        @OnMessage
        public void onMessage(DecodedText text, Session session) {
            receivedText = text;
            receivedSession = session;
        }
    }

    public static final class DecodedText {
        private final String value;

        public DecodedText(String value) {
            this.value = value;
        }
    }

    public static final class PrefixTextDecoder implements Decoder.Text<DecodedText> {

        public PrefixTextDecoder() {
        }

        @Override
        public DecodedText decode(String value) throws DecodeException {
            return new DecodedText(value.substring("decode:".length()));
        }

        @Override
        public boolean willDecode(String value) {
            return value.startsWith("decode:");
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
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
