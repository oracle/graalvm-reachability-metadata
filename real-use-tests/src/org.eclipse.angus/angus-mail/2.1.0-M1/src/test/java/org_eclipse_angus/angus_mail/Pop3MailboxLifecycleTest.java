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
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.angus.mail.pop3.POP3Folder;
import org.eclipse.angus.mail.pop3.POP3Store;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * POP3 mailbox lifecycle: an application reads new mail through Angus Mail, deletes processed
 * messages, and verifies that the mailbox retains unprocessed mail.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Pop3MailboxLifecycleTest {

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
    void processesAndDeletesOnlyCompletedPop3Messages() throws Exception {
        Properties smtpProperties = new Properties();
        smtpProperties.setProperty("mail.smtp.host", "127.0.0.1");
        smtpProperties.setProperty("mail.smtp.port", Integer.toString(smtpPort));
        smtpProperties.setProperty("mail.smtp.connectiontimeout", "10000");
        smtpProperties.setProperty("mail.smtp.timeout", "10000");
        Session smtpSession = Session.getInstance(smtpProperties);
        sendMessage(smtpSession, "Processed report", "The worker handled this report.");
        sendMessage(smtpSession, "Pending report", "The worker will handle this report later.");

        Properties pop3Properties = new Properties();
        pop3Properties.setProperty("mail.pop3.host", "127.0.0.1");
        pop3Properties.setProperty("mail.pop3.port", Integer.toString(pop3Port));
        pop3Properties.setProperty("mail.pop3.connectiontimeout", "10000");
        pop3Properties.setProperty("mail.pop3.timeout", "10000");
        Session pop3Session = Session.getInstance(pop3Properties);
        POP3Store store = connectToPop3(pop3Session);
        POP3Folder inbox = (POP3Folder) store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        Message[] messages = inbox.getMessages();
        assertEquals(2, messages.length);
        assertEquals(2, inbox.getMessageCount());
        Message processed = messages[0];
        assertEquals("Processed report", processed.getSubject());
        assertEquals("The worker handled this report.", processed.getContent().toString().strip());
        assertTrue(inbox.getUID(processed).length() > 0);

        processed.setFlag(Flags.Flag.DELETED, true);
        assertTrue(processed.isSet(Flags.Flag.DELETED));
        inbox.close(true);
        store.close();

        try (Store reopenedStore = pop3Session.getStore("pop3")) {
            reopenedStore.connect(mailbox, MAILBOX_PASSWORD);
            try (Folder reopenedFolder = reopenedStore.getFolder("INBOX")) {
                reopenedFolder.open(Folder.READ_ONLY);
                Message[] remaining = reopenedFolder.getMessages();
                assertEquals(1, remaining.length);
                assertEquals("Pending report", remaining[0].getSubject());
                assertEquals("The worker will handle this report later.",
                        remaining[0].getContent().toString().strip());
            }
        }
    }

    private POP3Store connectToPop3(Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        MessagingException lastFailure = null;
        while (System.nanoTime() < deadline) {
            POP3Store store = (POP3Store) session.getStore("pop3");
            try {
                store.connect(mailbox, MAILBOX_PASSWORD);
                return store;
            } catch (MessagingException exception) {
                lastFailure = exception;
                try {
                    store.close();
                } catch (MessagingException closeException) {
                    exception.addSuppressed(closeException);
                }
                Thread.yield();
            }
        }
        throw new IllegalStateException("Mail server did not accept POP3 credentials", lastFailure);
    }

    private void sendMessage(Session session, String subject, String body) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("sender@example.test"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(mailbox));
        message.setSubject(subject);
        message.setText(body, "UTF-8");
        Transport.send(message);
    }

    private void awaitPort(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
