/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sun.mail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JakartaMailTest {

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting email server ...");
        new ProcessBuilder("docker", "run", "--cidfile", "greenmail.cid", "-p", "3025:3025", "--rm", "-e",
                "GREENMAIL_OPTS=-Dgreenmail.setup.test.all -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.auth.disabled -Dgreenmail.verbose", "greenmail/standalone:2.1.0-alpha-3")
                .redirectOutput(new File("greenmail-stdout.txt"))
                .redirectError(new File("greenmail-stderr.txt")).start();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            String logs = FileUtils.readFileToString(new File("greenmail-stdout.txt"), StandardCharsets.UTF_8);
            return logs.contains("smtp.SmtpServer| Started") && logs.contains("pop3.Pop3Server| Started") && logs.contains("imap.ImapServer| Started");
        });
    }

    @AfterAll
    static void tearDown() throws InterruptedException, IOException {
        File cidFile = new File("greenmail.cid");
        String cid = FileUtils.readFileToString(cidFile, StandardCharsets.UTF_8);
        Files.delete(cidFile.toPath());
        new ProcessBuilder("docker", "kill", cid).start().waitFor();
        Files.delete(Path.of("greenmail-stdout.txt"));
        Files.delete(Path.of("greenmail-stderr.txt"));
    }

    @Test
    void sendMailWithSmtp() throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        properties.put("mail.smtp.port", "3025");
        Session session = Session.getInstance(properties);
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("alice@localhost"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("bob@localhost"));
        message.setSubject("This is a test");
        message.setText("Dear Bob, hello world! Alice.");
        Transport.send(message);
    }

    @Test
    void sendMailWithMultipart() throws MessagingException {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "localhost");
        properties.put("mail.smtp.port", "3025");
        Session session = Session.getInstance(properties);
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("alice@localhost"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("bob@localhost"));
        message.setSubject("This is a test");
        MimeMultipart mainPart = new MimeMultipart();
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText("Dear Bob, hello world! Alice.");
        mainPart.addBodyPart(bodyPart);
        message.setContent(mainPart, "multipart/mixed");
        Transport.send(message);
    }

    @Test
    void resources() {
        ClassLoader classLoader = getClass().getClassLoader();
        Assertions.assertThat(classLoader.getResource("META-INF/hk2-locator/default")).isNotNull();
        Assertions.assertThat(classLoader.getResource("META-INF/gfprobe-provider.xml")).isNotNull();
        Assertions.assertThat(classLoader.getResource("META-INF/javamail.charset.map")).isNotNull();
        Assertions.assertThat(classLoader.getResource("META-INF/javamail.default.address.map")).isNotNull();
        Assertions.assertThat(classLoader.getResource("META-INF/javamail.default.providers")).isNotNull();
        Assertions.assertThat(classLoader.getResource("META-INF/mailcap")).isNotNull();
    }

}
