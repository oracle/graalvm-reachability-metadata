/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jetty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.CrossOriginHandler;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jetty's CORS subsystem for a browser storefront reserving stock through a separate inventory API.
public class StockReservationCorsTest {

    @Test
    @Timeout(60)
    public void permitsStorefrontPreflightAndReservationButRejectsAnUntrustedOrigin() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        connector.setIdleTimeout(10000);
        server.addConnector(connector);
        server.setStopTimeout(10000);

        CrossOriginHandler cors = new CrossOriginHandler();
        cors.setAllowedOriginPatterns(Set.of("https://shop\\.example\\.test"));
        cors.setAllowedMethods(Set.of("POST"));
        cors.setAllowedHeaders(Set.of("X-Reservation-ID"));
        cors.setExposedHeaders(Set.of("X-Reservation-ID"));
        cors.setPreflightMaxAge(Duration.ofMinutes(10));
        cors.setDeliverPreflightRequests(false);
        cors.setDeliverNonAllowedOriginRequests(false);
        cors.setHandler(new InventoryHandler());
        server.setHandler(cors);

        try {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/inventory/desk-lamp");
            String storefront = "https://shop.example.test";
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()) {
                HttpResponse<String> preflight = client.send(HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(10))
                        .header("Origin", storefront)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Reservation-ID")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, preflight.statusCode());
                assertEquals("", preflight.body());
                assertEquals(storefront, preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                assertEquals("POST", preflight.headers().firstValue("Access-Control-Allow-Methods").orElseThrow());
                assertEquals("X-Reservation-ID",
                        preflight.headers().firstValue("Access-Control-Allow-Headers").orElseThrow());
                assertEquals("600", preflight.headers().firstValue("Access-Control-Max-Age").orElseThrow());
                assertEquals("Origin", preflight.headers().firstValue("Vary").orElseThrow());
                assertStock(client, endpoint, 7);

                HttpResponse<String> reserved = reserve(client, endpoint, storefront, "reservation-104");
                assertEquals(201, reserved.statusCode());
                assertEquals("Reserved desk lamp\nRemaining: 6\n", reserved.body());
                assertEquals(storefront, reserved.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                assertEquals("X-Reservation-ID",
                        reserved.headers().firstValue("Access-Control-Expose-Headers").orElseThrow());
                assertEquals("reservation-104", reserved.headers().firstValue("X-Reservation-ID").orElseThrow());

                HttpResponse<String> rejected = reserve(client, endpoint,
                        "https://shop.example.test.attacker.test", "untrusted-reservation");
                assertEquals(400, rejected.statusCode());
                assertTrue(rejected.headers().allValues("Access-Control-Allow-Origin").isEmpty());
                assertTrue(rejected.headers().allValues("X-Reservation-ID").isEmpty());
                // Rejection must happen before the inventory application changes its stock.
                assertStock(client, endpoint, 6);
            }
        } finally {
            server.stop();
            server.destroy();
        }
    }

    private static HttpResponse<String> reserve(HttpClient client, URI endpoint, String origin, String reservationId)
            throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Origin", origin)
                .header("X-Reservation-ID", reservationId)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStock(HttpClient client, URI endpoint, int quantity) throws Exception {
        HttpResponse<String> stock = client.send(HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, stock.statusCode());
        assertEquals("Available desk lamps: " + quantity + "\n", stock.body());
        assertTrue(stock.headers().allValues("Access-Control-Allow-Origin").isEmpty());
    }

    private static class InventoryHandler extends Handler.Abstract {
        private final AtomicInteger available = new AtomicInteger(7);

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            if (!"/inventory/desk-lamp".equals(Request.getPathInContext(request))) {
                return false;
            }
            String body;
            if ("GET".equals(request.getMethod())) {
                body = "Available desk lamps: " + available.get() + "\n";
            } else if ("POST".equals(request.getMethod())) {
                response.setStatus(201);
                response.getHeaders().put("X-Reservation-ID", request.getHeaders().get("X-Reservation-ID"));
                body = "Reserved desk lamp\nRemaining: " + available.decrementAndGet() + "\n";
            } else {
                return false;
            }
            response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain;charset=utf-8");
            Content.Sink.write(response, true, body, callback);
            return true;
        }
    }
}
