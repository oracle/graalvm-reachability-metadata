/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's HTTP proxy routing subsystem to download reports from multiple suppliers.
public class ProxiedSupplierReportsTest {
    @Test
    @Timeout(60)
    public void routesSupplierRequestsThroughTheProxyWhilePreservingTheirTargetAddresses() throws IOException {
        String northUrl = "http://north.supplier.invalid/reports/stock?warehouse=north&format=csv";
        String southUrl = "http://south.supplier.invalid:8080/reports/stock?warehouse=south&format=csv";
        Map<String, String> reports = Map.of(
                northUrl, "supplier,available\nnorth,18\n",
                southUrl, "supplier,available\nsouth,7\n");
        List<String> proxyRequests = new CopyOnWriteArrayList<>();
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            URI target = exchange.getRequestURI();
            String host = exchange.getRequestHeaders().getFirst("Host");
            proxyRequests.add(exchange.getRequestMethod() + " " + target + " Host=" + host);
            String report = reports.get(target.toString());
            if (!"GET".equals(exchange.getRequestMethod()) || report == null
                    || !target.getRawAuthority().equals(host)) {
                respond(exchange, 400, "An absolute supplier URI and matching Host header are required");
                return;
            }
            exchange.getResponseHeaders().set("Via", "1.1 supplier-gateway");
            respond(exchange, 200, report);
        });
        proxy.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        HttpHost proxyHost = new HttpHost("127.0.0.1", proxy.getAddress().getPort(), "http");
        try (CloseableHttpClient client = HttpClients.custom()
                .setProxy(proxyHost)
                .setDefaultRequestConfig(config)
                .build()) {
            // Reserved .invalid names ensure no supplier DNS or external network is needed.
            for (String supplierUrl : List.of(northUrl, southUrl)) {
                try (CloseableHttpResponse response = client.execute(new HttpGet(supplierUrl))) {
                    assertEquals(200, response.getStatusLine().getStatusCode());
                    assertEquals("1.1 supplier-gateway", response.getFirstHeader("Via").getValue());
                    assertEquals(reports.get(supplierUrl),
                            EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                }
            }
            assertEquals(List.of(
                    "GET " + northUrl + " Host=north.supplier.invalid",
                    "GET " + southUrl + " Host=south.supplier.invalid:8080"), proxyRequests);
        } finally {
            proxy.stop(0);
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
