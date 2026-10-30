/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.httpclient.HttpConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class HttpConnectionTest {
    @Test
    void halfClosesOutputWhileKeepingResponseInputOpen() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
            listener.setSoTimeout(10000);
            HttpConnection connection = new HttpConnection("127.0.0.1", listener.getLocalPort());
            connection.getParams().setConnectionTimeout(10000);
            connection.getParams().setSoTimeout(10000);
            try {
                connection.open();
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(10000);
                    connection.printLine("request", "US-ASCII");
                    connection.flushRequestOutputStream();
                    connection.shutdownOutput();
                    assertEquals("request\r\n", new String(peer.getInputStream().readAllBytes(), "US-ASCII"));
                    peer.getOutputStream().write("response\r\n".getBytes("US-ASCII"));
                    peer.getOutputStream().flush();
                    assertEquals("response", connection.readLine("US-ASCII"));
                    assertTrue(connection.isOpen());
                }
            } finally {
                connection.close();
            }
            assertFalse(connection.isOpen());
        }
    }
}
