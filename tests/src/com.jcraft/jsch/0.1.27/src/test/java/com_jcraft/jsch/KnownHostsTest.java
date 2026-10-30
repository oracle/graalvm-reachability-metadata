/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KnownHostsTest {
    private static SftpServer server;

    @TempDir
    Path directory;

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
    void recordsAnApprovedHostKeyAndTrustsItOnReconnect() throws Exception {
        Path knownHosts = Files.createFile(directory.resolve("known_hosts"));
        String hostKey = server.rsaHostKey();

        JSch firstClient = new JSch();
        firstClient.setKnownHosts(knownHosts.toString());
        Session first = connect(firstClient, "ask", new ApprovingUser());
        first.disconnect();
        assertThat(Files.readString(knownHosts)).isEqualTo(SftpServer.HOST + " ssh-rsa " + hostKey + "\n");

        JSch secondClient = new JSch();
        secondClient.setKnownHosts(knownHosts.toString());
        HostKey[] trusted = secondClient.getHostKeyRepository().getHostKey(SftpServer.HOST, "ssh-rsa");
        assertThat(trusted).singleElement().extracting(HostKey::getKey).isEqualTo(hostKey);
        Session second = connect(secondClient, "yes", new RefusingUser());
        try {
            assertThat(second.isConnected()).isTrue();
            assertThat(second.getHostKey().getKey()).isEqualTo(hostKey);
        } finally {
            second.disconnect();
        }
    }

    @Test
    void rejectsAServerWhoseHostKeyChanged() throws Exception {
        JSch jsch = new JSch();
        KeyPair otherKey = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
        Path knownHosts = directory.resolve("known_hosts");
        try {
            String staleEntry = SftpServer.HOST + " ssh-rsa "
                    + Base64.getEncoder().encodeToString(otherKey.getPublicKeyBlob()) + "\n";
            Files.writeString(knownHosts, staleEntry);
        } finally {
            otherKey.dispose();
        }
        jsch.setKnownHosts(knownHosts.toString());

        assertThatThrownBy(() -> connect(jsch, "yes", null))
                .isInstanceOf(JSchException.class)
                .hasMessage("HostKey has been changed: " + SftpServer.HOST);
    }

    private static Session connect(JSch jsch, String strictHostKeyChecking, UserInfo user) throws Exception {
        Properties config = new Properties();
        config.setProperty("StrictHostKeyChecking", strictHostKeyChecking);
        Session session = server.newSession(jsch, config);
        session.setUserInfo(user);
        session.setPassword(SftpServer.PASSWORD);
        session.connect(SftpServer.TIMEOUT_MILLIS);
        return session;
    }

    private static class ApprovingUser implements UserInfo {
        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return SftpServer.PASSWORD;
        }

        @Override
        public boolean promptPassword(String message) {
            return true;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
            throw new AssertionError("Unexpected SSH message: " + message);
        }
    }

    private static final class RefusingUser extends ApprovingUser {
        @Override
        public boolean promptYesNo(String message) {
            throw new AssertionError("A known host must not be prompted for: " + message);
        }
    }
}
