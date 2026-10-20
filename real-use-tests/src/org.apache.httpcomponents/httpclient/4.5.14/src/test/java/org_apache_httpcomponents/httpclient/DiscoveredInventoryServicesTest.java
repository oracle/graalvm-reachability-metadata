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
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises HttpClient's custom DNS resolution subsystem to query inventory through an application service directory.
public class DiscoveredInventoryServicesTest {
    @Test
    @Timeout(60)
    public void connectsToDiscoveredAddressesWhilePreservingEachServicesLogicalHost() throws IOException {
        String northHost = "north.inventory.invalid";
        String southHost = "south.inventory.invalid";
        InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        Map<String, InetAddress> serviceDirectory = Map.of(northHost, loopback, southHost, loopback);
        DnsResolver serviceDiscovery = hostname -> {
            InetAddress address = serviceDirectory.get(hostname);
            if (address == null) {
                throw new UnknownHostException("Service is not registered: " + hostname);
            }
            return new InetAddress[]{address};
        };
        Map<String, String> reports = Map.of(
                northHost, "warehouse,available\nnorth,18\n",
                southHost, "warehouse,available\nsouth,7\n");
        List<String> receivedHosts = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        Map<String, String> virtualHosts = Map.of(
                northHost + ":" + port, northHost,
                southHost + ":" + port, southHost);
        server.createContext("/stock", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String service = host == null ? null : virtualHosts.get(host);
            if (!"GET".equals(exchange.getRequestMethod())
                    || !"/stock".equals(exchange.getRequestURI().toString()) || service == null) {
                respond(exchange, 400, "An inventory service hostname is required");
                return;
            }
            receivedHosts.add(host);
            respond(exchange, 200, reports.get(service));
        });
        server.start();

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .build();
        try (CloseableHttpClient client = HttpClients.custom()
                .setDnsResolver(serviceDiscovery)
                .setDefaultRequestConfig(config)
                .build()) {
            // These reserved names resolve only through the application's service directory.
            for (String host : List.of(northHost, southHost)) {
                HttpGet lookup = new HttpGet("http://" + host + ":" + port + "/stock");
                try (CloseableHttpResponse response = client.execute(lookup)) {
                    assertEquals(200, response.getStatusLine().getStatusCode());
                    assertEquals(reports.get(host), EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                }
            }
            assertEquals(List.of(northHost + ":" + port, southHost + ":" + port), receivedHosts);
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
