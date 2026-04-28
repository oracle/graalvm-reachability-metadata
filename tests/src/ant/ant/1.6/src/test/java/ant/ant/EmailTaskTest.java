/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.email.EmailTask;
import org.junit.jupiter.api.Test;

public class EmailTaskTest {
    @Test
    void sendsMessageWithUuEncodingThroughConfiguredSmtpServer() throws IOException, InterruptedException {
        try (RecordingSmtpServer server = RecordingSmtpServer.start()) {
            EmailTask task = newEmailTask(server.getPort());
            task.execute();

            server.awaitHandledMessage();
            assertThat(server.getCommands()).contains(
                "MAIL FROM: <sender@example.test>",
                "RCPT TO: <recipient@example.test>",
                "DATA",
                "QUIT");
            assertThat(server.getDataLines()).contains(
                "Subject: UU encoded mail",
                "Content-Type: text/plain",
                "message body sent through the uu mailer");
        }
    }

    private static EmailTask newEmailTask(int smtpPort) {
        Project project = new Project();
        project.init();

        EmailTask.Encoding encoding = new EmailTask.Encoding();
        encoding.setValue(EmailTask.UU);

        EmailTask task = new EmailTask();
        task.setProject(project);
        task.setTaskName("mail");
        task.setEncoding(encoding);
        task.setMailhost(InetAddress.getLoopbackAddress().getHostAddress());
        task.setMailport(smtpPort);
        task.setFrom("sender@example.test");
        task.setToList("recipient@example.test");
        task.setSubject("UU encoded mail");
        task.setMessage("message body sent through the uu mailer");
        return task;
    }

    private static final class RecordingSmtpServer implements Closeable {
        private final ServerSocket serverSocket;
        private final CountDownLatch handledMessage = new CountDownLatch(1);
        private final List<String> commands = Collections.synchronizedList(new ArrayList<>());
        private final List<String> dataLines = Collections.synchronizedList(new ArrayList<>());
        private volatile IOException failure;

        private RecordingSmtpServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
        }

        private static RecordingSmtpServer start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            RecordingSmtpServer server = new RecordingSmtpServer(serverSocket);
            Thread serverThread = new Thread(server::serve, "recording-smtp-server");
            serverThread.setDaemon(true);
            serverThread.start();
            return server;
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private List<String> getCommands() {
            return commands;
        }

        private List<String> getDataLines() {
            return dataLines;
        }

        private void awaitHandledMessage() throws InterruptedException, IOException {
            assertThat(handledMessage.await(5, TimeUnit.SECONDS)).isTrue();
            if (failure != null) {
                throw failure;
            }
        }

        private void serve() {
            try (Socket socket = serverSocket.accept();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                 BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                reply(writer, "220 localhost recording smtp server ready");
                boolean readingData = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (readingData) {
                        if (".".equals(line)) {
                            readingData = false;
                            reply(writer, "250 message accepted");
                        } else {
                            dataLines.add(line);
                        }
                        continue;
                    }

                    commands.add(line);
                    if (line.startsWith("HELO ") || line.startsWith("EHLO ")) {
                        reply(writer, "250 localhost");
                    } else if (line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:")) {
                        reply(writer, "250 ok");
                    } else if ("DATA".equals(line)) {
                        readingData = true;
                        reply(writer, "354 end data with <CR><LF>.<CR><LF>");
                    } else if ("QUIT".equals(line)) {
                        reply(writer, "221 bye");
                        handledMessage.countDown();
                        return;
                    } else {
                        reply(writer, "250 ok");
                    }
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    failure = e;
                    handledMessage.countDown();
                }
            }
        }

        private static void reply(BufferedWriter writer, String response) throws IOException {
            writer.write(response);
            writer.write("\r\n");
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
