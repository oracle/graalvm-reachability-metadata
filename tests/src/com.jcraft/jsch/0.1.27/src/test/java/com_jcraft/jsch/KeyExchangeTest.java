/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class KeyExchangeTest {
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
    void presentsTheServerHostKeyFingerprintForApproval() throws Exception {
        Properties config = new Properties();
        config.setProperty("StrictHostKeyChecking", "ask");
        Session session = server.newSession(new JSch(), config);
        HostApproval approval = new HostApproval();
        session.setUserInfo(approval);
        session.setPassword(SftpServer.PASSWORD);
        session.connect(SftpServer.TIMEOUT_MILLIS);
        try {
            String fingerprint = Fingerprints.md5(Base64.getDecoder().decode(server.rsaHostKey()));
            assertThat(approval.prompts).singleElement().asString()
                    .contains("The authenticity of host '" + SftpServer.HOST + "' can't be established.")
                    .contains("RSA key fingerprint is " + fingerprint + ".");
            assertThat(session.isConnected()).isTrue();
        } finally {
            session.disconnect();
        }
    }

    private static final class HostApproval implements UserInfo {
        private final List<String> prompts = new ArrayList<>();

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
            prompts.add(message);
            return true;
        }

        @Override
        public void showMessage(String message) {
            throw new AssertionError("Unexpected SSH message: " + message);
        }
    }
}
