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
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class PojoClassHolderTest {
    @TempDir
    Path directory;
    private static final BlockingQueue<String> MESSAGES = new LinkedBlockingQueue<>();

    @Test
    void connectsUsingAnnotatedEndpointClass() throws Exception {
        MESSAGES.clear();
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory)) {
            Session session = server.track(server.client.connectToServer(ReceivingPojo.class, server.uri("/echo")));
            session.getBasicRemote().sendText("constructed pojo");
            assertThat(EmbeddedWebSocketServer.receive(MESSAGES)).isEqualTo("constructed pojo");
        }
    }

    @ClientEndpoint
    public static class ReceivingPojo {
        @OnMessage
        public void message(String text) {
            MESSAGES.add(text);
        }
    }
}
