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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.json.bind.Jsonb;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.ObjectMapperConfig.objectMapperConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class JsonbMapperInner_serialize_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.mapping.JsonbMapper"
            + "$_serialize_closure1";
    private static final String DYNAMIC_CLASS_NAME = "io.restassured.internal.proxy."
            + "RestAssuredProxySelectorRoutePlanner";

    @Test
    void serializesRequestBodyThroughJsonbClosure() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jsonb-closure", JsonbMapperInner_serialize_closure1Test::handleJsonbPayload);
        server.start();
        RestAssured.reset();
        Class<?> closureClass = JsonbMapperInner_serialize_closure1Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);

        try {
            RestAssuredConfig config = RestAssuredConfig.config().objectMapperConfig(
                    objectMapperConfig().jsonbObjectMapperFactory((type, charset) -> new TestJsonb()));
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("message", "closure");
            requestBody.put("count", 5);

            Response response = given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body(requestBody, ObjectMapperType.JSONB)
                    .when()
                    .post("/jsonb-closure");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("jsonb closure serialized request");
            assertThat(invokeGeneratedClassLookup(closureClass, DYNAMIC_CLASS_NAME).getName())
                    .isEqualTo(DYNAMIC_CLASS_NAME);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Class<?> closureClass, String className) throws Throwable {
        assertThat(closureClass.getName()).isEqualTo(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = privateLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handleJsonbPayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean closureSerializedRequest = "POST".equals(exchange.getRequestMethod())
                    && body.contains("\"message\":\"closure\"")
                    && body.contains("\"count\":5");
            byte[] responseBytes = (closureSerializedRequest
                    ? "jsonb closure serialized request"
                    : "jsonb closure did not serialize request").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    closureSerializedRequest ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class TestJsonb implements Jsonb {
        @Override
        public <T> T fromJson(String json, Class<T> type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public <T> T fromJson(String json, Type type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public <T> T fromJson(Reader reader, Class<T> type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public <T> T fromJson(Reader reader, Type type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public <T> T fromJson(InputStream stream, Class<T> type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public <T> T fromJson(InputStream stream, Type type) {
            throw new UnsupportedOperationException("Deserialization is not used by this test");
        }

        @Override
        public String toJson(Object object) {
            Map<?, ?> payload = (Map<?, ?>) object;
            return "{\"message\":\"" + payload.get("message") + "\",\"count\":" + payload.get("count") + "}";
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
    }
}
