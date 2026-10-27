/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ru_yandex_clickhouse.clickhouse_jdbc;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import ru.yandex.clickhouse.settings.ClickHouseProperties;
import ru.yandex.clickhouse.util.ClickHouseHttpClientBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClickHouseHttpClientBuilderTest {
    private static final String ROOT_CERTIFICATE =
            "ru_yandex_clickhouse/clickhouse_jdbc/clickhouse-http-client-ca.pem";

    @Test
    void loadsStrictSslRootCertificateFromClasspath() throws Exception {
        ClickHouseProperties properties = new ClickHouseProperties();
        properties.setSsl(true);
        properties.setSslMode("strict");
        properties.setSslRootCertificate(ROOT_CERTIFICATE);
        properties.setConnectionTimeout(10_000);
        properties.setSocketTimeout(10_000);

        AtomicReference<String> requestedPath = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(requestedPath);
             CloseableHttpClient client = new ClickHouseHttpClientBuilder(properties).buildClient();
             CloseableHttpResponse response = client.execute(new HttpGet(server.healthUrl()))) {
            assertEquals(HttpURLConnection.HTTP_OK, response.getStatusLine().getStatusCode());
            assertEquals("ready", EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            assertEquals("/health", requestedPath.get());
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer start(AtomicReference<String> requestedPath) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", exchange -> respond(exchange, requestedPath));
            server.start();
            return new TestHttpServer(server);
        }

        private static void respond(HttpExchange exchange, AtomicReference<String> requestedPath)
                throws IOException {
            try (exchange) {
                requestedPath.set(exchange.getRequestURI().getPath());
                byte[] response = "ready".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private String healthUrl() {
            return "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/health";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
