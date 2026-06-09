/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_getUnnamedPathParams_closure42Test {
    @Test
    void filterMaterializesUnnamedPathParametersAsMapEntries() throws Throwable {
        String dynamicallyLoadedClassName = RestAssured.class.getName();
        assertThat(invokeGeneratedClassLookup(dynamicallyLoadedClassName)).isSameAs(RestAssured.class);

        AtomicReference<Map<String, String>> unnamedPathParameters = new AtomicReference<>();
        Filter captureUnnamedPathParameters = (requestSpecification,
                responseSpecification,
                context) -> {
            unnamedPathParameters.set(new LinkedHashMap<>(requestSpecification.getUnnamedPathParams()));
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/stores/coffee/items/42",
                RequestSpecificationImplInner_getUnnamedPathParams_closure42Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(captureUnnamedPathParameters)
                    .when()
                    .get("/stores/{department}/items/{itemId}", "coffee", "42");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/stores/coffee/items/42");
            assertThat(unnamedPathParameters.get()).containsExactly(
                    Map.entry("department", "coffee"),
                    Map.entry("itemId", "42"));
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_getUnnamedPathParams_closure42Test.class
                .getClassLoader()
                .loadClass("io.restassured.internal.RequestSpecificationImpl$_getUnnamedPathParams_closure42");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void sendObservedPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
