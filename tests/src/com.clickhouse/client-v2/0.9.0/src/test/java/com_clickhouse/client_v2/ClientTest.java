/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.Client;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientTest {
    @Test
    void queryAllInstantiatesRegisteredPojoWithDefaultConstructor() throws Exception {
        String sql = "SELECT name FROM people";
        TableSchema schema = new TableSchema(
                null,
                sql,
                null,
                List.of(ClickHouseColumn.of("name", "String")));

        try (RowBinaryServer server = new RowBinaryServer(rowBinaryWithNamesAndTypes("name", "String", "Ada"));
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
            client.register(Person.class, schema);

            List<Person> people = client.queryAll(sql, Person.class, schema, null);

            assertThat(people).extracting(Person::getName).containsExactly("Ada");
        }
    }

    private static byte[] rowBinaryWithNamesAndTypes(String name, String type, String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeVarInt(output, 1);
        writeString(output, name);
        writeString(output, type);
        writeString(output, value);
        return output.toByteArray();
    }

    private static void writeString(OutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeVarInt(OutputStream output, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            output.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        output.write(remaining);
    }

    public static final class Person {
        private String name;

        public Person() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static final class RowBinaryServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final byte[] responseBody;

        private RowBinaryServer(byte[] responseBody) throws IOException {
            this.responseBody = responseBody.clone();
            this.executor = Executors.newSingleThreadExecutor();
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.server.createContext("/", this::handle);
            this.server.setExecutor(executor);
            this.server.start();
        }

        private String endpoint() {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(responseBody);
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
