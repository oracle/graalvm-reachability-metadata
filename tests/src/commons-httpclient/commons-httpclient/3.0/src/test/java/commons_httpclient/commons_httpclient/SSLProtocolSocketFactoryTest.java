/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.SSLProtocolSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class SSLProtocolSocketFactoryTest {
    @Test
    void equivalentFactoriesShareHashBasedConfiguration() {
        SSLProtocolSocketFactory first = new SSLProtocolSocketFactory();
        SSLProtocolSocketFactory second = new SSLProtocolSocketFactory();
        Map<SSLProtocolSocketFactory, String> configurations = new HashMap<>();
        configurations.put(first, "secure-connection");

        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("secure-connection", configurations.get(second));
        assertNotEquals(first, new DefaultProtocolSocketFactory());
        assertNotEquals(first, null);
    }

    @Test
    void connectsToLocalListenerWithConfiguredTimeout() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpConnectionParams params = new HttpConnectionParams();
        params.setConnectionTimeout(10000);
        try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
            listener.setSoTimeout(10000);
            try (Socket client = new SSLProtocolSocketFactory().createSocket("127.0.0.1", listener.getLocalPort(), loopback, 0, params);
                    Socket peer = listener.accept()) {
                client.setSoTimeout(10000);
                peer.setSoTimeout(10000);
                assertTrue(client.isConnected());
                assertEquals(listener.getLocalPort(), client.getPort());
                assertInstanceOf(SSLSocket.class, client);
            }
        }
    }
}
