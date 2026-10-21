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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.yandex.clickhouse.ClickHouseDriver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteFragmentUtilsTest {
    private static final String ARRAY_RESPONSE = """
        numbers\tnested
        Array(Int32)\tArray(Array(Int32))
        [1,2,3]\t[[1,2],[3,4]]
        """;

    @Test
    void readsOneAndTwoDimensionalArraysFromAQueryResult() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        try (TestHttpServer server = TestHttpServer.start(ARRAY_RESPONSE, requestBody);
             Connection connection = new ClickHouseDriver().connect(server.jdbcUrl(), new Properties());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT [1,2,3], [[1,2],[3,4]]")) {
            assertTrue(resultSet.next());

            assertArrayEquals(new int[] {1, 2, 3}, (int[]) resultSet.getArray("numbers").getArray());
            int[][] nested = (int[][]) resultSet.getArray("nested").getArray();
            assertEquals(2, nested.length);
            assertArrayEquals(new int[] {1, 2}, nested[0]);
            assertArrayEquals(new int[] {3, 4}, nested[1]);
            assertFalse(resultSet.next());
            assertTrue(requestBody.get().contains("FORMAT TabSeparatedWithNamesAndTypes"));
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        private static TestHttpServer start(String responseBody, AtomicReference<String> requestBody)
                throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, responseBody, requestBody));
            server.start();
            return new TestHttpServer(server);
        }

        private static void respond(HttpExchange exchange, String responseBody,
                AtomicReference<String> requestBody) throws IOException {
            try (exchange) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
            }
        }

        private String jdbcUrl() {
            return "jdbc:clickhouse://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort()
                + "/default?use_server_time_zone=false&use_time_zone=UTC&compress=false"
                + "&connection_timeout=10000&socket_timeout=10000";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
