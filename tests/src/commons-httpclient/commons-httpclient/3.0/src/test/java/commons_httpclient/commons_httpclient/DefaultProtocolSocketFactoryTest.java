/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class DefaultProtocolSocketFactoryTest {
    @Test
    void connectsToLocalListenerWithConfiguredTimeout() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpConnectionParams params = new HttpConnectionParams();
        params.setConnectionTimeout(10000);
        try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
            listener.setSoTimeout(10000);
            try (Socket client = new DefaultProtocolSocketFactory().createSocket("127.0.0.1", listener.getLocalPort(), loopback, 0, params);
                    Socket peer = listener.accept()) {
                client.setSoTimeout(10000);
                peer.setSoTimeout(10000);
                assertTrue(client.isConnected());
                assertEquals(listener.getLocalPort(), client.getPort());
                client.getOutputStream().write(42);
            client.getOutputStream().flush();
            assertEquals(42, peer.getInputStream().read());
            }
        }
    }
}
