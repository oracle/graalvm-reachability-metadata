/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_httpclient.commons_httpclient;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.DefaultProtocolSocketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(120)
public class ReflectionSocketFactoryTest {
    @Test
    void connectsBindsAndTransfersDataThroughTimeoutSocketFactory() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpConnectionParams params = new HttpConnectionParams();
        params.setConnectionTimeout(10000);
        try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
            listener.setSoTimeout(10000);
            try (Socket socket = new DefaultProtocolSocketFactory().createSocket("127.0.0.1", listener.getLocalPort(), loopback, 0, params);
                    Socket peer = listener.accept()) {
                socket.setSoTimeout(10000);
                peer.setSoTimeout(10000);
                assertEquals(loopback, socket.getLocalAddress());
                socket.getOutputStream().write(87);
                socket.getOutputStream().flush();
                assertEquals(87, peer.getInputStream().read());
                peer.getOutputStream().write(88);
                peer.getOutputStream().flush();
                assertEquals(88, socket.getInputStream().read());
            }
        }
    }
}
