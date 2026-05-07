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

import jakarta.websocket.ClientEndpointConfig;
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

public class PojoMessageHandlerPartialBaseTest {

    @Test
    void partialByteArrayMessageInvokesWrappedPojoHandler() throws DeploymentException {
        WsSession session = newSession();
        RecordingPartialByteArrayHandler handler = new RecordingPartialByteArrayHandler();

        session.addMessageHandler(byte[].class, handler);
        MessageHandler wrappedHandler = session.getMessageHandlers().iterator().next();
        asPartialBinaryHandler(wrappedHandler).onMessage(ByteBuffer.wrap(new byte[] {1, 2, 3}), true);

        assertThat(wrappedHandler).isNotSameAs(handler);
        assertThat(handler.receivedMessage).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(handler.receivedLast).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static MessageHandler.Partial<ByteBuffer> asPartialBinaryHandler(MessageHandler handler) {
        assertThat(handler).isInstanceOf(MessageHandler.Partial.class);
        return (MessageHandler.Partial<ByteBuffer>) handler;
    }

    private static WsSession newSession() throws DeploymentException {
        EndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        return new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);
    }

    public static class RecordingPartialByteArrayHandler implements MessageHandler.Partial<byte[]> {
        private byte[] receivedMessage;
        private boolean receivedLast;

        @Override
        public void onMessage(byte[] partialMessage, boolean last) {
            receivedMessage = partialMessage;
            receivedLast = last;
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
