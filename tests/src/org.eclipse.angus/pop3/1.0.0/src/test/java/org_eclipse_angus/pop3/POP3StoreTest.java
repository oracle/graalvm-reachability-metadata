/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.pop3.POP3Message;
import com.sun.mail.pop3.POP3Store;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class POP3StoreTest {
    private static final int TIMEOUT_MILLIS = 10_000;

    @Test
    @Timeout(60)
    public void configuredMessageClassCreatesMailboxMessages() throws Exception {
        withConfiguredMailbox(inbox -> {
            assertThat(inbox.getMessageCount()).isEqualTo(1);
            Message message = inbox.getMessage(1);
            assertThat(message).isInstanceOf(CustomPOP3Message.class);
            assertThat(message.getFolder()).isSameAs(inbox);
            assertThat(message.getMessageNumber()).isEqualTo(1);
        });
    }

    static void withConfiguredMailbox(MailboxAssertion assertion) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", CustomPOP3Message.class.getName());
        properties.setProperty("mail.pop3.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.pop3.timeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.pop3.writetimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.pop3.auth.mechanisms", "LOGIN");
        Session session = Session.getInstance(properties);

        try (ExecutorService executor = Executors.newSingleThreadExecutor();
                ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(TIMEOUT_MILLIS);
            Future<Void> conversation = executor.submit(() -> serveMailbox(server));

            try (POP3Store store = new POP3Store(session, new URLName("pop3://localhost"))) {
                store.connect(server.getInetAddress().getHostAddress(), server.getLocalPort(), "reader", "secret");
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);
                try {
                    assertion.verify(inbox);
                } finally {
                    inbox.close(false);
                }
            }
            conversation.get(30, TimeUnit.SECONDS);
        }
    }

    private static Void serveMailbox(ServerSocket server) throws IOException {
        try (Socket socket = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            reply(writer, "+OK mailbox ready");
            String command;
            while ((command = reader.readLine()) != null) {
                switch (command) {
                    case "CAPA" -> reply(writer, "+OK capabilities\r\nUSER\r\nTOP\r\n.");
                    case "USER reader", "PASS secret", "NOOP" -> reply(writer, "+OK");
                    case "STAT" -> reply(writer, "+OK 1 100");
                    case "TOP 1 0" -> reply(writer, """
                            +OK headers
                            From: sender@example.test
                            To: reader@example.test
                            Subject: Configured POP3 message

                            .""".replace("\n", "\r\n"));
                    case "QUIT" -> {
                        reply(writer, "+OK goodbye");
                        return null;
                    }
                    default -> throw new IOException("Unexpected POP3 command: " + command);
                }
            }
            throw new IOException("POP3 client disconnected before QUIT");
        }
    }

    private static void reply(BufferedWriter writer, String response) throws IOException {
        writer.write(response);
        writer.write("\r\n");
        writer.flush();
    }

    @FunctionalInterface
    interface MailboxAssertion {
        void verify(Folder inbox) throws Exception;
    }

    public static final class CustomPOP3Message extends POP3Message {
        public CustomPOP3Message(Folder folder, int messageNumber) throws MessagingException {
            super(folder, messageNumber);
        }
    }
}
