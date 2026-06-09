/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import io.restassured.specification.Argument;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.specification.Argument.arg;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MapCreatorInner_isRestAssuredArguments_closure1Test {
    @Test
    void bodyExpectationsValidateRestAssuredArgumentLists() throws Throwable {
        CapturingArgumentList arguments = new CapturingArgumentList(arg(0));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/items")
                    .then()
                    .body(
                            "items[0].name",
                            equalTo("first"),
                            "items[%d].name",
                            arguments,
                            equalTo("first"));

            Closure<?> predicate = arguments.getPredicate();
            assertNotNull(predicate);
            assertEquals("io.restassured.internal.MapCreator$_isRestAssuredArguments_closure1", predicate.getClass().getName());
            Class<?> resolvedClass = invokeGeneratedClassLookup(predicate, Argument.class.getName());
            assertEquals(Argument.class, resolvedClass);
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        Class<?> closureClass = closure.getClass();
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "application/json");
            byte[] body = """
                    {"items":[{"name":"first"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    public static final class CapturingArgumentList extends ArrayList<Argument> {
        private transient Closure<?> predicate;

        CapturingArgumentList(Argument... arguments) {
            super(Arrays.asList(arguments));
        }

        public boolean every(Closure<?> candidate) {
            predicate = candidate;
            return stream().allMatch(argument -> Boolean.TRUE.equals(candidate.call(argument)));
        }

        Closure<?> getPredicate() {
            return predicate;
        }
    }
}
