/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_http_client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.ClickHouseClient;
import com.clickhouse.client.ClickHouseCredentials;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseNodeSelector;
import com.clickhouse.client.ClickHouseProtocol;
import com.clickhouse.client.ClickHouseResponse;
import com.clickhouse.client.config.ClickHouseClientOption;
import com.clickhouse.client.config.ClickHouseHealthCheckMethod;
import com.clickhouse.client.http.config.ClickHouseHttpOption;
import com.clickhouse.client.http.config.HttpConnectionProvider;
import com.clickhouse.data.ClickHouseExternalTable;
import com.clickhouse.data.ClickHouseFormat;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class Clickhouse_http_clientTest {
    @Test
    void allConnectionProvidersExecuteQueriesWithHeadersAndParameters() throws Exception {
        for (HttpConnectionProvider provider : HttpConnectionProvider.values()) {
            try (MockClickHouseServer server = MockClickHouseServer.start();
                    ClickHouseClient client = newClient(provider)) {
                ClickHouseNode node = server.node("analytics");

                assertThat(client.accept(ClickHouseProtocol.HTTP)).isTrue();

                String queryId = "query-" + provider.name();
                try (ClickHouseResponse response = client.read(node)
                        .option(ClickHouseClientOption.ASYNC, false)
                        .option(ClickHouseClientOption.CONNECTION_TIMEOUT, 2_000)
                        .option(ClickHouseClientOption.SOCKET_TIMEOUT, 5_000)
                        .option(ClickHouseHttpOption.CONNECTION_PROVIDER, provider)
                        .format(ClickHouseFormat.TabSeparated)
                        .session("session 1", true, 4)
                        .set(ClickHouseClientOption.MAX_RESULT_ROWS.getKey(), "7")
                        .query("SELECT 42 FORMAT TabSeparated", queryId)
                        .executeAndWait()) {
                    assertThat(new String(response.getInputStream().readAllBytes(), UTF_8)).isEqualTo("answer\n");
                    assertThat(response.getTimeZone().getID()).isEqualTo("UTC");
                }

                RequestRecord queryRequest = server.nextRequest();
                assertThat(queryRequest.method()).isEqualTo("POST");
                assertThat(queryRequest.path()).isEqualTo("/");
                assertThat(queryRequest.query())
                        .contains("session_id=session+1")
                        .contains("session_check=1")
                        .contains("session_timeout=4")
                        .contains("query_id=" + queryId)
                        .contains("max_result_rows=7")
                        .contains("extremes=0");
                assertThat(queryRequest.header("X-Test-Header")).isEqualTo("test-value");
                assertThat(queryRequest.header("X-ClickHouse-Database")).isEqualTo("analytics");
                assertThat(queryRequest.header("X-ClickHouse-Format")).isEqualTo("TabSeparated");
                assertThat(queryRequest.header("User-Agent")).contains("ClickHouse");
                assertThat(queryRequest.bodyAsString()).contains("SELECT 42 FORMAT TabSeparated");
            }
        }
    }

    @Test
    void pingUsesClickHousePingEndpoint() throws Exception {
        try (MockClickHouseServer server = MockClickHouseServer.start();
                ClickHouseClient client = newClient(HttpConnectionProvider.APACHE_HTTP_CLIENT)) {
            assertThat(client.ping(server.node("default"), 2_000)).isTrue();

            RequestRecord pingRequest = server.nextRequest();
            assertThat(pingRequest.method()).isEqualTo("GET");
            assertThat(pingRequest.path()).isEqualTo("/ping");
        }
    }

    @Test
    void queryWithExternalTableUsesMultipartPostBody() throws Exception {
        try (MockClickHouseServer server = MockClickHouseServer.start();
                ClickHouseClient client = newClient(HttpConnectionProvider.APACHE_HTTP_CLIENT)) {
            ClickHouseExternalTable table = ClickHouseExternalTable.builder()
                    .name("external_numbers")
                    .columns("n UInt8")
                    .format(ClickHouseFormat.CSV)
                    .content(input("1\n2\n"))
                    .build();

            try (ClickHouseResponse response = client.read(server.node("default"))
                    .option(ClickHouseClientOption.ASYNC, false)
                    .option(ClickHouseHttpOption.CONNECTION_PROVIDER, HttpConnectionProvider.APACHE_HTTP_CLIENT)
                    .format(ClickHouseFormat.TabSeparated)
                    .query("SELECT sum(n) FROM external_numbers")
                    .external(List.of(table))
                    .executeAndWait()) {
                assertThat(new String(response.getInputStream().readAllBytes(), UTF_8)).isEqualTo("answer\n");
            }

            RequestRecord request = server.nextRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Content-Type")).startsWith("multipart/form-data; boundary=");
            assertThat(request.bodyAsString())
                    .contains("name=\"query\"")
                    .contains("SELECT sum(n) FROM external_numbers")
                    .contains("name=\"external_numbers_format\"")
                    .contains("CSV")
                    .contains("name=\"external_numbers_structure\"")
                    .contains("n UInt8")
                    .contains("name=\"external_numbers\"; filename=\"external_numbers\"")
                    .contains("content-type: application/octet-stream")
                    .contains("1\n2\n");
        }
    }

    @Test
    void writeStreamsInsertDataInRequestBody() throws Exception {
        try (MockClickHouseServer server = MockClickHouseServer.start();
                ClickHouseClient client = newClient(HttpConnectionProvider.HTTP_URL_CONNECTION)) {
            try (ClickHouseResponse ignored = client.write(server.node("default"))
                    .option(ClickHouseClientOption.ASYNC, false)
                    .option(ClickHouseHttpOption.CONNECTION_PROVIDER, HttpConnectionProvider.HTTP_URL_CONNECTION)
                    .format(ClickHouseFormat.CSV)
                    .table("events")
                    .data(input("1,Alice\n2,Bob\n"))
                    .executeAndWait()) {
                assertThat(ignored).isNotNull();
            }

            RequestRecord request = server.nextRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Content-Type")).startsWith("text/plain");
            assertThat(request.bodyAsString())
                    .contains("INSERT INTO events")
                    .contains("FORMAT CSV")
                    .contains("1,Alice\n2,Bob\n");
        }
    }

    @Test
    void credentialsAreSentAsBasicAuthenticationHeader() throws Exception {
        ClickHouseCredentials credentials = ClickHouseCredentials.fromUserAndPassword("analytics_user", "secret-key");
        try (MockClickHouseServer server = MockClickHouseServer.start();
                ClickHouseClient client = ClickHouseClient.newInstance(credentials, ClickHouseProtocol.HTTP)) {
            try (ClickHouseResponse response = client.read(server.node("default"))
                    .option(ClickHouseClientOption.ASYNC, false)
                    .option(ClickHouseClientOption.COMPRESS, false)
                    .option(ClickHouseClientOption.CONNECTION_TIMEOUT, 2_000)
                    .option(ClickHouseClientOption.SOCKET_TIMEOUT, 5_000)
                    .option(ClickHouseHttpOption.CONNECTION_PROVIDER, HttpConnectionProvider.APACHE_HTTP_CLIENT)
                    .format(ClickHouseFormat.TabSeparated)
                    .query("SELECT currentUser()")
                    .executeAndWait()) {
                assertThat(new String(response.getInputStream().readAllBytes(), UTF_8)).isEqualTo("answer\n");
            }

            RequestRecord request = server.nextRequest();
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString("analytics_user:secret-key".getBytes(UTF_8));
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.header("Authorization")).isEqualTo("Basic " + encodedCredentials);
            assertThat(request.header("X-ClickHouse-User")).isNull();
            assertThat(request.header("X-ClickHouse-Key")).isNull();
        }
    }

    private static ClickHouseClient newClient(HttpConnectionProvider provider) {
        return ClickHouseClient.builder()
                .nodeSelector(ClickHouseNodeSelector.of(ClickHouseProtocol.HTTP))
                .option(ClickHouseClientOption.ASYNC, false)
                .option(ClickHouseClientOption.COMPRESS, false)
                .option(ClickHouseClientOption.HEALTH_CHECK_METHOD, ClickHouseHealthCheckMethod.PING)
                .option(ClickHouseClientOption.CONNECTION_TIMEOUT, 2_000)
                .option(ClickHouseClientOption.SOCKET_TIMEOUT, 5_000)
                .option(ClickHouseHttpOption.CONNECTION_PROVIDER, provider)
                .option(ClickHouseHttpOption.CUSTOM_HEADERS, "X-Test-Header=test-value")
                .build();
    }

    private static InputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(UTF_8));
    }

    private static final class MockClickHouseServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final BlockingQueue<RequestRecord> requests;

        private MockClickHouseServer(HttpServer server, ExecutorService executor,
                BlockingQueue<RequestRecord> requests) {
            this.server = server;
            this.executor = executor;
            this.requests = requests;
        }

        static MockClickHouseServer start() throws IOException {
            BlockingQueue<RequestRecord> requests = new LinkedBlockingQueue<>();
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mock-clickhouse-http-server");
                thread.setDaemon(true);
                return thread;
            });
            server.createContext("/", exchange -> handle(exchange, requests));
            server.setExecutor(executor);
            server.start();
            return new MockClickHouseServer(server, executor, requests);
        }

        ClickHouseNode node(String database) {
            return ClickHouseNode.of("http://127.0.0.1:" + server.getAddress().getPort() + "/" + database);
        }

        RequestRecord nextRequest() throws InterruptedException {
            RequestRecord request = requests.poll(5, SECONDS);
            assertThat(request).as("recorded HTTP request").isNotNull();
            return request;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, SECONDS);
        }

        private static void handle(HttpExchange exchange, BlockingQueue<RequestRecord> requests) throws IOException {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(new RequestRecord(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(), copyHeaders(exchange.getRequestHeaders()), requestBody));

            if ("GET".equals(exchange.getRequestMethod()) && "/ping".equals(exchange.getRequestURI().getPath())) {
                send(exchange, 200, "Ok.\n");
            } else if ("POST".equals(exchange.getRequestMethod())) {
                Headers headers = exchange.getResponseHeaders();
                headers.add("X-ClickHouse-Server-Display-Name", "mock-clickhouse");
                headers.add("X-ClickHouse-Query-Id", "server-query-id");
                headers.add("X-ClickHouse-Format", "TabSeparated");
                headers.add("X-ClickHouse-Timezone", "UTC");
                headers.add("X-ClickHouse-Summary",
                        "{\"read_rows\":\"1\",\"read_bytes\":\"8\",\"written_rows\":\"2\","
                                + "\"written_bytes\":\"14\",\"total_rows_to_read\":\"1\"}");
                send(exchange, 200, "answer\n");
            } else {
                send(exchange, 405, "unsupported method\n");
            }
        }

        private static Headers copyHeaders(Headers original) {
            Headers copy = new Headers();
            original.forEach((name, values) -> copy.put(name, List.copyOf(values)));
            return copy;
        }

        private static void send(HttpExchange exchange, int status, String body) throws IOException {
            byte[] response = body.getBytes(UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }

    private record RequestRecord(String method, String path, String query, Headers headers, byte[] body) {
        String header(String name) {
            return headers.getFirst(name);
        }

        public String query() {
            return query == null ? "" : query;
        }

        String bodyAsString() {
            return new String(body, UTF_8);
        }
    }
}
