/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.util.MailLogger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class IMAPProtocolTest {
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(60)
    public void saslPlainLoginRespondsToServerChallenge() throws Exception {
        assertSaslPlainLogin(false);
    }

    @Test
    @Timeout(60)
    public void saslPlainLoginSendsInitialResponseWhenAdvertised() throws Exception {
        assertSaslPlainLogin(true);
    }

    private static void assertSaslPlainLogin(boolean initialResponse) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.imap.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.timeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.writetimeout", Integer.toString(TIMEOUT_MILLIS));
        MailLogger logger = new MailLogger(IMAPProtocolTest.class, "IMAP", false, System.out);

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(TIMEOUT_MILLIS);
            Future<List<String>> conversation = executor.submit(() -> serveSasl(server, initialResponse));
            IMAPProtocol protocol = new IMAPProtocol("imap", server.getInetAddress().getHostAddress(),
                    server.getLocalPort(), properties, false, logger);
            try {
                assertThat(protocol.isAuthenticated()).isFalse();
                assertThat(protocol.hasCapability("AUTH=PLAIN")).isTrue();
                protocol.sasllogin(new String[] {"UNSUPPORTED", "PLAIN"}, null, "mailbox", "reader", "secret");
                assertThat(protocol.isAuthenticated()).isTrue();
                protocol.noop();
                protocol.logout();
                assertThat(protocol.isAuthenticated()).isFalse();
            } finally {
                protocol.disconnect();
            }
            assertThat(conversation.get(30, TimeUnit.SECONDS)).containsExactly("AUTHENTICATE", "NOOP", "LOGOUT");
        }
    }

    private static List<String> serveSasl(ServerSocket server, boolean initialResponse) throws IOException {
        List<String> commands = new ArrayList<>();
        try (Socket socket = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            String capabilities = "IMAP4rev1 AUTH=PLAIN" + (initialResponse ? " SASL-IR" : "");
            reply(writer, "* OK [CAPABILITY " + capabilities + "] mailbox ready");
            boolean authenticated = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
                String tag = parts[0];
                String command = parts[1];
                commands.add(command);
                switch (command) {
                    case "AUTHENTICATE" -> {
                        assertThat(authenticated).isFalse();
                        assertThat(parts).hasSize(3);
                        String encoded;
                        if (initialResponse) {
                            String[] arguments = parts[2].split(" ", 2);
                            assertThat(arguments).hasSize(2);
                            assertThat(arguments[0]).isEqualTo("PLAIN");
                            encoded = arguments[1];
                        } else {
                            assertThat(parts[2]).isEqualTo("PLAIN");
                            reply(writer, "+ ");
                            encoded = reader.readLine();
                        }
                        assertThat(encoded).isNotNull();
                        String credentials = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                        assertThat(credentials).isEqualTo("mailbox\0reader\0secret");
                        authenticated = true;
                    }
                    case "NOOP" -> assertThat(authenticated).isTrue();
                    case "LOGOUT" -> {
                        assertThat(authenticated).isTrue();
                        reply(writer, "* BYE closing connection");
                        reply(writer, tag + " OK LOGOUT completed");
                        return commands;
                    }
                    default -> throw new IOException("Unexpected IMAP command: " + line);
                }
                reply(writer, tag + " OK " + command + " completed");
            }
            throw new IOException("IMAP client disconnected before LOGOUT");
        }
    }

    private static void reply(BufferedWriter writer, String response) throws IOException {
        writer.write(response);
        writer.write("\r\n");
        writer.flush();
    }
}
