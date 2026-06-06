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

import org.apache.tomcat.websocket.DecoderEntry;
import org.apache.tomcat.websocket.Util;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class UtilTest {

    @Test
    void getDecodersInstantiatesPublicDecoderAndResolvesGenericArrayType() throws DeploymentException {
        List<Class<? extends Decoder>> decoderClasses = List.of(StringArrayTextDecoder.class);
        List<DecoderEntry> decoderEntries = Util.getDecoders(decoderClasses);

        assertThat(decoderEntries).hasSize(1);
        DecoderEntry entry = decoderEntries.get(0);
        assertThat(entry.getDecoderClazz()).isEqualTo(StringArrayTextDecoder.class);
        assertThat(entry.getClazz()).isEqualTo(String[].class);
    }

    @Test
    void wholeByteArrayMessageHandlerIsAcceptedThroughPublicSessionApi() throws DeploymentException {
        WsSession session = newSession();

        session.addMessageHandler(byte[].class, new WholeByteArrayHandler());

        assertThat(session.getMessageHandlers()).hasSize(1);
    }

    @Test
    void partialByteArrayMessageHandlerIsAcceptedThroughPublicSessionApi() throws DeploymentException {
        WsSession session = newSession();

        session.addMessageHandler(byte[].class, new PartialByteArrayHandler());

        assertThat(session.getMessageHandlers()).hasSize(1);
    }

    private static WsSession newSession() throws DeploymentException {
        EndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        return new WsSession(new NoOpEndpoint(), new NoOpRemoteEndpoint(), new WsWebSocketContainer(),
                URI.create("ws://localhost/test"), Collections.emptyMap(), null, null, null, Collections.emptyList(),
                null, Collections.emptyMap(), false, endpointConfig);
    }

    public abstract static class ArrayTextDecoder<T> implements Decoder.Text<T[]> {
        @Override
        public T[] decode(String s) throws DecodeException {
            return null;
        }

        @Override
        public boolean willDecode(String s) {
            return true;
        }

        @Override
        public void init(EndpointConfig endpointConfig) {
        }

        @Override
        public void destroy() {
        }
    }

    public static class StringArrayTextDecoder extends ArrayTextDecoder<String> {
    }

    public static class WholeByteArrayHandler implements MessageHandler.Whole<byte[]> {
        @Override
        public void onMessage(byte[] message) {
        }
    }

    public static class PartialByteArrayHandler implements MessageHandler.Partial<byte[]> {
        @Override
        public void onMessage(byte[] partialMessage, boolean last) {
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
