/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.Decoder;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class PojoMessageHandlerWholeBaseTest {
    @TempDir
    Path directory;

    @Test
    void instantiatesConfiguredDecoderAndDeliversDecodedMessage() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            DecodingClient endpoint = new DecodingClient();
            Session session = server.track(server.client.connectToServer(endpoint, server.uri("/echo")));
            session.getBasicRemote().sendText("invoice:42");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("decoded:invoice:42");
        }
    }

    public record Payload(String value) {
    }

    public static class PayloadDecoder implements Decoder.Text<Payload> {
        private String prefix;

        @Override
        public void init(EndpointConfig config) {
            prefix = "decoded:";
        }

        @Override
        public Payload decode(String text) {
            return new Payload(prefix + text);
        }

        @Override
        public boolean willDecode(String text) {
            return text.startsWith("invoice:");
        }
    }

    @ClientEndpoint(decoders = PayloadDecoder.class)
    public static class DecodingClient {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @OnMessage
        public void message(Payload payload) {
            messages.add(payload.value());
        }
    }
}
