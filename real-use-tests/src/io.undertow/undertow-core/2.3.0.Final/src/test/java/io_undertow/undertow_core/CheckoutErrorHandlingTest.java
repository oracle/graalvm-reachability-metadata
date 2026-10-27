/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_undertow.undertow_core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.server.handlers.error.FileErrorPageHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import java.io.IOException;
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
 * Exception mapping and default error pages with `ExceptionHandler` and `FileErrorPageHandler`.
 *
 * A checkout endpoint reserves stock in a local inventory file. Domain exceptions become useful
 * conflict responses, invalid quantities become client errors, and storage failures serve a branded
 * maintenance page without exposing filesystem paths. Blocking file operations run on worker threads.
 */
@Timeout(60)
public class CheckoutErrorHandlingTest {

    private static final String MAINTENANCE_PAGE = """
            <!doctype html>
            <html><body><h1>Checkout temporarily unavailable</h1>
            <p>Your order has not been placed. Please try again later.</p></body></html>
            """;

    @TempDir
    Path directory;

    private Path inventory;
    private Undertow server;
    private HttpClient client;
    private URI checkout;

    @BeforeEach
    void startCheckout() throws IOException {
        inventory = directory.resolve("inventory.txt");
        Files.writeString(inventory, "5");
        Path maintenance = directory.resolve("maintenance.html");
        Files.writeString(maintenance, MAINTENANCE_PAGE);

        ExceptionHandler errors = new ExceptionHandler(this::reserveStock)
                .addExceptionHandler(InsufficientStockException.class, exchange -> {
                    InsufficientStockException failure = (InsufficientStockException)
                            exchange.getAttachment(ExceptionHandler.THROWABLE);
                    text(exchange, StatusCodes.CONFLICT, failure.getMessage());
                })
                .addExceptionHandler(IllegalArgumentException.class, exchange ->
                        text(exchange, StatusCodes.BAD_REQUEST, "quantity must be a positive integer"))
                .addExceptionHandler(IOException.class, exchange -> {
                    exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
                    exchange.getResponseHeaders().put(Headers.RETRY_AFTER, "60");
                    exchange.endExchange();
                });
        FileErrorPageHandler maintenancePage = new FileErrorPageHandler(
                errors, maintenance, StatusCodes.SERVICE_UNAVAILABLE);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(2)
                .addHttpListener(0, "127.0.0.1")
                .setHandler(new BlockingHandler(maintenancePage))
                .build();
        server.start();
        InetSocketAddress address = (InetSocketAddress) server.getListenerInfo().get(0).getAddress();
        checkout = URI.create("http://127.0.0.1:" + address.getPort() + "/checkout");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void stopCheckout() {
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
    void mapsStockConflictsWithoutChangingInventoryOrReplacingTheirResponse() throws Exception {
        HttpResponse<String> conflict = reserve("6");
        assertEquals(409, conflict.statusCode());
        assertEquals("Only 5 units remain", conflict.body());
        assertEquals("text/plain; charset=UTF-8",
                conflict.headers().firstValue("Content-Type").orElseThrow());

        HttpResponse<String> accepted = reserve("2");
        assertEquals(201, accepted.statusCode());
        assertEquals("Reserved 2 units; 3 remaining", accepted.body());

        HttpResponse<String> nextConflict = reserve("4");
        assertEquals(409, nextConflict.statusCode());
        assertEquals("Only 3 units remain", nextConflict.body());
    }

    @Test
    void mapsInvalidQuantitiesAndContinuesServingValidOrders() throws Exception {
        HttpResponse<String> malformed = reserve("several");
        assertEquals(400, malformed.statusCode());
        assertEquals("quantity must be a positive integer", malformed.body());

        HttpResponse<String> negative = reserve("-1");
        assertEquals(400, negative.statusCode());
        assertEquals("quantity must be a positive integer", negative.body());

        HttpResponse<String> accepted = reserve("5");
        assertEquals(201, accepted.statusCode());
        assertEquals("Reserved 5 units; 0 remaining", accepted.body());
    }

    @Test
    void servesAMaintenancePageForStorageFailuresAndRecoversWhenStorageReturns() throws Exception {
        Files.delete(inventory);

        HttpResponse<String> unavailable = reserve("1");
        assertEquals(503, unavailable.statusCode());
        assertEquals("60", unavailable.headers().firstValue("Retry-After").orElseThrow());
        assertEquals("text/html", unavailable.headers().firstValue("Content-Type").orElseThrow());
        assertEquals(MAINTENANCE_PAGE, unavailable.body());
        assertFalse(unavailable.body().contains(directory.toString()));
        assertFalse(unavailable.body().contains("Exception"));

        Files.writeString(inventory, "7");
        HttpResponse<String> recovered = reserve("2");
        assertEquals(201, recovered.statusCode());
        assertEquals("Reserved 2 units; 5 remaining", recovered.body());
        assertFalse(recovered.headers().firstValue("Retry-After").isPresent());
    }

    private void reserveStock(HttpServerExchange exchange) throws IOException, InsufficientStockException {
        int quantity = Integer.parseInt(exchange.getQueryParameters().get("quantity").getFirst());
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity is not positive");
        }
        int available = Integer.parseInt(Files.readString(inventory));
        if (quantity > available) {
            throw new InsufficientStockException(available);
        }
        int remaining = available - quantity;
        Files.writeString(inventory, Integer.toString(remaining));
        text(exchange, StatusCodes.CREATED, "Reserved " + quantity + " units; " + remaining + " remaining");
    }

    private HttpResponse<String> reserve(String quantity) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(checkout + "?quantity=" + quantity))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void text(HttpServerExchange exchange, int status, String body) {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8");
        exchange.getResponseSender().send(body);
    }

    private static final class InsufficientStockException extends Exception {
        private static final long serialVersionUID = 1L;

        private InsufficientStockException(int available) {
            super("Only " + available + " units remain");
        }
    }
}
