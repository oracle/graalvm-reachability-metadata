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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's connection pooling subsystem by downloading warehouse reports over a reused connection.
public class PooledWarehouseReportsTest {
    @Test
    @Timeout(60)
    public void releasesConsumedReportsForTheNextRequestInASingleConnectionPool() throws IOException {
        Map<String, String> reports = Map.of(
                "/stock/north", "warehouse,available\nnorth,18\n",
                "/stock/south", "warehouse,available\nsouth,7\n",
                "/stock/west", "warehouse,available\nwest,12\n");
        List<String> requestedReports = new CopyOnWriteArrayList<>();
        List<InetSocketAddress> connections = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stock/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requestedReports.add(path);
            connections.add(exchange.getRemoteAddress());
            String report = reports.get(path);
            if (!"GET".equals(exchange.getRequestMethod()) || report == null) {
                respond(exchange, 404, "Unknown warehouse");
                return;
            }
            respond(exchange, 200, report);
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try (PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager()) {
            pool.setMaxTotal(1);
            pool.setDefaultMaxPerRoute(1);
            try (CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(pool)
                    .setDefaultRequestConfig(config)
                    .build()) {
                int totalAvailable = 0;
                for (String warehouse : List.of("north", "south", "west")) {
                    // Fully consume and close each report before borrowing the connection again.
                    try (CloseableHttpResponse response = client.execute(new HttpGet(baseUrl + "/stock/" + warehouse))) {
                        assertEquals(200, response.getStatusLine().getStatusCode());
                        String report = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        assertEquals(reports.get("/stock/" + warehouse), report);
                        List<String> rows = report.lines().toList();
                        assertEquals("warehouse,available", rows.getFirst());
                        String[] stock = rows.get(1).split(",");
                        assertEquals(warehouse, stock[0]);
                        totalAvailable += Integer.parseInt(stock[1]);
                    }
                }
                assertEquals(37, totalAvailable);
                assertEquals(List.of("/stock/north", "/stock/south", "/stock/west"), requestedReports);
                assertEquals(3, connections.size());
                assertEquals(1L, connections.stream().distinct().count(),
                        "The origin should receive all reports over the same persistent connection");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
