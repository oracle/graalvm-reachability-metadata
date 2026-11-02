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
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.angus.mail.util.MailSSLSocketFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * SMTP over TLS transport: an application sends a message through Angus Mail's SMTPS provider
 * and reads the delivered message from the mailbox.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmtpsDeliveryTest {

    private static final String GREENMAIL_IMAGE = "greenmail/standalone:2.1.0-alpha-4";
    private static final String MAILBOX_PASSWORD = "password";

    private final String mailboxUser = "user-" + UUID.randomUUID();
    private final String mailbox = mailboxUser + "@localhost";
    private final String containerName = "real-use-angus-mail-" + UUID.randomUUID();

    private int smtpsPort;
    private int pop3Port;

    @BeforeAll
    void startMailServer() throws Exception {
        smtpsPort = freePort();
        pop3Port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-e", "GREENMAIL_OPTS=-Dgreenmail.setup.test.all -Dgreenmail.hostname=0.0.0.0"
                        + " -Dgreenmail.auth.disabled -Dgreenmail.users=" + mailboxUser + ":"
                        + MAILBOX_PASSWORD + "@localhost -Dgreenmail.users.login=email",
                "-p", smtpsPort + ":3465", "-p", pop3Port + ":3110", GREENMAIL_IMAGE)
                .inheritIO()
                .start();
        assertTrue(run.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, run.exitValue());
        awaitSecurePort(smtpsPort);
        awaitPort(pop3Port);
    }

    @AfterAll
    void stopMailServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        assertTrue(remove.waitFor(60, TimeUnit.SECONDS));
    }

    @Test
    void deliversMessageThroughTlsTransport() throws Exception {
        MailSSLSocketFactory socketFactory = trustedSocketFactory();
        Properties smtpProperties = new Properties();
        smtpProperties.setProperty("mail.transport.protocol", "smtps");
        smtpProperties.setProperty("mail.smtps.host", "127.0.0.1");
        smtpProperties.setProperty("mail.smtps.port", Integer.toString(smtpsPort));
        smtpProperties.setProperty("mail.smtps.connectiontimeout", "10000");
        smtpProperties.setProperty("mail.smtps.timeout", "10000");
        smtpProperties.put("mail.smtps.ssl.socketFactory", socketFactory);
        smtpProperties.setProperty("mail.smtps.ssl.socketFactory.fallback", "false");
        smtpProperties.setProperty("mail.smtps.ssl.protocols", "TLSv1.2");
        smtpProperties.setProperty("mail.smtps.ssl.checkserveridentity", "false");
        Session smtpSession = Session.getInstance(smtpProperties);

        MimeMessage outbound = new MimeMessage(smtpSession);
        outbound.setFrom(new InternetAddress("sender@example.test"));
        outbound.setRecipient(Message.RecipientType.TO, new InternetAddress(mailbox));
        outbound.setSubject("Encrypted build report");
        outbound.setText("The TLS mail worker completed its delivery.", "UTF-8");
        try (Transport transport = smtpSession.getTransport("smtps")) {
            transport.connect();
            transport.sendMessage(outbound, outbound.getAllRecipients());
        }

        Properties pop3Properties = new Properties();
        pop3Properties.setProperty("mail.pop3.host", "127.0.0.1");
        pop3Properties.setProperty("mail.pop3.port", Integer.toString(pop3Port));
        pop3Properties.setProperty("mail.pop3.connectiontimeout", "10000");
        pop3Properties.setProperty("mail.pop3.timeout", "10000");
        Session pop3Session = Session.getInstance(pop3Properties);
        try (Store store = pop3Session.getStore("pop3")) {
            store.connect(mailbox, MAILBOX_PASSWORD);
            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_ONLY);
                Message[] messages = inbox.getMessages();
                assertEquals(1, messages.length);
                assertEquals("Encrypted build report", messages[0].getSubject());
                assertEquals("The TLS mail worker completed its delivery.",
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
                Thread.sleep(250);
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
                return;
            } catch (IOException exception) {
                lastFailure = exception;
                Thread.sleep(250);
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
