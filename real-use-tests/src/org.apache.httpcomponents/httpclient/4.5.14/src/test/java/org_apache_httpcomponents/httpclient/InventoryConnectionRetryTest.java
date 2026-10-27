/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's transport retry subsystem to recover an inventory lookup after a dropped connection.
public class InventoryConnectionRetryTest {
    @Test
    @Timeout(60)
    public void retriesAnIdempotentLookupWhenTheServerClosesWithoutAResponse() throws IOException {
        String inventory = """
                sku,warehouse,available
                NOTEBOOK-42,north,18
                """;
        AtomicInteger attempts = new AtomicInteger();
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inventory/NOTEBOOK-42", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            if (attempts.incrementAndGet() == 1) {
                // The first connection drops before any HTTP response reaches the application.
                exchange.close();
                return;
            }
            respond(exchange, inventory);
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String inventoryUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/inventory/NOTEBOOK-42";
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(1, false))
                .build()) {
            String downloadedInventory = client.execute(new HttpGet(inventoryUrl), new BasicResponseHandler());
            assertEquals(inventory, downloadedInventory);
            assertEquals(List.of("sku,warehouse,available", "NOTEBOOK-42,north,18"),
                    downloadedInventory.lines().toList());
            assertEquals(List.of("GET /inventory/NOTEBOOK-42", "GET /inventory/NOTEBOOK-42"), requests);
            assertEquals(2, attempts.get());
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
