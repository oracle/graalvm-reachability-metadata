/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Folder;
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
import org.eclipse.angus.mail.imap.AppendUID;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * IMAP folder archiving: an application creates a mailbox, archives a delivered message there,
 * and retrieves it by the UID that Angus Mail reports for the appended message.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImapFolderArchiveTest {

    private static final String GREENMAIL_IMAGE = "greenmail/standalone:2.1.0-alpha-4";
    private static final String MAILBOX_PASSWORD = "password";

    private final String mailboxUser = "user-" + UUID.randomUUID();
    private final String mailbox = mailboxUser + "@localhost";
    private final String archiveName = "Archive-" + UUID.randomUUID();
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
    void archivesMessageAndReadsItByAppendUid() throws Exception {
        Properties smtpProperties = new Properties();
        smtpProperties.setProperty("mail.smtp.host", "127.0.0.1");
        smtpProperties.setProperty("mail.smtp.port", Integer.toString(smtpPort));
        smtpProperties.setProperty("mail.smtp.connectiontimeout", "10000");
        smtpProperties.setProperty("mail.smtp.timeout", "10000");
        Session smtpSession = Session.getInstance(smtpProperties);

        MimeMessage outbound = new MimeMessage(smtpSession);
        outbound.setFrom(new InternetAddress("sender@example.test"));
        outbound.setRecipient(Message.RecipientType.TO, new InternetAddress(mailbox));
        outbound.setSubject("Archive this report");
        outbound.setText("The archive worker stored this report.", "UTF-8");
        Transport.send(outbound);

        Properties imapProperties = new Properties();
        imapProperties.setProperty("mail.imap.host", "127.0.0.1");
        imapProperties.setProperty("mail.imap.port", Integer.toString(imapPort));
        imapProperties.setProperty("mail.imap.connectiontimeout", "10000");
        imapProperties.setProperty("mail.imap.timeout", "10000");
        Session imapSession = Session.getInstance(imapProperties);

        try (Store store = connectToImap(imapSession)) {
            IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
            IMAPFolder archive = (IMAPFolder) store.getFolder(archiveName);
            assertTrue(archive.create(Folder.HOLDS_MESSAGES));
            try {
                inbox.open(Folder.READ_WRITE);
                Message delivered = inbox.getMessage(1);
                assertEquals("Archive this report", delivered.getSubject());
                long inboxUid = inbox.getUID(delivered);
                assertTrue(inboxUid > 0);

                AppendUID[] appended = archive.appendUIDMessages(new Message[] {delivered});
                assertEquals(1, appended.length);
                assertEquals(archive.getUIDValidity(), appended[0].uidvalidity);
                assertTrue(appended[0].uid > 0);

                delivered.setFlag(jakarta.mail.Flags.Flag.DELETED, true);
                inbox.expunge();
                inbox.close(false);

                archive.open(Folder.READ_ONLY);
                Message archived = archive.getMessageByUID(appended[0].uid);
                assertNotNull(archived);
                assertEquals("Archive this report", archived.getSubject());
                assertEquals("The archive worker stored this report.",
                        archived.getContent().toString().strip());
            } finally {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
                if (archive.isOpen()) {
                    archive.close(false);
                }
                archive.delete(true);
            }
        }
    }

    private Store connectToImap(Session session) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        MessagingException lastFailure = null;
        while (System.nanoTime() < deadline) {
            Store store = session.getStore("imap");
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
        throw new IllegalStateException("Mail server did not accept IMAP credentials", lastFailure);
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
