/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.assertion.BodyMatcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponseSpecificationImplInner_body_closure1_closure16Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_body_closure1_closure16";

    @Test
    void validatesAdditionalWholeBodyMatcherThroughGeneratedClosure() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Matcher<String> expectedBody = equalTo("{\"message\":\"hello\"}");
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/message")
                    .then()
                    .body(expectedBody, expectedBody);

            try {
                Class<?> resolvedLookupClass = invokeGeneratedClassLookup(BodyMatcher.class.getName());
                assertEquals(BodyMatcher.class, resolvedLookupClass);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof Error error
                        && NativeImageSupport.isUnsupportedFeatureError(error)) {
                    return;
                }
                throw exception;
            } catch (Error error) {
                if (NativeImageSupport.isUnsupportedFeatureError(error)) {
                    return;
                }
                throw error;
            }
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ResponseSpecificationImplInner_body_closure1_closure16Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
