/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.util.IdleConnectionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class IdleConnectionHandlerTest {
    @Test
    void closesTrackedIdleConnection() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(10000);
            HttpConnection connection = new HttpConnection("127.0.0.1", listener.getLocalPort());
            connection.getParams().setConnectionTimeout(10000);
            connection.getParams().setSoTimeout(10000);
            try {
                connection.open();
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(10000);
                    IdleConnectionHandler handler = new IdleConnectionHandler();
                    handler.add(connection);
                    handler.closeIdleConnections(0);
                    assertFalse(connection.isOpen());
                    assertEquals(-1, peer.getInputStream().read());
                }
            } finally {
                connection.close();
            }
        }
    }
}
