/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.smtp.SMTPTransport;
import jakarta.mail.Session;
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
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class SMTPTransportTest {
    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    public void saslEnabledAuthenticationCreatesSaslAuthenticator() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            Future<SmtpConversation> accepted = executor.submit(() -> acceptSmtpConnection(serverSocket));

            SMTPTransport transport = (SMTPTransport) newSaslEnabledSession().getTransport("smtp");
            try {
                transport.connect(serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort(),
                        "user", "password");

                assertThat(transport.isConnected()).isTrue();
            } finally {
                transport.close();
            }

            SmtpConversation conversation = accepted.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertThat(conversation.commands()).anySatisfy(command -> assertThat(command).startsWith("EHLO "));
            assertThat(conversation.commands()).anySatisfy(command -> assertThat(command).startsWith("AUTH PLAIN"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Session newSaslEnabledSession() {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.sasl.enable", "true");
        properties.setProperty("mail.smtp.sasl.mechanisms", "PLAIN");
        properties.setProperty("mail.smtp.auth.mechanisms", "PLAIN");
        properties.setProperty("mail.smtp.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
        properties.setProperty("mail.smtp.timeout", Integer.toString(TIMEOUT_MILLIS));
        return Session.getInstance(properties);
    }

    private static SmtpConversation acceptSmtpConnection(ServerSocket serverSocket) throws IOException {
        List<String> commands = new ArrayList<>();
        try (Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.US_ASCII))) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            writeLine(writer, "220 localhost test SMTP service ready");

            String line;
            while ((line = reader.readLine()) != null) {
                commands.add(line);
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO ")) {
                    writeLine(writer, "250-localhost");
                    writeLine(writer, "250-AUTH PLAIN LOGIN");
                    writeLine(writer, "250 OK");
                } else if (command.startsWith("AUTH ")) {
                    writeLine(writer, "235 2.7.0 Authentication successful");
                } else if (command.startsWith("QUIT")) {
                    writeLine(writer, "221 2.0.0 Bye");
                    break;
                } else {
                    writeLine(writer, "250 2.0.0 OK");
                }
            }
        }
        return new SmtpConversation(commands);
    }

    private static void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.write("\r\n");
        writer.flush();
    }

    private record SmtpConversation(List<String> commands) {
    }
}
