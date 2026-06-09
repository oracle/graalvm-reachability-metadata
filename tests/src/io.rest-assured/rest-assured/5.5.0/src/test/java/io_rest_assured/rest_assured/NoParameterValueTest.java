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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.internal.NoParameterValue;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class NoParameterValueTest {
    @Test
    void generatedGroovyClassHelperResolvesRequestedClassName() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(NoParameterValue.class, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                NoParameterValue.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classHelper.invokeExact("java.lang.String");

        assertEquals(String.class, resolvedClass);
    }

    @Test
    void queryParameterWithoutValueIsSentWithoutEqualsSign() throws Exception {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> sendResponseAndCaptureQuery(exchange, rawQuery));
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .queryParam("available")
                    .when()
                    .get("/search")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(equalTo("ok"));

            assertEquals("available", rawQuery.get());
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendResponseAndCaptureQuery(HttpExchange exchange, AtomicReference<String> rawQuery) throws IOException {
        try {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
