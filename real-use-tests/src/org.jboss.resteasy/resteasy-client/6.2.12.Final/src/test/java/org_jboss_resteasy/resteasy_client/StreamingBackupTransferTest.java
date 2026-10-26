/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's chunked binary streaming subsystem to upload and restore a backup snapshot.
public class StreamingBackupTransferTest {

    @Test
    @Timeout(60)
    public void streamsABackupToStorageAndRestoresEveryByte(@TempDir Path directory) throws IOException {
        Path original = directory.resolve("original.snapshot");
        Path restored = directory.resolve("restored.snapshot");
        writeSnapshot(original);
        long snapshotSize = Files.size(original);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/backups/nightly", new BackupResource(directory.resolve("stored.snapshot")));
        server.start();
        try {
            URI backupUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/backups/nightly");
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                ResteasyWebTarget backup = client.target(backupUri).setChunked(true);
                StreamingOutput snapshot = output -> Files.copy(original, output);
                URI downloadUri;
                try (Response response = backup.request(MediaType.TEXT_PLAIN_TYPE)
                        .put(Entity.entity(snapshot, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {
                    assertEquals(201, response.getStatus());
                    assertEquals("Stored " + snapshotSize + " bytes", response.readEntity(String.class));
                    assertEquals(URI.create("/backups/nightly"), response.getLocation());
                    downloadUri = backupUri.resolve(response.getLocation());
                }

                try (Response response = client.target(downloadUri)
                        .request(MediaType.APPLICATION_OCTET_STREAM_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals(MediaType.APPLICATION_OCTET_STREAM_TYPE, response.getMediaType());
                    assertEquals("attachment; filename=nightly.snapshot",
                            response.getHeaderString("Content-Disposition"));
                    try (InputStream input = response.readEntity(InputStream.class)) {
                        assertEquals(snapshotSize, Files.copy(input, restored));
                    }
                }
                assertEquals(-1L, Files.mismatch(original, restored),
                        "Restoring the streamed backup must preserve all binary data");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void writeSnapshot(Path file) throws IOException {
        byte[] block = new byte[8192];
        for (int index = 0; index < block.length; index++) {
            block[index] = (byte) (index * 31);
        }
        try (OutputStream output = Files.newOutputStream(file)) {
            for (int blockNumber = 0; blockNumber < 32; blockNumber++) {
                output.write(block);
            }
            // A partial final block detects truncation at a transfer-buffer boundary.
            output.write(new byte[]{0, (byte) 0xff, 13, 10, 42});
        }
    }

    private static final class BackupResource implements HttpHandler {
        private final Path storedSnapshot;

        private BackupResource(Path storedSnapshot) {
            this.storedSnapshot = storedSnapshot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> {
                        String contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        String transferEncoding = exchange.getRequestHeaders().getFirst("Transfer-Encoding");
                        if (!MediaType.APPLICATION_OCTET_STREAM.equals(contentType)
                                || !"chunked".equalsIgnoreCase(transferEncoding)) {
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }
                        long storedBytes = Files.copy(exchange.getRequestBody(), storedSnapshot);
                        byte[] receipt = ("Stored " + storedBytes + " bytes").getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
                        exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/backups/nightly");
                        exchange.sendResponseHeaders(201, receipt.length);
                        exchange.getResponseBody().write(receipt);
                    }
                    case "GET" -> {
                        if (!Files.exists(storedSnapshot)) {
                            exchange.sendResponseHeaders(404, -1);
                            return;
                        }
                        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_OCTET_STREAM);
                        exchange.getResponseHeaders().set("Content-Disposition",
                                "attachment; filename=nightly.snapshot");
                        // Zero selects chunked transfer, without buffering the snapshot in memory.
                        exchange.sendResponseHeaders(200, 0);
                        Files.copy(storedSnapshot, exchange.getResponseBody());
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
                }
            }
        }
    }
}
