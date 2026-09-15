/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.KeySetPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class PublicKeyAuthenticationTest {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void authenticatesWithRegisteredPublicKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair clientIdentity = keyPairGenerator.generateKeyPair();
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPublickeyAuthenticator(
                new KeySetPublickeyAuthenticator("test-user", List.of(clientIdentity.getPublic())));

        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.addPublicKeyIdentity(clientIdentity);

        try {
            server.start();
            client.start();

            try (ClientSession session = client.connect("test-user", "localhost", server.getPort())
                    .verify(CONNECTION_TIMEOUT)
                    .getSession()) {
                session.auth().verify(CONNECTION_TIMEOUT);

                assertThat(session.isAuthenticated()).isTrue();
                assertThat(session.getUsername()).isEqualTo("test-user");
            }
        } finally {
            client.stop();
            server.stop(true);
        }
    }
}
