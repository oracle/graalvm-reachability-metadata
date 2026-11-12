/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class PojoMethodMappingTest {
    @TempDir
    Path directory;

    @Test
    void mapsInheritedCallbacksAndConvertsPathParameters() throws Exception {
        try (EmbeddedWebSocketServer server = new EmbeddedWebSocketServer(directory, Set.of(RoomEndpoint.class))) {
            EmbeddedWebSocketServer.TextReceiver endpoint = new EmbeddedWebSocketServer.TextReceiver();
            Session session = server.track(server.client.connectToServer(endpoint,
                    ClientEndpointConfig.Builder.create().build(), server.uri("/rooms/7")));
            session.getBasicRemote().sendText("ping");
            assertThat(EmbeddedWebSocketServer.receive(endpoint.messages)).isEqualTo("7/7:ping");
        }
    }

    public static class RoomCallbacks {
        private int openedRoom;

        @OnOpen
        public void open(@PathParam("room") int room) {
            openedRoom = room;
        }

        @OnMessage
        public String message(String text, @PathParam("room") int room) {
            return openedRoom + "/" + room + ":" + text;
        }
    }

    @ServerEndpoint("/rooms/{room}")
    public static class RoomEndpoint extends RoomCallbacks {
    }
}
