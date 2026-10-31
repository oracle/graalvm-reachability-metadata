/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.net.ServerSocket;

import javax.net.ssl.SSLServerSocket;

import org.hsqldb.server.HsqlSocketFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HsqlSocketFactoryTest {
    @Test
    void createsASecureListeningSocket() throws Exception {
        HsqlSocketFactory factory = HsqlSocketFactory.getInstance(true);
        assertTrue(factory.isSecure());
        assertSame(factory, HsqlSocketFactory.getInstance(true));
        try (ServerSocket socket = factory.createServerSocket(0, "127.0.0.1")) {
            socket.setSoTimeout(10000);
            SSLServerSocket secure = assertInstanceOf(SSLServerSocket.class, socket);
            assertTrue(socket.isBound());
            assertTrue(secure.getEnabledCipherSuites().length > 0);
        }
    }
}
