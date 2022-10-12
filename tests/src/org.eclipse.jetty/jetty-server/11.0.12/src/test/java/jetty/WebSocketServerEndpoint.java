/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;

@WebSocket
public class WebSocketServerEndpoint {
    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.printf("onConnect(%s)%n", session.getRemoteAddress());
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.printf("onClose(%s, %d, %s)%n", session.getRemoteAddress(), statusCode, reason);
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) throws IOException {
        System.out.printf("onMessage(%s, %s)%n", session.getRemoteAddress(), message);
        session.getRemote().sendString(message);
    }
}
