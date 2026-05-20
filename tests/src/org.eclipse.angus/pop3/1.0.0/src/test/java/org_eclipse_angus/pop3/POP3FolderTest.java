/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.sun.mail.pop3.POP3Store;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class POP3FolderTest {
    @Test
    void createsConfiguredMessageClassWhenMessageIsFetched() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", POP3StoreFallbackMessage.class.getName());
        properties.setProperty("mail.pop3.disablecapa", "true");
        properties.setProperty("mail.pop3.connectiontimeout", "5000");
        properties.setProperty("mail.pop3.timeout", "5000");
        Session session = Session.getInstance(properties);

        try (TestPop3Server server = TestPop3Server.start()) {
            URLName url = new URLName("pop3", server.host(), server.port(), null, "user", "password");
            POP3Store store = new POP3Store(session, url);
            store.connect(server.host(), server.port(), "user", "password");
            try {
                Folder folder = store.getFolder("INBOX");
                folder.open(Folder.READ_ONLY);
                try {
                    Message message = folder.getMessage(1);

                    assertThat(message).isInstanceOf(POP3StoreFallbackMessage.class);
                    assertThat(message.getMessageNumber()).isEqualTo(1);
                } finally {
                    if (folder.isOpen()) {
                        folder.close(false);
                    }
                }
            } finally {
                store.close();
            }
        }
    }

    private static final class TestPop3Server implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<?> serverTask;

        private TestPop3Server(ServerSocket serverSocket, ExecutorService executor) {
            this.serverSocket = serverSocket;
            this.executor = executor;
            this.serverTask = executor.submit(this::serveOneClient);
        }

        static TestPop3Server start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            serverSocket.setSoTimeout(5000);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            return new TestPop3Server(serverSocket, executor);
        }

        String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            try {
                serverTask.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException exception) {
                serverTask.cancel(true);
                throw exception;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException(cause);
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        private void serveOneClient() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.ISO_8859_1));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.ISO_8859_1));

                writeLine(writer, "+OK test POP3 server ready");
                String line;
                while ((line = reader.readLine()) != null) {
                    String command = line.toUpperCase(Locale.ROOT);
                    if (command.startsWith("USER ") || command.startsWith("PASS ")) {
                        writeLine(writer, "+OK");
                    } else if ("STAT".equals(command)) {
                        writeLine(writer, "+OK 1 120");
                    } else if ("NOOP".equals(command) || "RSET".equals(command)) {
                        writeLine(writer, "+OK");
                    } else if ("QUIT".equals(command)) {
                        writeLine(writer, "+OK goodbye");
                        return;
                    } else {
                        writeLine(writer, "-ERR unsupported command");
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("POP3 test server failed", exception);
            }
        }

        private static void writeLine(BufferedWriter writer, String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }
    }
}
