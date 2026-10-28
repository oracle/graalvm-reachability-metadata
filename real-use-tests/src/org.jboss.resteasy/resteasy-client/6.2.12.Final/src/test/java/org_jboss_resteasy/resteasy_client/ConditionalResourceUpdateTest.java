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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises RESTEasy's conditional HTTP request subsystem to prevent lost resource updates.
public class ConditionalResourceUpdateTest {

    @Test
    @Timeout(60)
    public void updatesAResourceWithoutOverwritingANewerRevision() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stock/widget", new StockResource());
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                WebTarget stock = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                        .path("stock/widget");
                EntityTag originalRevision;
                try (Response response = stock.request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals("available=12", response.readEntity(String.class));
                    originalRevision = response.getEntityTag();
                    assertNotNull(originalRevision);
                }

                EntityTag updatedRevision;
                try (Response response = stock.request(MediaType.TEXT_PLAIN_TYPE)
                        .header(HttpHeaders.IF_MATCH, originalRevision)
                        .put(Entity.text("available=9"))) {
                    assertEquals(200, response.getStatus());
                    assertEquals("available=9", response.readEntity(String.class));
                    updatedRevision = response.getEntityTag();
                    assertEquals(new EntityTag("2"), updatedRevision);
                }

                try (Response response = stock.request(MediaType.TEXT_PLAIN_TYPE)
                        .header(HttpHeaders.IF_MATCH, originalRevision)
                        .put(Entity.text("available=11"))) {
                    assertEquals(412, response.getStatus());
                    assertEquals(updatedRevision, response.getEntityTag());
                    assertEquals("Resource changed; reload before saving", response.readEntity(String.class));
                }

                try (Response response = stock.request(MediaType.TEXT_PLAIN_TYPE).get()) {
                    assertEquals(200, response.getStatus());
                    assertEquals(updatedRevision, response.getEntityTag());
                    assertEquals("available=9", response.readEntity(String.class));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private record Stock(String value, int revision) {
        String etag() {
            return "\"" + revision + "\"";
        }
    }

    private static final class StockResource implements HttpHandler {
        private final AtomicReference<Stock> stock = new AtomicReference<>(new Stock("available=12", 1));

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                Stock current = stock.get();
                if ("GET".equals(exchange.getRequestMethod())) {
                    respond(exchange, 200, current, current.value());
                } else if ("PUT".equals(exchange.getRequestMethod())) {
                    String value = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Stock updated = new Stock(value, current.revision() + 1);
                    if (current.etag().equals(exchange.getRequestHeaders().getFirst(HttpHeaders.IF_MATCH))
                            && stock.compareAndSet(current, updated)) {
                        respond(exchange, 200, updated, updated.value());
                    } else {
                        respond(exchange, 412, stock.get(), "Resource changed; reload before saving");
                    }
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, PUT");
                    respond(exchange, 405, current, "Method not allowed");
                }
            }
        }

        private static void respond(HttpExchange exchange, int status, Stock current, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set(HttpHeaders.ETAG, current.etag());
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
