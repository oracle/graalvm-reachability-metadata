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
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class PojoEndpointBaseTest {
    @TempDir
    Path directory;

    @Test
    void dispatchesOpenDecodeErrorAndCloseCallbacks() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            LifecycleClient endpoint = new LifecycleClient();
            Session session = server.track(server.client.connectToServer(endpoint, server.uri("/echo")));
            assertThat(EmbeddedWebSocketServer.receive(endpoint.opened)).isSameAs(session);
            session.getBasicRemote().sendText("23");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.numbers)).isEqualTo(23);
            session.getBasicRemote().sendText("not-a-number");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.errors)).isInstanceOf(DecodeException.class)
                    .hasMessageContaining("Expected an integer");
            session.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "finished"));
            CloseReason reason = EmbeddedWebSocketServer.receive(endpoint.closed);
            assertThat(reason.getCloseCode()).isEqualTo(CloseCodes.NORMAL_CLOSURE);
            assertThat(reason.getReasonPhrase()).isEqualTo("finished");
        }
    }

    public record NumberValue(int value) {
    }

    public static class IntegerDecoder implements Decoder.Text<NumberValue> {
        @Override
        public boolean willDecode(String text) {
            return true;
        }

        @Override
        public NumberValue decode(String text) throws DecodeException {
            try {
                return new NumberValue(Integer.parseInt(text));
            } catch (NumberFormatException exception) {
                throw new DecodeException(text, "Expected an integer", exception);
            }
        }
    }

    @ClientEndpoint(decoders = IntegerDecoder.class)
    public static class LifecycleClient {
        final BlockingQueue<Session> opened = new LinkedBlockingQueue<>();
        final BlockingQueue<Integer> numbers = new LinkedBlockingQueue<>();
        final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseReason> closed = new LinkedBlockingQueue<>();

        @OnOpen
        public void open(Session session) {
            opened.add(session);
        }

        @OnMessage
        public void message(NumberValue number) {
            numbers.add(number.value());
        }

        @OnError
        public void error(Throwable failure) {
            errors.add(failure);
        }

        @OnClose
        public void close(CloseReason reason) {
            closed.add(reason);
        }
    }
}
