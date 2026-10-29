/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.Session;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DHG1Test {
    private static SftpServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = SftpServer.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void negotiatesGroupOneAndVerifiesTheServerRsaHostKeySignature() throws Exception {
        Properties config = new Properties();
        config.setProperty("kex", "diffie-hellman-group1-sha1");
        config.setProperty("server_host_key", "ssh-rsa");
        Session session = server.connect(config);
        try {
            HostKey hostKey = session.getHostKey();
            assertThat(session.isConnected()).isTrue();
            assertThat(hostKey.getHost()).isEqualTo(SftpServer.HOST);
            assertThat(hostKey.getType()).isEqualTo("ssh-rsa");
            assertThat(hostKey.getKey()).isEqualTo(server.rsaHostKey());
            session.sendIgnore();
        } finally {
            session.disconnect();
        }
    }
}
