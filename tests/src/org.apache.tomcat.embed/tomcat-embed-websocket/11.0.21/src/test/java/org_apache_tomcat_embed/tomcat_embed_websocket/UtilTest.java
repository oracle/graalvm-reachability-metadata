/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Decoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class UtilTest {
    @TempDir
    Path directory;

    @Test
    void resolvesGenericArrayDecoderAndWrapsWholeAndPartialHandlers() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            ArrayReceiver endpoint = new ArrayReceiver();
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .decoders(List.of(StringArrayDecoder.class)).build();
            Session session = server.track(server.client.connectToServer(endpoint, config, server.uri("/echo")));

            session.getBasicRemote().sendText("red,green,blue");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.arrays.messages))
                    .containsExactly("red", "green", "blue");

            session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[] {1, 2}), false);
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[] {3}), true);
            assertThat(EmbeddedWebSocketServer.receive(endpoint.bytes.messages)).containsExactly(1, 2, 3);
        }
    }

    public abstract static class ArrayDecoder<T> implements Decoder.Text<T[]> {
        @Override
        public boolean willDecode(String text) {
            return true;
        }
    }

    public static class StringArrayDecoder extends ArrayDecoder<String> {
        @Override
        public String[] decode(String text) {
            return text.split(",");
        }
    }

    public static class ArrayHandler implements MessageHandler.Whole<String[]> {
        final BlockingQueue<String[]> messages = new LinkedBlockingQueue<>();

        @Override
        public void onMessage(String[] message) {
            messages.add(message);
        }
    }

    public static class BytesHandler implements MessageHandler.Partial<byte[]> {
        final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
        private final ByteBuffer fragments = ByteBuffer.allocate(32);

        @Override
        public void onMessage(byte[] message, boolean last) {
            fragments.put(message);
            if (last) {
                byte[] assembled = new byte[fragments.position()];
                fragments.flip();
                fragments.get(assembled);
                messages.add(assembled);
            }
        }
    }

    public static class ArrayReceiver extends Endpoint {
        final ArrayHandler arrays = new ArrayHandler();
        final BytesHandler bytes = new BytesHandler();

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(arrays);
            session.addMessageHandler(bytes);
        }
    }
}
