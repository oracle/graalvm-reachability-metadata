/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.io.Receiver;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Asynchronous request-body streaming with `Receiver.receivePartialBytes` archives database backups.
 *
 * A backup agent sends binary snapshots without knowing their length in advance. The receiver
 * pauses between chunks while a worker writes to disk, rather than collecting the complete body
 * in memory or blocking the I/O thread. Only the final callback closes and publishes the staged
 * archive, returning its length and SHA-256 receipt. Known-length and empty backups use the same
 * endpoint and must also finish correctly.
 */
@Timeout(60)
public class BackupRequestStreamingTest {

    private static final HttpString CHECKSUM = new HttpString("X-Backup-SHA256");

    @TempDir
    Path directory;

    private Undertow server;
    private HttpClient client;
    private URI endpoint;

    @BeforeEach
    void startBackupService() {
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .setBufferSize(4096)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(Handlers.routing().put("/backups/nightly", this::beginUpload))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        endpoint = URI.create("http://127.0.0.1:" + address.getPort() + "/backups/nightly");
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopBackupService() {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void archivesAChunkedBinaryBackupAndReturnsItsReceipt() throws Exception {
        byte[] content = backupBytes(262181);
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(content));
        assertEquals(-1, body.contentLength());
        assertArchived(content, body);
    }

    @Test
    void archivesAKnownLengthBackupWithoutChangingBinaryBytes() throws Exception {
        byte[] content = backupBytes(65539);
        assertArchived(content, HttpRequest.BodyPublishers.ofByteArray(content));
    }

    @Test
    void publishesAnEmptyChunkedBackupInsteadOfWaitingForAnotherChunk() throws Exception {
        byte[] content = new byte[0];
        assertArchived(content, HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(content)));
    }

    private void assertArchived(byte[] expected, HttpRequest.BodyPublisher body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/octet-stream")
                .PUT(body)
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals("Archived " + expected.length + " bytes", response.body());
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
        assertEquals(checksum, response.headers().firstValue(CHECKSUM.toString()).orElseThrow());
        assertArrayEquals(expected, Files.readAllBytes(directory.resolve("nightly.backup")));
        assertFalse(Files.exists(directory.resolve("nightly.partial")));
    }

    private void beginUpload(HttpServerExchange exchange) throws IOException, NoSuchAlgorithmException {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this::beginUpload);
            return;
        }
        Upload upload = new Upload(directory.resolve("nightly.partial"), directory.resolve("nightly.backup"));
        exchange.getRequestReceiver().receivePartialBytes(upload::accept, upload::failed);
    }

    private static byte[] backupBytes(int length) {
        byte[] content = new byte[length];
        for (int index = 0; index < length; index++) {
            content[index] = (byte) (index * 37 + index / 251);
        }
        return content;
    }

    private static final class Upload {

        private final Path staged;
        private final Path published;
        private final MessageDigest digest;
        private final OutputStream output;
        private long length;

        private Upload(Path staged, Path published) throws IOException, NoSuchAlgorithmException {
            this.staged = staged;
            this.published = published;
            digest = MessageDigest.getInstance("SHA-256");
            output = Files.newOutputStream(staged);
        }

        private void accept(HttpServerExchange exchange, byte[] chunk, boolean last) {
            Receiver receiver = exchange.getRequestReceiver();
            receiver.pause();
            // Dispatch before returning so a paused request remains owned by the worker.
            exchange.dispatch(() -> {
                try {
                    output.write(chunk);
                    digest.update(chunk);
                    length += chunk.length;
                    if (last) {
                        output.close();
                        Files.move(staged, published, StandardCopyOption.REPLACE_EXISTING);
                        exchange.setStatusCode(StatusCodes.CREATED);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
                        exchange.getResponseHeaders().put(CHECKSUM, HexFormat.of().formatHex(digest.digest()));
                        exchange.getResponseSender().send("Archived " + length + " bytes");
                    } else {
                        receiver.resume();
                    }
                } catch (IOException failure) {
                    failed(exchange, failure);
                }
            });
        }

        private void failed(HttpServerExchange exchange, IOException failure) {
            if (exchange.isInIoThread()) {
                exchange.dispatch(() -> failed(exchange, failure));
                return;
            }
            try {
                output.close();
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            exchange.getResponseSender().send("Backup was not archived");
        }
    }
}
