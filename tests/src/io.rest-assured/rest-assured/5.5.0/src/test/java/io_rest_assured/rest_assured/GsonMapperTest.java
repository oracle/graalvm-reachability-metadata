/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.mapping.GsonMapper;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class GsonMapperTest {
    @Test
    void resolvesGeneratedGroovyClassLiteralHelper() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(RestAssured.class.getName());

        assertThat(resolvedClass).isEqualTo(RestAssured.class);
    }

    @Test
    void resolvesGeneratedGroovyClassLiteralHelperThroughGroovyInvocation() {
        Object resolvedClass = InvokerHelper.invokeStaticMethod(
                GsonMapper.class,
                "class$",
                new Object[] {GsonMapper.class.getName()});

        assertThat(resolvedClass).isEqualTo(GsonMapper.class);
    }

    @Test
    void serializesAndDeserializesJsonWithExplicitGsonObjectMapper() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payload", GsonMapperTest::handlePayload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body(new Payload("request", 7), ObjectMapperType.GSON)
                    .when()
                    .post("/payload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            Payload payload = response.as(Payload.class, ObjectMapperType.GSON);
            assertThat(payload.message).isEqualTo("accepted");
            assertThat(payload.count).isEqualTo(8);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(GsonMapper.class, MethodHandles.lookup());
        MethodHandle classHelper = privateLookup.findStatic(
                GsonMapper.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handlePayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean gsonMapperWasUsed = "POST".equals(exchange.getRequestMethod())
                    && body.contains("\"message\":\"request\"")
                    && body.contains("\"count\":7");
            byte[] responseBytes = (gsonMapperWasUsed
                    ? "{\"message\":\"accepted\",\"count\":8}"
                    : "{\"message\":\"unexpected\",\"count\":0}").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                    gsonMapperWasUsed ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
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
}
