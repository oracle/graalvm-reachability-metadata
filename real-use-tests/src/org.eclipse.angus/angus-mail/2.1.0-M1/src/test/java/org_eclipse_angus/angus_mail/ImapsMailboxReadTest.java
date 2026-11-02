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
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.util.MailSSLSocketFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * IMAP over TLS mailbox reading: an application delivers a message and reads it through Angus
 * Mail's secure IMAP provider.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImapsMailboxReadTest {

    private static final String GREENMAIL_IMAGE = "greenmail/standalone:2.1.0-alpha-4";
    private static final String MAILBOX_PASSWORD = "password";

    private final String mailboxUser = "user-" + UUID.randomUUID();
    private final String mailbox = mailboxUser + "@localhost";
    private final String containerName = "real-use-angus-mail-" + UUID.randomUUID();

    private int smtpPort;
    private int imapsPort;

    @BeforeAll
    void startMailServer() throws Exception {
        smtpPort = freePort();
        imapsPort = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-e", "GREENMAIL_OPTS=-Dgreenmail.setup.test.all -Dgreenmail.hostname=0.0.0.0"
                        + " -Dgreenmail.auth.disabled -Dgreenmail.users=" + mailboxUser + ":"
                        + MAILBOX_PASSWORD + "@localhost -Dgreenmail.users.login=email",
                "-p", smtpPort + ":3025", "-p", imapsPort + ":3993", GREENMAIL_IMAGE)
                .inheritIO()
                .start();
        assertTrue(run.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, run.exitValue());
        awaitPort(smtpPort);
        awaitSecurePort(imapsPort);
    }

    @AfterAll
    void stopMailServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        assertTrue(remove.waitFor(60, TimeUnit.SECONDS));
    }

    @Test
    void readsDeliveredMessageThroughImaps() throws Exception {
        Properties smtpProperties = new Properties();
        smtpProperties.setProperty("mail.smtp.host", "127.0.0.1");
        smtpProperties.setProperty("mail.smtp.port", Integer.toString(smtpPort));
        smtpProperties.setProperty("mail.smtp.connectiontimeout", "10000");
        smtpProperties.setProperty("mail.smtp.timeout", "10000");
        Session smtpSession = Session.getInstance(smtpProperties);

        MimeMessage outbound = new MimeMessage(smtpSession);
        outbound.setFrom(new InternetAddress("sender@example.test"));
        outbound.setRecipient(Message.RecipientType.TO, new InternetAddress(mailbox));
        outbound.setSubject("Secure build report");
        outbound.setText("The secure IMAP worker read this report.", "UTF-8");
        Transport.send(outbound);

        MailSSLSocketFactory socketFactory = trustedSocketFactory();
        Properties imapsProperties = new Properties();
        imapsProperties.setProperty("mail.imaps.host", "127.0.0.1");
        imapsProperties.setProperty("mail.imaps.port", Integer.toString(imapsPort));
        imapsProperties.setProperty("mail.imaps.connectiontimeout", "10000");
        imapsProperties.setProperty("mail.imaps.timeout", "10000");
        imapsProperties.put("mail.imaps.ssl.socketFactory", socketFactory);
        imapsProperties.setProperty("mail.imaps.ssl.socketFactory.fallback", "false");
        imapsProperties.setProperty("mail.imaps.ssl.protocols", "TLSv1.2");
        imapsProperties.setProperty("mail.imaps.ssl.checkserveridentity", "false");
        Session imapsSession = Session.getInstance(imapsProperties);

        try (Store store = imapsSession.getStore("imaps")) {
            IMAPStore imapStore = (IMAPStore) store;
            imapStore.connect(mailbox, MAILBOX_PASSWORD);
            try (Folder folder = imapStore.getFolder("INBOX")) {
                IMAPFolder inbox = (IMAPFolder) folder;
                inbox.open(Folder.READ_ONLY);
                Message[] messages = inbox.getMessages();
                assertEquals(1, messages.length);
                assertEquals("Secure build report", messages[0].getSubject());
                assertEquals("The secure IMAP worker read this report.",
                        messages[0].getContent().toString().strip());
            }
        }
    }

    private MailSSLSocketFactory trustedSocketFactory() throws GeneralSecurityException {
        MailSSLSocketFactory socketFactory = new MailSSLSocketFactory("TLSv1.2");
        socketFactory.setTrustManagers(new TrustAllManager());
        socketFactory.setTrustAllHosts(true);
        return socketFactory;
    }

    private void awaitSecurePort(int port) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                MailSSLSocketFactory socketFactory = trustedSocketFactory();
                try (SSLSocket socket = (SSLSocket) socketFactory.createSocket("127.0.0.1", port)) {
                    socket.setEnabledProtocols(new String[] {"TLSv1.2"});
                    socket.startHandshake();
                    return;
                }
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.yield();
            }
        }
        throw new IllegalStateException("Mail server did not complete TLS handshake on port " + port,
                lastFailure);
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

    private static final class TrustAllManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
