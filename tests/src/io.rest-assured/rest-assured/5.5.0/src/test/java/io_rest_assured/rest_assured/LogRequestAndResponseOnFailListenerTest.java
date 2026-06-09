/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogRequestAndResponseOnFailListenerTest {
    @Test
    void logsRequestAndResponseWhenValidationDoesNotMatch() throws Throwable {
        ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(logBytes, true, StandardCharsets.UTF_8);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", this::sendTextResponse);
        server.setExecutor(executor);
        server.start();

        try {
            RestAssuredConfig config = RestAssuredConfig.config()
                    .logConfig(LogConfig.logConfig().defaultStream(logStream));

            assertThrows(AssertionError.class, () -> given()
                    .config(config)
                    .log().ifValidationFails(LogDetail.ALL)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/status")
                    .then()
                    .log().ifValidationFails(LogDetail.ALL)
                    .statusCode(HttpURLConnection.HTTP_CREATED));

            logStream.flush();
            String log = logBytes.toString(StandardCharsets.UTF_8);
            assertTrue(log.contains("Request method:"), log);
            assertTrue(log.contains("GET"), log);
            assertTrue(log.contains("ready"), log);

            String listenerClassName = "io.restassured.internal.LogRequestAndResponseOnFailListener";
            assertEquals(listenerClassName, invokeGeneratedClassLookup(listenerClassName).getName());
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            logStream.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> listenerClass = LogRequestAndResponseOnFailListenerTest.class
                .getClassLoader()
                .loadClass("io.restassured.internal.LogRequestAndResponseOnFailListener");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(listenerClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                listenerClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private void sendTextResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/plain; charset=utf-8");
            byte[] body = "ready".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
