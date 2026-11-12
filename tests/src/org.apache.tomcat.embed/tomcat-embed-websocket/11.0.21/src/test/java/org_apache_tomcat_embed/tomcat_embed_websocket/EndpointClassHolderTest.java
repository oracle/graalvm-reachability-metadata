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

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class EndpointClassHolderTest {
    @TempDir
    Path directory;
    private static final BlockingQueue<String> MESSAGES = new LinkedBlockingQueue<>();

    @Test
    void connectsUsingProgrammaticEndpointClass() throws Exception {
        MESSAGES.clear();
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            Session session = server.track(server.client.connectToServer(ReceivingEndpoint.class,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/echo")));
            session.getBasicRemote().sendText("constructed endpoint");
            assertThat(EmbeddedWebSocketServer.receive(MESSAGES)).isEqualTo("constructed endpoint");
        }
    }

    public static class ReceivingEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            session.addMessageHandler(String.class, MESSAGES::add);
        }
    }
}
