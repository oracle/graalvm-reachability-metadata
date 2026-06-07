/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.smtp.SMTPTransport;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

public class SMTPTransportTest {
    @Test
    void fallsBackToLoginAfterCreatingSaslAuthenticator() throws Exception {
        try (TestSmtpServer server = TestSmtpServer.start()) {
            Properties properties = new Properties();
            properties.setProperty("mail.smtp.auth", "true");
            properties.setProperty("mail.smtp.sasl.enable", "true");
            properties.setProperty("mail.smtp.sasl.mechanisms", "BOGUS");
            properties.setProperty("mail.smtp.auth.mechanisms", "LOGIN");
            properties.setProperty("mail.smtp.connectiontimeout", "5000");
            properties.setProperty("mail.smtp.timeout", "5000");
            properties.setProperty("mail.smtp.writetimeout", "5000");

            Session session = Session.getInstance(properties);
            Transport transport = session.getTransport("smtp");
            assertThat(transport).isInstanceOf(SMTPTransport.class);

            try {
                transport.connect("127.0.0.1", server.port(), "user", "password");
                assertThat(transport.isConnected()).isTrue();
            } finally {
                transport.close();
            }

            server.awaitCompletion();
            assertThat(server.commands()).anyMatch(command -> command.startsWith("AUTH LOGIN"));
        }
    }

    private static final class TestSmtpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<?> task;
        private final List<String> commands;
        private final AtomicReference<Exception> failure;
        private volatile Socket clientSocket;

        private TestSmtpServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            this.executor = Executors.newSingleThreadExecutor();
            this.commands = new CopyOnWriteArrayList<>();
            this.failure = new AtomicReference<>();
            this.task = executor.submit(this::serveOneClient);
        }

        static TestSmtpServer start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            return new TestSmtpServer(serverSocket);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        List<String> commands() {
            return commands;
        }

        void awaitCompletion() throws Exception {
            try {
                task.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new AssertionError(cause);
            } catch (TimeoutException exception) {
                throw new AssertionError("SMTP test server did not finish", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            }
            Exception exception = failure.get();
            if (exception != null) {
                throw exception;
            }
        }

        @Override
        public void close() throws Exception {
            closeClientSocket();
            serverSocket.close();
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("SMTP test server executor did not stop");
            }
        }

        private void serveOneClient() {
            try (Socket socket = serverSocket.accept()) {
                clientSocket = socket;
                socket.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));

                writeLine(writer, "220 localhost ESMTP ready");
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    commands.add(line);
                    String command = line.toUpperCase(Locale.ROOT);
                    if (command.startsWith("EHLO")) {
                        writeEhloResponse(writer);
                    } else if (command.startsWith("HELO")) {
                        writeLine(writer, "250 localhost");
                    } else if (command.startsWith("AUTH LOGIN")) {
                        authenticateWithLogin(reader, writer);
                    } else if (command.startsWith("NOOP") || command.startsWith("RSET")) {
                        writeLine(writer, "250 OK");
                    } else if (command.startsWith("QUIT")) {
                        writeLine(writer, "221 Bye");
                        return;
                    } else {
                        writeLine(writer, "250 OK");
                    }
                }
            } catch (Exception exception) {
                failure.compareAndSet(null, exception);
            } finally {
                closeClientSocket();
            }
        }

        private static void writeEhloResponse(BufferedWriter writer) throws IOException {
            writer.write("250-localhost");
            writer.write("\r\n");
            writer.write("250-AUTH BOGUS LOGIN");
            writer.write("\r\n");
            writer.write("250 OK");
            writer.write("\r\n");
            writer.flush();
        }

        private void authenticateWithLogin(BufferedReader reader, BufferedWriter writer) throws IOException {
            writeBase64Challenge(writer, "Username:");
            String encodedUser = reader.readLine();
            if (encodedUser != null) {
                commands.add(encodedUser);
            }
            writeBase64Challenge(writer, "Password:");
            String encodedPassword = reader.readLine();
            if (encodedPassword != null) {
                commands.add(encodedPassword);
            }
            writeLine(writer, "235 2.7.0 Authentication successful");
        }

        private static void writeBase64Challenge(BufferedWriter writer, String prompt) throws IOException {
            String challenge = Base64.getEncoder().encodeToString(prompt.getBytes(StandardCharsets.US_ASCII));
            writeLine(writer, "334 " + challenge);
        }

        private static void writeLine(BufferedWriter writer, String line) throws IOException {
            writer.write(line);
            writer.write("\r\n");
            writer.flush();
        }

        private void closeClientSocket() {
            Socket socket = clientSocket;
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException exception) {
                    failure.compareAndSet(null, exception);
                }
            }
        }
    }
}
