/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.AcceptAllPasswordAuthenticator;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;

public class Sshd_coreTest {
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void authenticatesAndExecutesCommandsOverSsh() throws Exception {
        SshServer server = SshServer.setUpDefaultServer();
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator(AcceptAllPasswordAuthenticator.INSTANCE);
        server.setCommandFactory((channel, command) -> new ReplyCommand(command));

        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);

        try {
            server.start();
            client.start();

            try (ClientSession session = client.connect("user", "localhost", server.getPort())
                    .verify(CONNECTION_TIMEOUT)
                    .getSession()) {
                session.addPasswordIdentity("password");
                session.auth().verify(CONNECTION_TIMEOUT);

                assertThat(session.executeRemoteCommand("first-command", CONNECTION_TIMEOUT))
                        .isEqualTo("executed: first-command");
                assertThat(session.executeRemoteCommand("second-command", CONNECTION_TIMEOUT))
                        .isEqualTo("executed: second-command");
            }
        } finally {
            client.stop();
            server.stop(true);
        }
    }

    private static final class ReplyCommand implements Command {
        private final String command;
        private OutputStream outputStream;
        private ExitCallback exitCallback;

        private ReplyCommand(String command) {
            this.command = command;
        }

        @Override
        public void setInputStream(InputStream inputStream) {
        }

        @Override
        public void setOutputStream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void setErrorStream(OutputStream errorStream) {
        }

        @Override
        public void setExitCallback(ExitCallback exitCallback) {
            this.exitCallback = exitCallback;
        }

        @Override
        public void start(ChannelSession channel, Environment environment) throws IOException {
            outputStream.write(("executed: " + command).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            exitCallback.onExit(0);
        }

        @Override
        public void destroy(ChannelSession channel) {
        }
    }
}
