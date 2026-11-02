/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.eclipse.angus.mail.pop3.POP3Folder;
import org.eclipse.angus.mail.util.logging.MailHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Logging mail delivery: an application sends a buffered warning through Angus Mail's logging
 * handler and reads the resulting alert from its mailbox.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MailHandlerLogDeliveryTest {

    private static final String GREENMAIL_IMAGE = "greenmail/standalone:2.1.0-alpha-4";
    private static final String MAILBOX_PASSWORD = "password";

    private final String mailboxUser = "user-" + UUID.randomUUID();
    private final String mailbox = mailboxUser + "@localhost";
    private final String containerName = "real-use-angus-mail-" + UUID.randomUUID();

    private int smtpPort;
    private int pop3Port;

    @BeforeAll
    void startMailServer() throws Exception {
        smtpPort = freePort();
        pop3Port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-e", "GREENMAIL_OPTS=-Dgreenmail.setup.test.all -Dgreenmail.hostname=0.0.0.0"
                        + " -Dgreenmail.auth.disabled -Dgreenmail.users=" + mailboxUser + ":"
                        + MAILBOX_PASSWORD + "@localhost -Dgreenmail.users.login=email",
                "-p", smtpPort + ":3025", "-p", pop3Port + ":3110", GREENMAIL_IMAGE)
                .inheritIO()
                .start();
        assertTrue(run.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, run.exitValue());
        awaitPort(smtpPort);
        awaitPort(pop3Port);
    }

    @AfterAll
    void stopMailServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        assertTrue(remove.waitFor(60, TimeUnit.SECONDS));
    }

    @Test
    void deliversBufferedWarningAsMailAlert() throws Exception {
        Properties mailProperties = new Properties();
        mailProperties.setProperty("mail.transport.protocol", "smtp");
        mailProperties.setProperty("mail.smtp.host", "127.0.0.1");
        mailProperties.setProperty("mail.smtp.port", Integer.toString(smtpPort));
        mailProperties.setProperty("mail.smtp.connectiontimeout", "10000");
        mailProperties.setProperty("mail.smtp.timeout", "10000");
        mailProperties.setProperty("mail.from", "alerts@example.test");
        mailProperties.setProperty("mail.to", mailbox);

        MailHandler handler = new MailHandler();
        handler.setMailProperties(mailProperties);
        handler.setCapacity(10);
        handler.setLevel(Level.WARNING);
        handler.setSubject("Application warning");

        try {
            handler.publish(new LogRecord(Level.WARNING, "The report export is delayed."));
            handler.push();
        } finally {
            handler.close();
        }

        Properties pop3Properties = new Properties();
        pop3Properties.setProperty("mail.pop3.host", "127.0.0.1");
        pop3Properties.setProperty("mail.pop3.port", Integer.toString(pop3Port));
        pop3Properties.setProperty("mail.pop3.connectiontimeout", "10000");
        pop3Properties.setProperty("mail.pop3.timeout", "10000");
        Session session = Session.getInstance(pop3Properties);

        try (Store store = connectToPop3(session);
                Folder folder = store.getFolder("INBOX")) {
            POP3Folder inbox = (POP3Folder) folder;
            inbox.open(Folder.READ_ONLY);
            awaitMessage(inbox);
            Message alert = inbox.getMessage(1);
            assertEquals("Application warning", alert.getSubject());
            assertTrue(alert.getContent().toString().contains("The report export is delayed."));
        }
    }

    private Store connectToPop3(Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        Exception lastFailure = null;
        while (System.nanoTime() < deadline) {
            Store store = session.getStore("pop3");
            try {
                store.connect(mailbox, MAILBOX_PASSWORD);
                return store;
            } catch (Exception exception) {
                lastFailure = exception;
                try {
                    store.close();
                } catch (Exception closeException) {
                    exception.addSuppressed(closeException);
                }
                Thread.yield();
            }
        }
        throw new IllegalStateException("Mail server did not accept POP3 credentials", lastFailure);
    }

    private void awaitMessage(POP3Folder inbox) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (inbox.getMessageCount() > 0) {
                return;
            }
            Thread.yield();
        }
        throw new IllegalStateException("Mail server did not receive the alert");
    }

    private void awaitPort(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(10000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                if (reader.readLine() == null) {
                    throw new IOException("Mail server closed its greeting connection");
                }
                return;
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.yield();
            }
        }
        throw new IllegalStateException("Mail server did not open port " + port, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
