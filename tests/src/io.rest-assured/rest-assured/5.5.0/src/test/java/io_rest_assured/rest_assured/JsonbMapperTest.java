/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import javax.json.bind.Jsonb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.internal.mapping.JsonbMapper;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class JsonbMapperTest {
    @Test
    void resolvesGeneratedGroovyClassLiteralHelper() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(String.class.getName());

        assertThat(resolvedClass).isEqualTo(String.class);
    }

    @Test
    void serializesAndDeserializesJsonWithExplicitJsonbObjectMapper() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payload", JsonbMapperTest::handlePayload);
        server.start();
        RestAssured.reset();

        try {
            RestAssuredConfig config = RestAssuredConfig.config().objectMapperConfig(
                    objectMapperConfig().jsonbObjectMapperFactory((type, charset) -> new TestJsonb()));

            Response response = given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body(new Payload("request", 7), ObjectMapperType.JSONB)
                    .when()
                    .post("/payload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            Payload payload = response.as(Payload.class, ObjectMapperType.JSONB);
            assertThat(payload.message).isEqualTo("accepted");
            assertThat(payload.count).isEqualTo(8);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(JsonbMapper.class, MethodHandles.lookup());
        MethodHandle classHelper = privateLookup.findStatic(
                JsonbMapper.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handlePayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean jsonbMapperWasUsed = "POST".equals(exchange.getRequestMethod())
                    && body.contains("\"message\":\"request\"")
                    && body.contains("\"count\":7");
            byte[] responseBytes = (jsonbMapperWasUsed
                    ? "{\"message\":\"accepted\",\"count\":8}"
                    : "{\"message\":\"unexpected\",\"count\":0}").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                    jsonbMapperWasUsed ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    public static class Payload {
        public String message;
        public int count;

        public Payload() {
        }

        Payload(String message, int count) {
            this.message = message;
            this.count = count;
        }
    }

    @SuppressWarnings("unchecked")
    private static final class TestJsonb implements Jsonb {
        @Override
        public <T> T fromJson(String json, Class<T> type) {
            return fromJson(json, (Type) type);
        }

        @Override
        public <T> T fromJson(String json, Type type) {
            if (Payload.class.equals(type)) {
                return (T) new Payload(valueOf(json, "message"), numberOf(json, "count"));
            }
            throw new UnsupportedOperationException("Unsupported JSON-B target type: " + type);
        }

        @Override
        public <T> T fromJson(Reader reader, Class<T> type) {
            return fromJson(readAll(reader), type);
        }

        @Override
        public <T> T fromJson(Reader reader, Type type) {
            return fromJson(readAll(reader), type);
        }

        @Override
        public <T> T fromJson(InputStream stream, Class<T> type) {
            return fromJson(readAll(stream), type);
        }

        @Override
        public <T> T fromJson(InputStream stream, Type type) {
            return fromJson(readAll(stream), type);
        }

        @Override
        public String toJson(Object object) {
            Payload payload = (Payload) object;
            return "{\"message\":\"" + payload.message + "\",\"count\":" + payload.count + "}";
        }

        @Override
        public String toJson(Object object, Type type) {
            return toJson(object);
        }

        @Override
        public void toJson(Object object, Writer writer) {
            try {
                writer.write(toJson(object));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void toJson(Object object, Type type, Writer writer) {
            toJson(object, writer);
        }

        @Override
        public void toJson(Object object, OutputStream stream) {
            try {
                stream.write(toJson(object).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void toJson(Object object, Type type, OutputStream stream) {
            toJson(object, stream);
        }

        @Override
        public void close() {
        }

        private static String readAll(Reader reader) {
            try {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[256];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String readAll(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static String valueOf(String json, String name) {
            String prefix = "\"" + name + "\":\"";
            int start = json.indexOf(prefix) + prefix.length();
            int end = json.indexOf('"', start);
            return json.substring(start, end);
        }

        private static int numberOf(String json, String name) {
            String prefix = "\"" + name + "\":";
            int start = json.indexOf(prefix) + prefix.length();
            int end = json.indexOf('}', start);
            return Integer.parseInt(json.substring(start, end));
        }
    }
}
