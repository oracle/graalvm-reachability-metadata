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

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.HttpContinueAcceptingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTP 100-continue upload negotiation with `HttpContinueAcceptingHandler`.
 *
 * A publishing service accepts binary image uploads carrying a previously issued upload ticket.
 * Large uploads wait for permission before sending their body; an invalid ticket is rejected at
 * that handshake without creating a file. Small clients can omit the handshake but must still
 * present the ticket. Accepted bytes are archived on a worker thread before returning a receipt.
 */
@Timeout(60)
public class ImageUploadContinueTest {

    private static final HttpString UPLOAD_TICKET = new HttpString("X-Upload-Ticket");
    private static final String VALID_TICKET = "publish-banner-42";

    @TempDir
    Path archive;

    private Undertow server;
    private HttpClient client;
    private URI upload;
    private Path image;

    @BeforeEach
    void startPublishingService() {
        image = archive.resolve("banner.bin");
        HttpContinueAcceptingHandler uploads = new HttpContinueAcceptingHandler(
                new BlockingHandler(this::storeImage), ImageUploadContinueTest::hasUploadTicket);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(uploads)
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        upload = URI.create("http://127.0.0.1:" + address.getPort() + "/images/banner.bin");
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterEach
    void stopPublishingService() {
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
    void negotiatesPermissionThenArchivesTheCompleteBinaryImage() throws Exception {
        byte[] content = imageBytes(32768);

        HttpResponse<String> response = upload(content, VALID_TICKET, true);

        assertReceipt(response, content.length);
        assertArrayEquals(content, Files.readAllBytes(image));
    }

    @Test
    void refusesAnInvalidTicketAtTheHandshakeAndStillAcceptsTheNextUpload() throws Exception {
        byte[] content = imageBytes(32768);

        HttpResponse<String> rejected = upload(content, "expired-ticket", true);

        assertEquals(417, rejected.statusCode());
        assertFalse(Files.exists(image));

        HttpResponse<String> accepted = upload(content, VALID_TICKET, true);
        assertReceipt(accepted, content.length);
        assertArrayEquals(content, Files.readAllBytes(image));
    }

    @Test
    void acceptsSmallUploadsWithoutContinueButStillRequiresTheTicket() throws Exception {
        byte[] content = imageBytes(256);

        HttpResponse<String> rejected = upload(content, "expired-ticket", false);
        assertEquals(403, rejected.statusCode());
        assertEquals("A valid upload ticket is required", rejected.body());
        assertFalse(Files.exists(image));

        HttpResponse<String> accepted = upload(content, VALID_TICKET, false);
        assertReceipt(accepted, content.length);
        assertArrayEquals(content, Files.readAllBytes(image));
    }

    private void storeImage(HttpServerExchange exchange) throws IOException {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        if (!hasUploadTicket(exchange)) {
            exchange.setStatusCode(StatusCodes.FORBIDDEN);
            exchange.getResponseSender().send("A valid upload ticket is required");
            return;
        }
        try (InputStream input = exchange.getInputStream()) {
            Files.copy(input, image);
        }
        exchange.setStatusCode(StatusCodes.CREATED);
        exchange.getResponseHeaders().put(Headers.LOCATION, "/images/banner.bin");
        exchange.getResponseSender().send("Stored " + Files.size(image) + " bytes");
    }

    private static boolean hasUploadTicket(HttpServerExchange exchange) {
        return VALID_TICKET.equals(exchange.getRequestHeaders().getFirst(UPLOAD_TICKET));
    }

    private HttpResponse<String> upload(byte[] content, String ticket, boolean expectContinue)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(upload)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .header("X-Upload-Ticket", ticket)
                .expectContinue(expectContinue)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertReceipt(HttpResponse<String> response, int length) {
        assertEquals(201, response.statusCode());
        assertEquals("/images/banner.bin", response.headers().firstValue("Location").orElseThrow());
        assertEquals("text/plain; charset=UTF-8",
                response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("Stored " + length + " bytes", response.body());
    }

    private static byte[] imageBytes(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (i * 37);
        }
        return bytes;
    }
}
