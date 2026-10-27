/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.RequestBufferingHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Request-body prebuffering with `RequestBufferingHandler` feeds a blocking stock-import endpoint.
 *
 * A stock service prebuffers small CSV uploads before dispatching to its blocking importer, so
 * those requests need not wait for network reads on a worker. Larger uploads exceed the bounded
 * prebuffer and continue through the same input stream. The importer validates the complete CSV
 * before publishing it, returns row and unit totals, and lets operators download the accepted
 * batch. Both known-length desktop uploads and chunked exports must arrive without lost or
 * duplicated bytes; a rejected batch must leave the previous import available.
 */
@Timeout(60)
public class StockImportRequestBufferingTest {

    @TempDir
    Path directory;

    private Undertow server;
    private HttpClient client;
    private URI base;

    @BeforeEach
    void startImporter() {
        ImportDesk desk = new ImportDesk(directory.resolve("latest.csv"));
        RoutingHandler routes = Handlers.routing()
                .post("/imports", desk::store)
                .get("/imports/latest", desk::download);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .setBufferSize(1024)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new RequestBufferingHandler(new BlockingHandler(routes), 2))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        base = URI.create("http://127.0.0.1:" + address.getPort());
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopImporter() {
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
    void importsSmallBatchesAndKeepsTheLastAcceptedBatchAfterAValidationFailure() throws Exception {
        String accepted = """
                sku,quantity
                coffee,4
                tea,7
                """;
        HttpResponse<String> imported = upload(BodyPublishers.ofString(accepted));
        assertEquals(201, imported.statusCode());
        assertEquals("Imported 2 rows containing 11 units\n", imported.body());
        assertEquals("/imports/latest", imported.headers().firstValue("Location").orElseThrow());
        assertDownload(accepted);

        HttpResponse<String> rejected = upload(BodyPublishers.ofString("""
                sku,quantity
                coffee,not-a-number
                """));
        assertEquals(422, rejected.statusCode());
        assertEquals("Expected stock rows with nonnegative integer quantities\n", rejected.body());
        assertDownload(accepted);

        HttpResponse<String> empty = upload(BodyPublishers.noBody());
        assertEquals(422, empty.statusCode());
        assertDownload(accepted);
    }

    @Test
    void importsAChunkedExportLargerThanThePrebufferWithoutLosingItsPrefixOrTail() throws Exception {
        StringBuilder csv = new StringBuilder("sku,quantity\n");
        int units = 0;
        for (int row = 0; row < 4000; row++) {
            int quantity = row % 9;
            csv.append("item-").append(row).append(',').append(quantity).append('\n');
            units += quantity;
        }
        String expected = csv.toString();
        byte[] payload = expected.getBytes(StandardCharsets.UTF_8);
        BodyPublisher chunked = BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(payload));

        HttpResponse<String> imported = upload(chunked);
        assertEquals(201, imported.statusCode());
        assertEquals("Imported 4000 rows containing " + units + " units\n", imported.body());
        assertDownload(expected);
    }

    private HttpResponse<String> upload(BodyPublisher body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/imports"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "text/csv; charset=UTF-8")
                .POST(body)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertDownload(String expected) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/imports/latest"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> downloaded = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, downloaded.statusCode());
        assertEquals("text/csv; charset=UTF-8",
                downloaded.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(expected, downloaded.body());
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body);
    }

    private static final class ImportDesk {
        private final Path archive;

        private ImportDesk(Path archive) {
            this.archive = archive;
        }

        private void store(HttpServerExchange exchange) throws IOException {
            byte[] payload = exchange.getInputStream().readAllBytes();
            String[] lines = new String(payload, StandardCharsets.UTF_8).split("\n");
            int rows = 0;
            long units = 0;
            try {
                if (lines.length < 2 || !lines[0].equals("sku,quantity")) {
                    throw new IllegalArgumentException("missing stock rows");
                }
                for (int index = 1; index < lines.length; index++) {
                    String[] fields = lines[index].split(",", -1);
                    if (fields.length != 2 || fields[0].isBlank()) {
                        throw new IllegalArgumentException("invalid stock row");
                    }
                    int quantity = Integer.parseInt(fields[1]);
                    if (quantity < 0) {
                        throw new IllegalArgumentException("negative stock quantity");
                    }
                    rows++;
                    units += quantity;
                }
            } catch (IllegalArgumentException invalid) {
                text(exchange, StatusCodes.UNPROCESSABLE_ENTITY,
                        "Expected stock rows with nonnegative integer quantities\n");
                return;
            }
            Files.write(archive, payload);
            exchange.getResponseHeaders().put(Headers.LOCATION, "/imports/latest");
            text(exchange, StatusCodes.CREATED,
                    "Imported " + rows + " rows containing " + units + " units\n");
        }

        private void download(HttpServerExchange exchange) throws IOException {
            if (!Files.exists(archive)) {
                text(exchange, StatusCodes.NOT_FOUND, "No stock import has been accepted\n");
                return;
            }
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv; charset=UTF-8");
            exchange.getOutputStream().write(Files.readAllBytes(archive));
        }
    }
}
