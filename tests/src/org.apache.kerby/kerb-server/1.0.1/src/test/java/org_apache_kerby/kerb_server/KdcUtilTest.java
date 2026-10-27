/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_server;

import org.apache.kerby.kerberos.kerb.identity.backend.IdentityBackend;
import org.apache.kerby.kerberos.kerb.server.KdcServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

public class KdcUtilTest {
    private static final String MEMORY_IDENTITY_BACKEND = "org.apache.kerby.kerberos.kerb."
            + "identity.backend.MemoryIdentityBackend";

    @Test
    void startsEmbeddedKdcWithDefaultMemoryIdentityBackend() throws Exception {
        KdcServer server = new KdcServer();
        server.setKdcHost(InetAddress.getLoopbackAddress().getHostAddress());
        server.setKdcRealm("EXAMPLE.COM");
        server.setAllowTcp(true);
        server.setAllowUdp(false);
        server.setKdcTcpPort(availableTcpPort());

        boolean initialized = false;
        try {
            server.init();
            initialized = true;
            IdentityBackend identityBackend = server.getIdentityService();

            assertThat(identityBackend.getClass().getName()).isEqualTo(MEMORY_IDENTITY_BACKEND);

            server.start();
            assertThat(server.getKdcTcpPort()).isGreaterThan(0);
        } finally {
            if (initialized) {
                server.stop();
            }
        }
    }

    private static int availableTcpPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
