/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.data.ClickHouseColumn;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class POJOSerDeTest {
    @Test
    void insertSerializesRegisteredPojoByInvokingGetter() throws Exception {
        TableSchema schema = new TableSchema(
                "people",
                null,
                null,
                List.of(ClickHouseColumn.of("name", "String")));

        try (CapturingServer server = new CapturingServer();
                Client client = new Client.Builder()
                        .addEndpoint(server.endpoint())
                        .setUsername("default")
                        .setPassword("test")
                        .compressServerResponse(false)
                        .setConnectTimeout(10, ChronoUnit.SECONDS)
                        .setConnectionRequestTimeout(10, ChronoUnit.SECONDS)
                        .setSocketTimeout(10, ChronoUnit.SECONDS)
                        .setExecutionTimeout(10, ChronoUnit.SECONDS)
                        .build()) {
            client.register(InsertPerson.class, schema);

            InsertResponse response = client.insert("people", List.of(new InsertPerson("Ada")))
                    .get(10, TimeUnit.SECONDS);

            assertThat(response).isNotNull();
            byte[] body = server.requestBody();
            String requestPrefix = new String(
                    Arrays.copyOf(body, body.length - 4),
                    StandardCharsets.UTF_8);
            assertThat(requestPrefix)
                    .contains("INSERT INTO people")
                    .contains("FORMAT RowBinary");
            assertThat(Arrays.copyOfRange(body, body.length - 4, body.length))
                    .isEqualTo(new byte[] {3, 'A', 'd', 'a'});
        }
    }

    public static final class InsertPerson {
        private final String name;

        public InsertPerson(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final class CapturingServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private byte[] requestBody;

        private CapturingServer() throws IOException {
            this.executor = Executors.newSingleThreadExecutor();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.server.createContext("/", this::handle);
            this.server.setExecutor(executor);
            this.server.start();
        }

        private String endpoint() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        private byte[] requestBody() {
            return requestBody.clone();
        }

        private void handle(HttpExchange exchange) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exchange.getRequestBody().transferTo(output);
            this.requestBody = output.toByteArray();
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream response = exchange.getResponseBody()) {
                response.flush();
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
