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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.internal.MapCreator;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.specification.Argument.arg;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapCreatorTest {
    @Test
    void generatedGroovyClassHelperResolvesLibraryClassName() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(MapCreator.class, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                MapCreator.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        String markerClassName = "io.restassured.internal.NoParameterValue";

        Class<?> resolvedClass = (Class<?>) classHelper.invokeExact(markerClassName);

        assertEquals(markerClassName, resolvedClass.getName());
    }

    @Test
    void rejectsAdditionalBodyExpectationArgumentsThatAreNotRestAssuredArguments() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> given()
                            .baseUri("http://127.0.0.1")
                            .port(server.getAddress().getPort())
                            .when()
                            .get("/items")
                            .then()
                            .body(
                                    "items[%d].name",
                                    List.of(arg(0)),
                                    equalTo("first"),
                                    "items[%d].name",
                                    List.of(arg(1)),
                                    equalTo("second"),
                                    "items[%d].name",
                                    List.of("not-a-rest-assured-argument"),
                                    equalTo("third")));

            assertTrue(exception.getMessage().contains("a list of io.restassured.specification.Argument is required"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/json");
            byte[] body = """
                    {"items":[{"name":"first"},{"name":"second"},{"name":"third"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
