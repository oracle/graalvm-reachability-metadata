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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.cache.BrowserCacheFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's browser cache subsystem to reuse shipping rates and refresh them after an update.
public class CachedShippingRatesTest {
    private static final String ORIGINAL_RATES = "standard=5.00; express=12.00";
    private static final String UPDATED_RATES = "standard=6.00; express=14.00";

    @Test
    @Timeout(60)
    public void reusesShippingRatesUntilTheApplicationExplicitlyRefreshesItsCache() throws IOException {
        ShippingRatesResource resource = new ShippingRatesResource();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/shipping/rates", resource);
        server.start();
        try {
            BrowserCacheFeature cache = new BrowserCacheFeature();
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            builder.register(cache);
            try (ResteasyClient client = builder.build()) {
                WebTarget rates = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                        .path("shipping/rates");

                assertRates(rates, ORIGINAL_RATES);
                assertRates(rates, ORIGINAL_RATES);
                assertEquals(1, resource.downloads.get(), "Repeated checkout quotes should reuse the cached rates");

                try (Response response = rates.request().put(Entity.text(UPDATED_RATES))) {
                    assertEquals(204, response.getStatus());
                }
                // An administrative update explicitly refreshes this application's local rate cache.
                cache.getCache().clear();

                assertRates(rates, UPDATED_RATES);
                assertRates(rates, UPDATED_RATES);
                assertEquals(2, resource.downloads.get(), "Refreshing should download the new schedule only once");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void assertRates(WebTarget rates, String expected) {
        try (Response response = rates.request(MediaType.TEXT_PLAIN_TYPE).get()) {
            assertEquals(200, response.getStatus());
            assertEquals(expected, response.readEntity(String.class));
            assertEquals("public, max-age=86400", response.getHeaderString(HttpHeaders.CACHE_CONTROL));
        }
    }

    private static final class ShippingRatesResource implements HttpHandler {
        private final AtomicReference<String> schedule = new AtomicReference<>(ORIGINAL_RATES);
        private final AtomicInteger downloads = new AtomicInteger();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> {
                        downloads.incrementAndGet();
                        byte[] bytes = schedule.get().getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
                        // Fresh for a day, far beyond this bounded scenario; no expiration wait is needed.
                        exchange.getResponseHeaders().set(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    }
                    case "PUT" -> {
                        schedule.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
                }
            }
        }
    }
}
