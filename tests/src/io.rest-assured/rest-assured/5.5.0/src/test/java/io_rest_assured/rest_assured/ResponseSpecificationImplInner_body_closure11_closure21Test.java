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
import io.restassured.http.ContentType;
import io.restassured.internal.assertion.BodyMatcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponseSpecificationImplInner_body_closure11_closure21Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_body_closure11_closure21";

    @Test
    void validatesDuplicateParameterizedAdditionalBodyExpectations() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/catalog")
                    .then()
                    .body(
                            "items[%d].name", withArgs(0), equalTo("book"),
                            "items[%d].name", withArgs(1), equalTo("pen"),
                            "items[%d].name", withArgs(2), equalTo("bag"));

            try {
                assertEquals(BodyMatcher.class, invokeGeneratedClassLookup(BodyMatcher.class.getName()));
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
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = """
                    {"items":[{"name":"book"},{"name":"pen"},{"name":"bag"}]}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = ResponseSpecificationImplInner_body_closure11_closure21Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
