/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.BlobMessage;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQBlobMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Moves scanned documents through blob messages with the file-system blob transfer strategy:
 * `BlobTransferPolicy` upload URLs set by setter and by the `jms.blobTransferPolicy.*` broker
 * URL option, `createBlobMessage` from a file, a stream and an existing URL, and consumer-side
 * download and deletion of the out-of-band payload.
 *
 * The blob name stays on the sending client, so the station forwards it as a message property.
 *
 * A scanning station hands large page images to an archive service. Only a small JMS message
 * travels over the OpenWire TCP transport; the bytes go to a shared drop folder that both sides
 * reach through a `file:` upload URL, and the archive deletes each upload once it has filed it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannedDocumentBlobTransferTest {

    private static final String SCANS = "archive.scans";

    private static final String PDF = "application/pdf";

    private BrokerService broker;

    private String brokerUrl;

    @TempDir
    Path workspace;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("scan-archive-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        brokerUrl = connector.getConnectUri().toString();
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void filesScannedPagesThroughSharedDropFolder() throws Exception {
        Path dropFolder = workspace.resolve("drop");
        String uploadUrl = dropFolder.toUri().toString();
        Path scanned = workspace.resolve("invoice-0042.pdf");
        byte[] invoice = pageImage("invoice-0042", 96 * 1024);
        Files.write(scanned, invoice);
        Path letterhead = workspace.resolve("letterhead.txt");
        Files.writeString(letterhead, "ACME Logistics, Dock Road 7", StandardCharsets.US_ASCII);

        ActiveMQConnectionFactory stationFactory = new ActiveMQConnectionFactory(brokerUrl);
        BlobTransferPolicy stationPolicy = new BlobTransferPolicy();
        stationPolicy.setUploadUrl(uploadUrl);
        stationPolicy.setBufferSize(4 * 1024);
        stationFactory.setBlobTransferPolicy(stationPolicy);
        ActiveMQConnectionFactory archiveFactory = new ActiveMQConnectionFactory(
                brokerUrl + "?jms.blobTransferPolicy.uploadUrl=" + uploadUrl);

        Connection station = stationFactory.createConnection();
        Connection archive = archiveFactory.createConnection();
        try {
            archive.start();
            assertThat(((ActiveMQConnection) archive).getBlobTransferPolicy().getUploadUrl())
                    .isEqualTo(uploadUrl);
            ActiveMQSession stationSession =
                    (ActiveMQSession) station.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session archiveSession = archive.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            Queue scans = stationSession.createQueue(SCANS);
            MessageProducer producer = stationSession.createProducer(scans);
            MessageConsumer consumer = archiveSession.createConsumer(scans);

            BlobMessage fromFile = stationSession.createBlobMessage(scanned.toFile());
            fromFile.setMimeType(PDF);
            fromFile.setStringProperty("batch", "B-7");
            assertThat(fromFile.getName()).isEqualTo("invoice-0042.pdf");
            fromFile.setStringProperty("fileName", fromFile.getName());
            producer.send(fromFile);
            Path uploaded = dropFolder.resolve(fromFile.getJMSMessageID().replace(':', '_'));
            assertThat(uploadsIn(dropFolder)).isEqualTo(1);
            assertThat(uploaded).hasBinaryContent(invoice);

            byte[] receipt = pageImage("receipt-0043", 10 * 1024 + 17);
            BlobMessage fromStream =
                    stationSession.createBlobMessage(new ByteArrayInputStream(receipt));
            fromStream.setStringProperty("batch", "B-7");
            producer.send(fromStream);

            BlobMessage fromUrl = stationSession.createBlobMessage(letterhead.toUri().toURL());
            fromUrl.setMimeType("text/plain");
            producer.send(fromUrl);
            assertThat(fromUrl.getURL()).isEqualTo(letterhead.toUri().toURL());
            assertThat(uploadsIn(dropFolder)).isEqualTo(2);

            BlobMessage filedInvoice = receiveBlob(consumer);
            assertThat(filedInvoice.getJMSMessageID()).isEqualTo(fromFile.getJMSMessageID());
            assertThat(filedInvoice.getName()).isNull();
            assertThat(filedInvoice.getStringProperty("fileName")).isEqualTo("invoice-0042.pdf");
            assertThat(filedInvoice.getMimeType()).isEqualTo(PDF);
            assertThat(filedInvoice.getStringProperty("batch")).isEqualTo("B-7");
            assertThat(filedInvoice.getURL()).isEqualTo(uploaded.toUri().toURL());
            assertThat(readAll(filedInvoice)).isEqualTo(invoice);

            BlobMessage filedReceipt = receiveBlob(consumer);
            assertThat(filedReceipt.getMimeType()).isEqualTo("application/octet-stream");
            assertThat(readAll(filedReceipt)).isEqualTo(receipt);

            BlobMessage filedLetterhead = receiveBlob(consumer);
            assertThat(filedLetterhead.getMimeType()).isEqualTo("text/plain");
            assertThat(new String(readAll(filedLetterhead), StandardCharsets.US_ASCII))
                    .isEqualTo("ACME Logistics, Dock Road 7");
            filedLetterhead.acknowledge();

            ((ActiveMQBlobMessage) filedInvoice).deleteFile();
            ((ActiveMQBlobMessage) filedReceipt).deleteFile();
            assertThat(uploadsIn(dropFolder)).isZero();
            assertThat(scanned).hasBinaryContent(invoice);
            assertThat(letterhead).exists();
            assertThatThrownBy(() -> readAll(filedInvoice)).isInstanceOf(IOException.class);
            assertThat(consumer.receiveNoWait()).isNull();
        } finally {
            station.close();
            archive.close();
        }
    }

    private static BlobMessage receiveBlob(MessageConsumer consumer) throws Exception {
        Message received = consumer.receive(10_000);
        assertThat(received).isInstanceOf(BlobMessage.class);
        return (BlobMessage) received;
    }

    private static byte[] readAll(BlobMessage message) throws Exception {
        try (InputStream in = message.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static long uploadsIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    /** Builds a deterministic stand-in for a scanned page image of the given size. */
    private static byte[] pageImage(String documentId, int size) {
        byte[] seed = documentId.getBytes(StandardCharsets.US_ASCII);
        byte[] image = new byte[size];
        for (int i = 0; i < size; i++) {
            image[i] = (byte) (seed[i % seed.length] * 31 + i);
        }
        return image;
    }
}
