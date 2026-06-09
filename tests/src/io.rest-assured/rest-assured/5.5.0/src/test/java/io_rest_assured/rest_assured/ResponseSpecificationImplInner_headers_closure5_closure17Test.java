/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.assertion.HeaderMatcher;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_headers_closure5_closure17Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_headers_closure5_closure17";

    @Test
    void validatesListValuedHeaderExpectationsFromMap() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/headers",
                ResponseSpecificationImplInner_headers_closure5_closure17Test::sendHeaderResponse);
        server.setExecutor(executor);
        server.start();

        try {
            Map<String, Object> expectedHeaders = new LinkedHashMap<>();
            expectedHeaders.put("X-Cache", Arrays.asList(equalTo("cache-hit"), containsString("hit")));
            expectedHeaders.put("X-Trace", equalTo("trace-123"));

            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/headers")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .headers(expectedHeaders);

            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(HeaderMatcher.class.getName());
            assertEquals(HeaderMatcher.class, resolvedLookupClass);
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ResponseSpecificationImplInner_headers_closure5_closure17Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        assertNotNull(closureClass);
        Method generatedLookup = closureClass.getDeclaredMethod("class$", String.class);
        generatedLookup.setAccessible(true);
        return (Class<?>) generatedLookup.invoke(null, className);
    }

    private static void sendHeaderResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("X-Cache", "cache-hit");
            headers.add("X-Trace", "trace-123");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
