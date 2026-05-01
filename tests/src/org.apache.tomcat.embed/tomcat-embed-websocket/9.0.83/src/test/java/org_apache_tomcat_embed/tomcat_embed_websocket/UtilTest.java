/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.websocket.ClientEndpointHolder;
import org.apache.tomcat.websocket.DecoderEntry;
import org.apache.tomcat.websocket.Util;
import org.apache.tomcat.websocket.WsRemoteEndpointImplClient;
import org.apache.tomcat.websocket.WsSession;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;

public class UtilTest {

    @Test
    void getDecodersInstantiatesPublicDecoderAndResolvesGenericArrayType() throws DeploymentException {
        List<Class<? extends Decoder>> decoderClasses = Collections.singletonList(StringArrayTextDecoder.class);

        List<DecoderEntry> decoderEntries = Util.getDecoders(decoderClasses);

        assertThat(decoderEntries).hasSize(1);
        assertThat(decoderEntries.get(0).getDecoderClazz()).isEqualTo(StringArrayTextDecoder.class);
        assertThat(decoderEntries.get(0).getClazz()).isEqualTo(String[].class);
    }

    @Test
    void addingWholeBinaryHandlerDiscoversErasedOnMessageMethod() throws DeploymentException {
        WsSession session = newClientSession();

        session.addMessageHandler(byte[].class, new WholeByteArrayHandler());

        assertThat(session.getMessageHandlers()).hasSize(1);
    }

    @Test
    void addingPartialBinaryHandlerDiscoversErasedPartialOnMessageMethod() throws DeploymentException {
        WsSession session = newClientSession();

        session.addMessageHandler(byte[].class, new PartialByteArrayHandler());

        assertThat(session.getMessageHandlers()).hasSize(1);
    }

    private static WsSession newClientSession() throws DeploymentException {
        ClientEndpointConfig endpointConfig = ClientEndpointConfig.Builder.create().build();
        return new WsSession(new TestClientEndpointHolder(), new WsRemoteEndpointImplClient(null),
                new WsWebSocketContainer(), Collections.emptyList(), null, Collections.emptyMap(), false,
                endpointConfig);
    }

    public abstract static class GenericArrayTextDecoder<T> implements Decoder.Text<T[]> {

        @Override
        public T[] decode(String value) throws DecodeException {
            return null;
        }

        @Override
        public boolean willDecode(String value) {
            return true;
        }

        @Override
        public void init(EndpointConfig config) {
        }

        @Override
        public void destroy() {
        }
    }

    public static final class StringArrayTextDecoder extends GenericArrayTextDecoder<String> {

        public StringArrayTextDecoder() {
        }
    }

    public static final class WholeByteArrayHandler implements MessageHandler.Whole<byte[]> {

        @Override
        public void onMessage(byte[] message) {
            assertThat(message).isNull();
        }
    }

    public static final class PartialByteArrayHandler implements MessageHandler.Partial<byte[]> {

        @Override
        public void onMessage(byte[] partialMessage, boolean last) {
            assertThat(partialMessage).isNull();
            assertThat(last).isFalse();
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
