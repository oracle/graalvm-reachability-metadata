/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.nio.ByteBuffer;

@ServerEndpoint("/websocket")
public class JakartaWebSocketServerEndpoint {
    @OnOpen
    public void onConnect(Session session) {
        System.out.printf("onConnect(%s)%n", session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.printf("onClose(%s, %s)%n", session.getId(), closeReason);
    }

    @OnMessage
    public void onTextMessage(Session session, String message) throws IOException {
        System.out.printf("onTextMessage(%s, %s)%n", session.getId(), message);
        session.getBasicRemote().sendText(message);
    }

    //    @OnMessage
    public void onBinaryMessage(Session session, byte[] data) throws IOException {
        System.out.printf("onBinaryMessage(%s, %d bytes)%n", session.getId(), data.length);
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
    }
}
