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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredResponseOptionsGroovyImplInner_path_closure5Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl"
            + "$_path_closure5";
    private static final String CLASS_NAME_HEADER = "X-Class-Name";

    @Test
    void pathRejectsResponseWithoutContentTypeOrDefaultParser() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/untyped", RestAssuredResponseOptionsGroovyImplInner_path_closure5Test::sendUntypedResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/untyped");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> response.path("message"));

            assertTrue(exception.getMessage().contains("no content-type was present"));
            String classNameFromResponse = response.header(CLASS_NAME_HEADER);
            assertEquals(
                    classNameFromResponse,
                    invokeGeneratedClassLookup(classNameFromResponse).getName());
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendUntypedResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add(CLASS_NAME_HEADER, String.class.getName());
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RestAssuredResponseOptionsGroovyImplInner_path_closure5Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }
}
