/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IdentityFileTest {
    private static final String PASSPHRASE = "identity-secret";

    @TempDir
    static Path directory;

    private static Path identity;
    private static SftpServer server;

    @BeforeAll
    static void startServerTrustingAGeneratedKey() throws Exception {
        KeyPair pair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, 2048);
        try {
            identity = directory.resolve("id_rsa");
            pair.setPassphrase(PASSPHRASE);
            pair.writePrivateKey(identity.toString());
            pair.writePublicKey(identity + ".pub", "jsch-client");
            ByteArrayOutputStream authorizedKey = new ByteArrayOutputStream();
            pair.writePublicKey(authorizedKey, "jsch-client");
            server = SftpServer.start(authorizedKey.toString(StandardCharsets.US_ASCII).trim());
        } finally {
            pair.dispose();
        }
        assertThat(Files.readString(identity)).contains("Proc-Type: 4,ENCRYPTED", "DEK-Info: DES-EDE3-CBC");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void authenticatesWithAnEncryptedRsaIdentity() throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(identity.toString(), PASSPHRASE);
        Session session = server.newSession(jsch, new Properties());
        session.connect(SftpServer.TIMEOUT_MILLIS);
        try {
            assertAuthenticated(session);
        } finally {
            session.disconnect();
        }
    }

    @Test
    void asksForThePassphraseOfAnEncryptedRsaIdentity() throws Exception {
        JSch jsch = new JSch();
        jsch.addIdentity(identity.toString());
        Session session = server.newSession(jsch, new Properties());
        PassphraseUser user = new PassphraseUser();
        session.setUserInfo(user);
        session.connect(SftpServer.TIMEOUT_MILLIS);
        try {
            assertThat(user.passphrasePrompts).containsExactly("Passphrase for " + identity);
            assertAuthenticated(session);
        } finally {
            session.disconnect();
        }
    }

    /// Opens an SFTP channel, which the server grants only to an authenticated user.
    private static void assertAuthenticated(Session session) throws Exception {
        assertThat(session.isConnected()).isTrue();
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        try {
            assertThat(sftp.stat(SftpServer.UPLOAD_DIRECTORY).isDir()).isTrue();
        } finally {
            sftp.disconnect();
        }
    }

    private static final class PassphraseUser implements UserInfo {
        private final List<String> passphrasePrompts = new ArrayList<>();

        @Override
        public String getPassphrase() {
            return PASSPHRASE;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            passphrasePrompts.add(message);
            return true;
        }

        @Override
        public boolean promptYesNo(String message) {
            return false;
        }

        @Override
        public void showMessage(String message) {
            throw new AssertionError("Unexpected SSH message: " + message);
        }
    }
}
