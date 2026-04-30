/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    void fileDescriptorSocketReportsPeerHostFromWrappedPlainSocket() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        sslContext.init(null, null, null);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        Conscrypt.setUseEngineSocket(socketFactory, false);

        InetAddress loopback = InetAddress.getLoopbackAddress();
        String targetHost = loopback.getHostAddress();
        try (ServerSocket server = new ServerSocket(0, 1, loopback);
                Socket client = new Socket(targetHost, server.getLocalPort());
                Socket accepted = server.accept()) {
            assertNull(client.getChannel());
            assertTrue(accepted.isConnected());

            try (SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(
                    client, null, server.getLocalPort(), true)) {
                assertTrue(Conscrypt.isConscrypt(sslSocket));

                String hostnameOrIp = Conscrypt.getHostnameOrIP(sslSocket);

                assertNotNull(hostnameOrIp);
                assertFalse(hostnameOrIp.isEmpty());
            }
        }
    }
}
