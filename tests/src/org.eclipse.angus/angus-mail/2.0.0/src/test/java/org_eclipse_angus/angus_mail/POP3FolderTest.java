/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

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

public class POP3FolderTest {
    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    public void configuredMessageClassIsCreatedWhenMessageIsRead() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            Future<Void> served = executor.submit(() -> serveSingleMessageMailbox(serverSocket));

            POP3Store store = new POP3Store(newSessionWithMessageClass(), new URLName("pop3://localhost"));
            Folder folder = null;
            boolean folderOpened = false;
            try {
                store.connect(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort(), "user",
                        "password");
                folder = store.getFolder("INBOX");
                folder.open(Folder.READ_ONLY);
                folderOpened = true;

                Message message = folder.getMessage(1);

                assertThat(message).isInstanceOf(RecordingPOP3Message.class);
                assertThat(((RecordingPOP3Message) message).constructedMessageNumber()).isEqualTo(1);
            } finally {
                if (folderOpened) {
                    folder.close(false);
                }
                store.close();
            }
            served.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Session newSessionWithMessageClass() {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.pop3.timeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.pop3.message.class", RecordingPOP3Message.class.getName());
        return Session.getInstance(properties);
    }

    private static Void serveSingleMessageMailbox(ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.ISO_8859_1));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.ISO_8859_1))) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            writeLine(writer, "+OK test POP3 server ready");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CAPA")) {
                    writeLine(writer, "-ERR capabilities unavailable");
                } else if (line.startsWith("USER ") || line.startsWith("PASS ")) {
                    writeLine(writer, "+OK");
                } else if (line.equals("STAT")) {
                    writeLine(writer, "+OK 1 42");
                } else if (line.equals("QUIT")) {
                    writeLine(writer, "+OK goodbye");
                    return null;
                } else {
                    writeLine(writer, "-ERR unexpected command");
                }
            }
        }
        return null;
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    public static final class RecordingPOP3Message extends POP3Message {
        private final int constructedMessageNumber;

        public RecordingPOP3Message(Folder folder, int messageNumber) throws MessagingException {
            super(folder, messageNumber);
            this.constructedMessageNumber = messageNumber;
        }

        public int constructedMessageNumber() {
            return constructedMessageNumber;
        }
    }
}
