/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import com.sun.net.httpserver.HttpServer;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's HTTP connection subsystem to load a catalog search result.
 */
public class JsoupConnectionTest {

    @Test
    @Timeout(60)
    public void loadsSearchResultsThroughConfiguredConnection() throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/search", exchange -> {
            boolean requestMatches = "GET".equals(exchange.getRequestMethod())
                    && "q=camp".equals(exchange.getRequestURI().getRawQuery())
                    && "CatalogApp/1.0".equals(exchange.getRequestHeaders().getFirst("User-Agent"))
                    && "session=catalog-42".equals(exchange.getRequestHeaders().getFirst("Cookie"));
            String responseBody = requestMatches
                    ? """
                      <!doctype html>
                      <html><body>
                      <main id="results">
                        <h1>Search results for camp</h1>
                        <article class="product" data-sku="lamp-2">Camp Lamp</article>
                      </main>
                      </body></html>
                      """
                    : "<p>Unexpected request</p>";
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(requestMatches ? 200 : 400, responseBytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(responseBytes);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
            Connection connection = Jsoup.connect(endpoint)
                    .userAgent("CatalogApp/1.0")
                    .cookie("session", "catalog-42")
                    .data("q", "camp")
                    .timeout(10_000);

            Document results = connection.get();

            assertEquals(200, connection.response().statusCode());
            assertEquals("Search results for camp", results.selectFirst("h1").text());
            assertEquals("lamp-2", results.selectFirst("article.product").attr("data-sku"));
            assertTrue(results.select("article.product").text().contains("Camp Lamp"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
