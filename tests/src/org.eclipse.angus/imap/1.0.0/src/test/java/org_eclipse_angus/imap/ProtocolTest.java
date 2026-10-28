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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ProtocolTest {
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(60)
    public void tlsSocketWithoutChannelSupportsProtocolCommands() throws Exception {
        SSLContext context = tlsContext();
        Properties properties = new Properties();
        properties.put("mail.imap.ssl.socketFactory", context.getSocketFactory());
        properties.setProperty("mail.imap.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.timeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.ssl.checkserveridentity", "true");
        MailLogger logger = new MailLogger(ProtocolTest.class, "IMAP", false, System.out);

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                        .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(TIMEOUT_MILLIS);
            Future<List<String>> conversation = executor.submit(() -> serveTls(server));
            IMAPProtocol protocol = new IMAPProtocol("imap", "localhost", server.getLocalPort(),
                    properties, true, logger);
            try {
                assertThat(protocol.isSSL()).isTrue();
                assertThat(protocol.getChannel()).isNull();
                assertThat(protocol.getInetAddress().isLoopbackAddress()).isTrue();
                assertThat(protocol.hasCapability("IMAP4rev1")).isTrue();
                protocol.noop();
                protocol.logout();
            } finally {
                protocol.disconnect();
            }
            assertThat(conversation.get(30, TimeUnit.SECONDS)).containsExactly("NOOP", "LOGOUT");
        }
    }

    @Test
    @Timeout(60)
    public void tlsChannelIsRecoveredFromNamedSocketField() throws Exception {
        assertTlsChannelIsRecovered(true);
    }

    @Test
    @Timeout(60)
    public void tlsChannelIsRecoveredFromAssignableSocketField() throws Exception {
        assertTlsChannelIsRecovered(false);
    }

    private static void assertTlsChannelIsRecovered(boolean namedField) throws Exception {
        SSLContext context = tlsContext();
        TlsChannelSocketFactory factory = new TlsChannelSocketFactory(context.getSocketFactory(), namedField);
        Properties properties = new Properties();
        properties.put("mail.imap.ssl.socketFactory", factory);
        properties.setProperty("mail.imap.usesocketchannels", "true");
        properties.setProperty("mail.imap.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imap.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.imap.timeout", Integer.toString(TIMEOUT_MILLIS));
        MailLogger logger = new MailLogger(ProtocolTest.class, "IMAP", false, System.out);

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                        .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(TIMEOUT_MILLIS);
            Future<List<String>> conversation = executor.submit(() -> serveTls(server));
            IMAPProtocol protocol = new IMAPProtocol("imap", "localhost", server.getLocalPort(),
                    properties, true, logger);
            try {
                assertThat(protocol.isSSL()).isTrue();
                assertThat(factory.channel()).isNotNull();
                assertThat(protocol.getChannel()).isSameAs(factory.channel());
                assertThat(protocol.getChannel().isConnected()).isTrue();
                assertThat(protocol.hasCapability("IMAP4rev1")).isTrue();
                protocol.noop();
                protocol.logout();
            } finally {
                protocol.disconnect();
            }
            assertThat(factory.channel().isOpen()).isFalse();
            assertThat(conversation.get(30, TimeUnit.SECONDS)).containsExactly("NOOP", "LOGOUT");
        }
    }

    private static SSLContext tlsContext() throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = ProtocolTest.class.getResourceAsStream("protocol-tls.p12")) {
            assertThat(input).isNotNull();
            keys.load(input, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, password);
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keys);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context;
    }

    private static List<String> serveTls(SSLServerSocket server) throws IOException {
        List<String> commands = new ArrayList<>();
        try (SSLSocket socket = (SSLSocket) server.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.startHandshake();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII));
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                            StandardCharsets.US_ASCII))) {
                reply(writer, "* OK [CAPABILITY IMAP4rev1] TLS mailbox ready");
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 2) {
                        throw new IOException("Invalid IMAP command: " + line);
                    }
                    String tag = parts[0];
                    String command = parts[1];
                    commands.add(command);
                    switch (command) {
                        case "NOOP" -> reply(writer, tag + " OK NOOP completed");
                        case "LOGOUT" -> {
                            reply(writer, "* BYE closing connection");
                            reply(writer, tag + " OK LOGOUT completed");
                            return commands;
                        }
                        default -> throw new IOException("Unexpected IMAP command: " + line);
                    }
                }
                throw new IOException("IMAP client disconnected before LOGOUT");
            }
        }
    }

    private static void reply(BufferedWriter writer, String response) throws IOException {
        writer.write(response);
        writer.write("\r\n");
        writer.flush();
    }
}
