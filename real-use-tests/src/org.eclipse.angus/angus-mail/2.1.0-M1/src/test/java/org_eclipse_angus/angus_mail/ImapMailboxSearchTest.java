/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SubjectTerm;
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
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * IMAP mailbox search: an application delivers messages and searches, flags, and rereads a mailbox
 * through Angus Mail's IMAP provider.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImapMailboxSearchTest {

    private static final String GREENMAIL_IMAGE = "greenmail/standalone:2.1.0-alpha-4";
    private static final String MAILBOX_PASSWORD = "password";

    private final String mailboxUser = "user-" + UUID.randomUUID();
    private final String mailbox = mailboxUser + "@localhost";
    private final String containerName = "real-use-angus-mail-" + UUID.randomUUID();

    private int smtpPort;
    private int imapPort;

    @BeforeAll
    void startMailServer() throws Exception {
        smtpPort = freePort();
        imapPort = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-e", "GREENMAIL_OPTS=-Dgreenmail.setup.test.all -Dgreenmail.hostname=0.0.0.0"
                        + " -Dgreenmail.auth.disabled -Dgreenmail.users=" + mailboxUser + ":"
                        + MAILBOX_PASSWORD + "@localhost -Dgreenmail.users.login=email",
                "-p", smtpPort + ":3025", "-p", imapPort + ":3143", GREENMAIL_IMAGE)
                .inheritIO()
                .start();
        assertTrue(run.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, run.exitValue());
        awaitPort(smtpPort);
        awaitPort(imapPort);
    }

    @AfterAll
    void stopMailServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        assertTrue(remove.waitFor(60, TimeUnit.SECONDS));
    }

    @Test
    void searchesAndFlagsMessagesInImapMailbox() throws Exception {
        Properties smtpProperties = new Properties();
        smtpProperties.setProperty("mail.smtp.host", "127.0.0.1");
        smtpProperties.setProperty("mail.smtp.port", Integer.toString(smtpPort));
        smtpProperties.setProperty("mail.smtp.connectiontimeout", "10000");
        smtpProperties.setProperty("mail.smtp.timeout", "10000");
        Session smtpSession = Session.getInstance(smtpProperties);
        sendMessage(smtpSession, "Build completed", "The native mail worker completed its delivery.");
        sendMessage(smtpSession, "Unrelated notification", "This message should not match the search.");

        Properties imapProperties = new Properties();
        imapProperties.setProperty("mail.imap.host", "127.0.0.1");
        imapProperties.setProperty("mail.imap.port", Integer.toString(imapPort));
        imapProperties.setProperty("mail.imap.connectiontimeout", "10000");
        imapProperties.setProperty("mail.imap.timeout", "10000");
        Session imapSession = Session.getInstance(imapProperties);
        try (Store store = imapSession.getStore("imap")) {
            store.connect(mailbox, MAILBOX_PASSWORD);
            try (Folder folder = store.getFolder("INBOX")) {
                IMAPFolder inbox = (IMAPFolder) folder;
                inbox.open(Folder.READ_WRITE);
                Message[] matches = inbox.search(new SubjectTerm("Build completed"));
                assertEquals(1, matches.length);
                assertEquals("The native mail worker completed its delivery.",
                        matches[0].getContent().toString().strip());

                inbox.setFlags(matches, new Flags(Flags.Flag.FLAGGED), true);
                assertTrue(matches[0].isSet(Flags.Flag.FLAGGED));
            }

            try (Folder reopenedFolder = store.getFolder("INBOX")) {
                IMAPFolder reopenedInbox = (IMAPFolder) reopenedFolder;
                reopenedInbox.open(Folder.READ_ONLY);
                Message[] flagged = reopenedInbox.search(new FlagTerm(new Flags(Flags.Flag.FLAGGED), true));
                assertEquals(1, flagged.length);
                assertEquals("Build completed", flagged[0].getSubject());
            }
        }
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
                Thread.sleep(250);
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
