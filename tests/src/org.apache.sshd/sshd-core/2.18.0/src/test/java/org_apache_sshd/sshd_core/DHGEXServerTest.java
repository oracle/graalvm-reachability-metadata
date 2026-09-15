/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.kex.DHGEXClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.kex.DHGEXServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class DHGEXServerTest {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void negotiatesGroupExchangeUsingBundledModuli() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        server.setKeyExchangeFactories(List.of(DHGEXServer.newFactory(BuiltinDHFactories.dhgex256)));

        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.setKeyExchangeFactories(List.of(DHGEXClient.newFactory(BuiltinDHFactories.dhgex256)));

        try {
            server.start();
            client.start();

            try (ClientSession session = client.connect("user", "localhost", server.getPort())
                    .verify(CONNECTION_TIMEOUT)
                    .getSession()) {
                session.addPasswordIdentity("password");
                session.auth().verify(CONNECTION_TIMEOUT);

                assertThat(session.isAuthenticated()).isTrue();
            }
        } finally {
            client.stop();
            server.stop(true);
        }
    }
}
