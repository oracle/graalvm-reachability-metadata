/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
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

import org.apache.tomcat.websocket.EndpointHolder;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.apache.tomcat.websocket.pojo.PojoEndpointClient;
import org.junit.jupiter.api.Test;

public class PojoMessageHandlerWholeTextTest {

    @Test
    void textDecoderDeliversWholeMessageToPojo() throws Exception {
        TextEndpoint endpoint = new TextEndpoint();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        PojoEndpointClient pojoEndpoint = new PojoEndpointClient(endpoint, List.of(TextDecoder.class), null);
        WsSession session = newSession(config);

        pojoEndpoint.onOpen(session, config);

        MessageHandler handler = session.getMessageHandlers().iterator().next();
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        asWholeTextHandler(handler).onMessage("message");

        assertThat(endpoint.received).isEqualTo("decoded:message");
    }

    @Test
    void textStreamDecoderDeliversWholeMessageToPojo() throws Exception {
        TextEndpoint endpoint = new TextEndpoint();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        PojoEndpointClient pojoEndpoint = new PojoEndpointClient(endpoint, List.of(TextStreamDecoder.class), null);
        WsSession session = newSession(config);

        pojoEndpoint.onOpen(session, config);

        MessageHandler handler = session.getMessageHandlers().iterator().next();
        assertThat(handler).isInstanceOf(MessageHandler.Whole.class);
        asWholeTextHandler(handler).onMessage("stream-message");

        assertThat(endpoint.received).isEqualTo("stream:stream-message");
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler.Whole<String> asWholeTextHandler(MessageHandler handler) {
        return (MessageHandler.Whole<String>) handler;
    }

    private static WsSession newSession(ClientEndpointConfig endpointConfig) throws Exception {
        return new WsSession(new EndpointHolder(new NoOpEndpoint()), new NoOpRemoteEndpoint(),
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);
    }

    public static class TextEndpoint {
        private String received;

        @OnMessage
        public void onMessage(TextPayload payload) {
            received = payload.value;
        }
    }

    public static class TextPayload {
        private final String value;

        TextPayload(String value) {
            this.value = value;
        }
    }

    public static class TextDecoder implements Decoder.Text<TextPayload> {
        @Override
        public TextPayload decode(String message) throws DecodeException {
            return new TextPayload("decoded:" + message);
        }

        @Override
        public boolean willDecode(String message) {
            return true;
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    public static class TextStreamDecoder implements Decoder.TextStream<TextPayload> {
        @Override
        public TextPayload decode(Reader reader) throws DecodeException, IOException {
            char[] message = new char[32];
            int length = reader.read(message);
            return new TextPayload("stream:" + new String(message, 0, length));
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
        protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry,
                ByteBuffer... data) {
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
