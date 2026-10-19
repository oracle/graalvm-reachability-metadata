/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streams archive files through Artemis large messages: bodies above `minLargeMessageSize`
 * sent from an `InputStream` with `JMS_AMQ_InputStream`, saved to disk with
 * `JMS_AMQ_SaveStream`, read back in chunks, and compressed with `compressLargeMessage`.
 *
 * A records office ships scanned documents and CSV exports to an archive service. Scans go
 * out straight from the file on disk and are saved by the archiver to its own file without
 * holding the body in memory; exports travel compressed over a factory configured through
 * connector URL parameters, and a small cover note below the threshold travels as usual.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchiveLargeMessageStreamingTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String ARCHIVE = "records.archive";

    private static final int MIN_LARGE_MESSAGE_SIZE = 64 * 1024;

    private ActiveMQServer broker;

    private String url;

    private Path workDirectory;

    @BeforeAll
    void startBroker(@TempDir Path directory) throws Exception {
        workDirectory = directory;
        url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .setLargeMessagesDirectory(workDirectory.resolve("broker-large-messages").toString())
                .addAcceptorConfiguration("netty", url);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void streamsScannedDocumentFromDiskToDisk() throws Exception {
        Path scan = workDirectory.resolve("scan-2026-0042.tiff");
        byte[] content = new byte[3 * 1024 * 1024 + 517];
        new Random(42).nextBytes(content);
        Files.write(scan, content);

        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url)) {
            factory.setMinLargeMessageSize(MIN_LARGE_MESSAGE_SIZE);
            try (Connection connection = factory.createConnection()) {
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination archive = session.createQueue(ARCHIVE);

                try (MessageProducer producer = session.createProducer(archive);
                     InputStream file = Files.newInputStream(scan)) {
                    BytesMessage upload = session.createBytesMessage();
                    upload.setStringProperty("fileName", scan.getFileName().toString());
                    upload.setStringProperty("sha256", sha256(content));
                    upload.setObjectProperty("JMS_AMQ_InputStream", file);
                    producer.send(upload);
                }

                Path archived = workDirectory.resolve("archived.tiff");
                try (MessageConsumer consumer = session.createConsumer(archive);
                     OutputStream target = Files.newOutputStream(archived)) {
                    BytesMessage received = (BytesMessage) consumer.receive(10_000);
                    assertThat(received).isNotNull();
                    assertThat(received.getStringProperty("fileName")).isEqualTo("scan-2026-0042.tiff");
                    // Blocks until the last chunk of the body has been written to the file.
                    received.setObjectProperty("JMS_AMQ_SaveStream", target);
                    target.flush();
                    assertThat(received.getStringProperty("sha256")).isEqualTo(sha256(content));
                }

                byte[] stored = Files.readAllBytes(archived);
                assertThat(stored).hasSize(content.length);
                assertThat(sha256(stored)).isEqualTo(sha256(content));
            }
        }

        assertThat(awaitDrained()).isZero();
    }

    @Test
    void compressesCsvExportsConfiguredThroughConnectorUrl() throws Exception {
        String export = csvExport(60_000);
        byte[] exportBytes = export.getBytes(StandardCharsets.UTF_8);
        assertThat(exportBytes.length).isGreaterThan(20 * MIN_LARGE_MESSAGE_SIZE);
        String coverNote = "Quarterly export of the residents register";

        String compressingUrl = url + "?minLargeMessageSize=" + MIN_LARGE_MESSAGE_SIZE
                + "&compressLargeMessage=true&consumerWindowSize=0";
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(compressingUrl)) {
            assertThat(factory.getMinLargeMessageSize()).isEqualTo(MIN_LARGE_MESSAGE_SIZE);
            assertThat(factory.isCompressLargeMessage()).isTrue();
            assertThat(factory.getConsumerWindowSize()).isZero();

            try (Connection connection = factory.createConnection()) {
                connection.start();
                Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                Destination archive = session.createQueue(ARCHIVE);

                try (MessageProducer producer = session.createProducer(archive)) {
                    BytesMessage note = session.createBytesMessage();
                    note.setStringProperty("kind", "note");
                    note.writeUTF(coverNote);
                    producer.send(note);

                    BytesMessage csv = session.createBytesMessage();
                    csv.setStringProperty("kind", "export");
                    csv.setStringProperty("fileName", "residents.csv");
                    csv.setIntProperty("rows", 60_000);
                    csv.writeBytes(exportBytes);
                    producer.send(csv);
                }

                try (MessageConsumer consumer = session.createConsumer(archive)) {
                    BytesMessage note = (BytesMessage) consumer.receive(10_000);
                    assertThat(note).isNotNull();
                    assertThat(note.getStringProperty("kind")).isEqualTo("note");
                    assertThat(note.readUTF()).isEqualTo(coverNote);

                    BytesMessage csv = (BytesMessage) consumer.receive(10_000);
                    assertThat(csv).isNotNull();
                    assertThat(csv.getStringProperty("kind")).isEqualTo("export");
                    assertThat(csv.getStringProperty("fileName")).isEqualTo("residents.csv");
                    assertThat(csv.getIntProperty("rows")).isEqualTo(60_000);

                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8 * 1024];
                    int read;
                    while ((read = csv.readBytes(chunk)) > 0) {
                        body.write(chunk, 0, read);
                    }
                    String restored = body.toString(StandardCharsets.UTF_8);
                    assertThat(restored).isEqualTo(export);
                    assertThat(restored.lines()).hasSize(60_001)
                            .first().isEqualTo("id,name,district,registered");
                    csv.acknowledge();
                    assertThat(consumer.receiveNoWait()).isNull();
                }
            }
        }

        assertThat(awaitDrained()).isZero();
    }

    private static String csvExport(int rows) {
        StringBuilder csv = new StringBuilder("id,name,district,registered\n");
        String[] districts = {"harbour", "old-town", "hillside", "riverside"};
        for (int row = 1; row <= rows; row++) {
            csv.append(row).append(",resident-").append(row).append(',')
                    .append(districts[row % districts.length]).append(",2026-0")
                    .append(row % 9 + 1).append("-1").append(row % 10).append('\n');
        }
        return csv.toString();
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private long awaitDrained() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long remaining = pending();
        while (remaining > 0 && System.nanoTime() < deadline) {
            Thread.sleep(50);
            remaining = pending();
        }
        return remaining;
    }

    private long pending() {
        // The broker auto-deletes the auto-created queue once it is empty and unconsumed.
        Queue queue = broker.locateQueue(ARCHIVE);
        return queue == null ? 0 : queue.getMessageCount();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
