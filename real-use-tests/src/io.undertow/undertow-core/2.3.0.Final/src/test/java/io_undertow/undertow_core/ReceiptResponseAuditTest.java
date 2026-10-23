/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.undertow.Undertow;
import io.undertow.attribute.StoredResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.StoredResponseHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Response-body auditing with `StoredResponseHandler` captures completed sales receipts.
 *
 * A coffee merchant streams a human-readable receipt to a point-of-sale client and retains the
 * exact response text, status, and request identifier in its audit sink. Capturing at exchange
 * completion includes all the separately flushed receipt lines, not just the last write. The
 * same audit boundary records validation failures so a declined sale cannot appear as a receipt.
 * Audit delivery is asynchronous relative to the client's response, so assertions use a bounded
 * queue poll rather than a delay.
 */
public class ReceiptResponseAuditTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String CONTENT_TYPE = "text/plain; charset=UTF-8";

    @Test
    @Timeout(60)
    void retainsExactlyTheReceiptsAndRejectionsDeliveredToTheCashier() throws Exception {
        LinkedBlockingQueue<AuditEntry> audit = new LinkedBlockingQueue<>();
        ReceiptDesk desk = new ReceiptDesk();
        HttpHandler captured = new StoredResponseHandler(new BlockingHandler(desk));
        HttpHandler audited = exchange -> {
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            exchange.addExchangeCompleteListener((completed, next) -> {
                try {
                    audit.add(new AuditEntry(requestId, completed.getRequestMethod().toString(),
                            completed.getRequestURI(), completed.getStatusCode(),
                            StoredResponse.INSTANCE.readAttribute(completed)));
                } finally {
                    next.proceed();
                }
            });
            captured.handleRequest(exchange);
        };
        RoutingHandler routes = new RoutingHandler().post("/receipts", audited);
        Undertow server = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setIoThreads(1)
                .setWorkerThreads(2)
                .setHandler(routes)
                .build();
        server.start();
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(WAIT)
                .build()) {
            InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().getFirst().getAddress();
            URI endpoint = URI.create("http://127.0.0.1:" + address.getPort() + "/receipts");

            HttpResponse<String> first = purchase(client, endpoint, "sale-17", "3");
            String firstReceipt = """
                    RECEIPT R-1
                    Item: Caf\u00e9 beans
                    Quantity: 3
                    Total: EUR 37.50
                    """;
            assertEquals(StatusCodes.CREATED, first.statusCode());
            assertEquals(CONTENT_TYPE, first.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("R-1", first.headers().firstValue("X-Receipt-Id").orElseThrow());
            assertEquals(firstReceipt, first.body());
            assertAudit(audit, "sale-17", StatusCodes.CREATED, firstReceipt);

            HttpResponse<String> rejected = purchase(client, endpoint, "sale-18", "0");
            assertEquals(StatusCodes.UNPROCESSABLE_ENTITY, rejected.statusCode());
            assertEquals("Quantity must be between 1 and 20.\n", rejected.body());
            assertAudit(audit, "sale-18", StatusCodes.UNPROCESSABLE_ENTITY, rejected.body());

            HttpResponse<String> second = purchase(client, endpoint, "sale-19", "1");
            String secondReceipt = """
                    RECEIPT R-2
                    Item: Caf\u00e9 beans
                    Quantity: 1
                    Total: EUR 12.50
                    """;
            assertEquals(StatusCodes.CREATED, second.statusCode());
            assertEquals("R-2", second.headers().firstValue("X-Receipt-Id").orElseThrow());
            assertEquals(secondReceipt, second.body());
            assertAudit(audit, "sale-19", StatusCodes.CREATED, secondReceipt);
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> purchase(HttpClient client, URI endpoint, String requestId,
            String quantity) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(WAIT)
                .header("X-Request-Id", requestId)
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(quantity))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertAudit(LinkedBlockingQueue<AuditEntry> audit, String requestId,
            int status, String body) throws InterruptedException {
        AuditEntry entry = audit.poll(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(entry, "the completed exchange must deliver its audit entry");
        assertEquals(new AuditEntry(requestId, "POST", "/receipts", status, body), entry);
    }

    private record AuditEntry(String requestId, String method, String path, int status, String body) {
    }

    private static final class ReceiptDesk implements HttpHandler {

        private static final BigDecimal UNIT_PRICE = new BigDecimal("12.50");
        private final AtomicInteger receiptIds = new AtomicInteger();

        @Override
        public void handleRequest(HttpServerExchange exchange) throws IOException {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE);
            String submitted = new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int quantity;
            try {
                quantity = Integer.parseInt(submitted);
            } catch (NumberFormatException invalid) {
                reject(exchange);
                return;
            }
            if (quantity < 1 || quantity > 20) {
                reject(exchange);
                return;
            }

            String receiptId = "R-" + receiptIds.incrementAndGet();
            BigDecimal total = UNIT_PRICE.multiply(BigDecimal.valueOf(quantity));
            exchange.setStatusCode(StatusCodes.CREATED);
            exchange.getResponseHeaders().put(new HttpString("X-Receipt-Id"), receiptId);
            try (OutputStream output = exchange.getOutputStream()) {
                line(output, "RECEIPT " + receiptId);
                line(output, "Item: Caf\u00e9 beans");
                line(output, "Quantity: " + quantity);
                line(output, "Total: EUR " + total.toPlainString());
            }
        }

        private static void reject(HttpServerExchange exchange) throws IOException {
            exchange.setStatusCode(StatusCodes.UNPROCESSABLE_ENTITY);
            try (OutputStream output = exchange.getOutputStream()) {
                line(output, "Quantity must be between 1 and 20.");
            }
        }

        private static void line(OutputStream output, String text) throws IOException {
            output.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }
}
