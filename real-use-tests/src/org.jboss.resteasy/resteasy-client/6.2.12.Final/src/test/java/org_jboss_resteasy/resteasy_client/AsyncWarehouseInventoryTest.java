/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises RESTEasy's asynchronous invocation subsystem to combine warehouse inventory lookups.
public class AsyncWarehouseInventoryTest {

    @Test
    @Timeout(60)
    public void combinesStockFromIndependentWarehouseCallbacks() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/warehouses/east/stock/blue-widget",
                exchange -> serveStock(exchange, "7"));
        server.createContext("/warehouses/west/stock/blue-widget",
                exchange -> serveStock(exchange, "5"));
        server.start();
        try {
            ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            builder.connectTimeout(10, TimeUnit.SECONDS);
            builder.readTimeout(10, TimeUnit.SECONDS);
            try (ResteasyClient client = builder.build()) {
                ResteasyWebTarget warehouses = client.target("http://127.0.0.1:" + server.getAddress().getPort())
                        .path("warehouses");
                CompletableFuture<String> eastStock = new CompletableFuture<>();
                CompletableFuture<String> westStock = new CompletableFuture<>();

                warehouses.path("east/stock/blue-widget").request(MediaType.TEXT_PLAIN_TYPE)
                        .async().get(new StockCallback(eastStock));
                warehouses.path("west/stock/blue-widget").request(MediaType.TEXT_PLAIN_TYPE)
                        .async().get(new StockCallback(westStock));

                CompletableFuture<Integer> availableStock = eastStock.thenCombine(westStock,
                        (east, west) -> Integer.parseInt(east) + Integer.parseInt(west));
                assertEquals(12, availableStock.get(20, TimeUnit.SECONDS));
                assertEquals("7", eastStock.get(10, TimeUnit.SECONDS));
                assertEquals("5", westStock.get(10, TimeUnit.SECONDS));
            }
        } finally {
            server.stop(0);
        }
    }

    private static void serveStock(HttpExchange exchange, String stock) throws IOException {
        try (exchange) {
            byte[] bytes = stock.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    public static final class StockCallback implements InvocationCallback<String> {
        private final CompletableFuture<String> result;

        private StockCallback(CompletableFuture<String> result) {
            this.result = result;
        }

        @Override
        public void completed(String stock) {
            result.complete(stock);
        }

        @Override
        public void failed(Throwable failure) {
            result.completeExceptionally(failure);
        }
    }
}
