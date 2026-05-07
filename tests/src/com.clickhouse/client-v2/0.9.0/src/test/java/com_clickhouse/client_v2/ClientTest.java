/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientConfigProperties;
import com.clickhouse.client.api.http.ClickHouseHttpProto;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.format.BinaryStreamUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientTest {
    private static final String QUERY = "SELECT id, name FROM people";

    @Test
    void queryAllInstantiatesDtoWithDefaultConstructor() throws Exception {
        byte[] responseBody = rowBinaryWithNamesAndTypesResponse();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> handleQuery(exchange, responseBody, requestBody));
        server.start();

        String endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort();
        try (Client client = new Client.Builder()
                .addEndpoint(endpoint)
                .setUsername("default")
                .setPassword("")
                .setOption(ClientConfigProperties.COMPRESS_SERVER_RESPONSE.getKey(), "false")
                .setOption(ClientConfigProperties.RETRY_ON_FAILURE.getKey(), "0")
                .build()) {
            TableSchema schema = new TableSchema(
                    null, QUERY, "default", ClickHouseColumn.parse("id Int32, name String"));
            try {
                client.register(Person.class, schema);
                List<Person> people = client.queryAll(QUERY, Person.class, schema);

                assertThat(people).extracting(Person::getId).containsExactly(1, 2);
                assertThat(people).extracting(Person::getName).containsExactly("Ada", "Grace");
                assertThat(requestBody.get()).isEqualTo(QUERY);
            } catch (Error error) {
                rethrowUnlessUnsupportedFeatureError(error);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void handleQuery(HttpExchange exchange, byte[] responseBody, AtomicReference<String> requestBody)
            throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            requestBody.set(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }

        exchange.getResponseHeaders().add(ClickHouseHttpProto.HEADER_FORMAT, "RowBinaryWithNamesAndTypes");
        exchange.getResponseHeaders().add(ClickHouseHttpProto.HEADER_TIMEZONE, "UTC");
        exchange.sendResponseHeaders(200, responseBody.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(responseBody);
        }
    }

    private static byte[] rowBinaryWithNamesAndTypesResponse() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryStreamUtils.writeVarInt(output, 2);
        BinaryStreamUtils.writeString(output, "id");
        BinaryStreamUtils.writeString(output, "name");
        BinaryStreamUtils.writeString(output, "Int32");
        BinaryStreamUtils.writeString(output, "String");
        writePerson(output, 1, "Ada");
        writePerson(output, 2, "Grace");
        return output.toByteArray();
    }

    private static void writePerson(OutputStream output, int id, String name) throws IOException {
        BinaryStreamUtils.writeInt32(output, id);
        BinaryStreamUtils.writeString(output, name);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static final class Person {
        private int id;
        private String name;

        public Person() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
