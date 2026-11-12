/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class PojoMessageHandlerPartialBaseTest {
    @TempDir
    Path directory;

    @Test
    void invokesAnnotatedHandlerForBinaryFragments() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            FragmentClient endpoint = new FragmentClient();
            Session session = server.track(server.client.connectToServer(endpoint, server.uri("/echo")));
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[] {10, 20}), false);
            session.getBasicRemote().sendBinary(ByteBuffer.wrap(new byte[] {30, 40}), true);
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).containsExactly(10, 20, 30, 40);
        }
    }

    @ClientEndpoint
    public static class FragmentClient {
        final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

        @OnMessage
        public void message(byte[] fragment, boolean last) {
            fragments.writeBytes(fragment);
            if (last) {
                messages.add(fragments.toByteArray());
            }
        }
    }
}
