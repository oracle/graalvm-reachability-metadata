/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_headers_closure5Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_headers_closure5";

    @Test
    void recordsMultipleHeaderExpectationsFromMap() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/headers", ResponseSpecificationImplInner_headers_closure5Test::sendHeaderResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Map<String, Object> expectedHeaders = new LinkedHashMap<>();
            expectedHeaders.put("X-Request-Id", equalTo("request-1"));
            expectedHeaders.put("X-Trace-Id", startsWith("trace-"));

            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/headers")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .headers(expectedHeaders);

            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(Map.class.getName());
            assertEquals(Map.class, resolvedLookupClass);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendHeaderResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("X-Request-Id", "request-1");
            headers.add("X-Trace-Id", "trace-abc");
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ResponseSpecificationImplInner_headers_closure5Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        assertNotNull(closureClass);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodType signature = MethodType.methodType(Class.class, String.class);
        MethodHandle generatedLookup = lookup.findStatic(closureClass, "class$", signature);
        return (Class<?>) generatedLookup.invoke(className);
    }
}
