/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;

@ServerEndpoint("/websocket")
public class WebSocketHandler {
    @OnOpen
    public void onOpen(Session session) {
        System.out.printf("onOpen(%s)%n", session);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.printf("onClose(%s, %s)%n", session, closeReason);
    }

    @OnMessage
    public void onMessage(Session session, String text) throws IOException {
        System.out.printf("onMessage(%s, %s)%n", session, text);
        session.getBasicRemote().sendText(text);
    }
}
