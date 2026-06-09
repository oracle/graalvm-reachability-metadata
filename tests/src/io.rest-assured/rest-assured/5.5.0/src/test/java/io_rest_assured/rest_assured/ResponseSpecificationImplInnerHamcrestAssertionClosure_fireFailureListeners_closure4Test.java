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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.config.FailureConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.listener.ResponseValidationFailureListener;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosure_fireFailureListeners_closure4Test {
    @Test
    void invokesConfiguredFailureListenerWhenResponseValidationFails() throws Throwable {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        AtomicBoolean generatedLookupCalled = new AtomicBoolean(false);
        AtomicInteger observedStatus = new AtomicInteger(-1);
        ResponseValidationFailureListener listener = (requestSpecification, responseSpecification, response) -> {
            listenerCalled.set(true);
            observedStatus.set(response.statusCode());
            String responseClassName = "io.restassured.response.Response";
            try {
                assertEquals(responseClassName, invokeGeneratedClassLookup(responseClassName).getName());
                generatedLookupCalled.set(true);
            } catch (Throwable throwable) {
                throw new AssertionError(throwable);
            }
        };

        RestAssuredConfig config = RestAssuredConfig.config()
                .failureConfig(FailureConfig.failureConfig().failureListeners(listener));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/created", this::sendOkResponse);
        server.setExecutor(executor);
        server.start();

        try {
            AssertionError error = assertThrows(AssertionError.class, () -> given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/created")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_CREATED));

            assertTrue(error.getMessage().contains("1 expectation failed"));
            assertTrue(listenerCalled.get());
            assertTrue(generatedLookupCalled.get());
            assertEquals(HttpURLConnection.HTTP_OK, observedStatus.get());
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        String closureClassName = "io.restassured.internal."
                + "ResponseSpecificationImpl$_HamcrestAssertionClosure_fireFailureListeners_closure4";
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> closureClass = classLoader.loadClass(closureClassName);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private void sendOkResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
