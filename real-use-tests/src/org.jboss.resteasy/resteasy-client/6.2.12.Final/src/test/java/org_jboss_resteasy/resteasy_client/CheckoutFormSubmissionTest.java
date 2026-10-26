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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's URL-encoded form entity subsystem for a shopping-cart checkout.
public class CheckoutFormSubmissionTest {

    @Test
    @Timeout(60)
    public void submitsRepeatedItemsAndSpecialCharactersWithoutChangingTheOrderDetails() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/checkout", new CheckoutResource());
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                Form checkout = new Form()
                        .param("customer", "A+B & Co")
                        .param("address", "12 Market St / Unit #4")
                        .param("instructions", "Leave by the caf\u00e9 door; code=50%")
                        .param("item", "blue widget")
                        .param("item", "red & gold widget")
                        .param("gift-message", "");

                URI receiptUri;
                try (Response response = client.target(baseUri).path("checkout")
                        .request(MediaType.TEXT_PLAIN_TYPE).post(Entity.form(checkout))) {
                    assertEquals(201, response.getStatus());
                    assertEquals("Order accepted", response.readEntity(String.class));
                    assertEquals(URI.create("/checkout/receipts/order-42"), response.getLocation());
                    receiptUri = baseUri.resolve(response.getLocation());
                }

                try (Response response = client.target(receiptUri).request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("""
                            Order: order-42
                            Customer: A+B & Co
                            Address: 12 Market St / Unit #4
                            Instructions: Leave by the caf\u00e9 door; code=50%
                            Items: blue widget | red & gold widget
                            Gift message: []
                            """, response.readEntity(String.class));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private static final class CheckoutResource implements HttpHandler {
        private final AtomicReference<String> receipt = new AtomicReference<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod()) && "/checkout".equals(path)) {
                    String contentType = exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                    if (contentType == null || !MediaType.APPLICATION_FORM_URLENCODED_TYPE
                            .isCompatible(MediaType.valueOf(contentType))) {
                        respond(exchange, 415, "Checkout requires a URL-encoded form");
                        return;
                    }
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    MultivaluedMap<String, String> fields = new MultivaluedHashMap<>();
                    for (String pair : body.split("&")) {
                        String[] parts = pair.split("=", 2);
                        fields.add(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                                parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
                    }
                    if (!fields.keySet().containsAll(
                            Set.of("customer", "address", "instructions", "item", "gift-message"))) {
                        respond(exchange, 400, "Missing checkout details");
                        return;
                    }
                    receipt.set("""
                            Order: order-42
                            Customer: %s
                            Address: %s
                            Instructions: %s
                            Items: %s
                            Gift message: [%s]
                            """.formatted(fields.getFirst("customer"), fields.getFirst("address"),
                            fields.getFirst("instructions"), String.join(" | ", fields.get("item")),
                            fields.getFirst("gift-message")));
                    exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/checkout/receipts/order-42");
                    respond(exchange, 201, "Order accepted");
                } else if ("GET".equals(exchange.getRequestMethod())
                        && "/checkout/receipts/order-42".equals(path) && receipt.get() != null) {
                    respond(exchange, 200, receipt.get());
                } else {
                    respond(exchange, 404, "Receipt not found");
                }
            }
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
