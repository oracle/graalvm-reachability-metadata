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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_newFilterContext_closure13Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_newFilterContext_closure13";

    @Test
    void unnamedPathParametersAreCollectedWhenCreatingFilterContext() throws Throwable {
        MethodHandle classHelper = generatedClassLookup();
        String requestSpecificationClassName = "io.restassured.internal.RequestSpecificationImpl";
        AtomicReference<Class<?>> dynamicallyResolvedClass = new AtomicReference<>();
        AtomicReference<List<String>> observedPathParameterValues = new AtomicReference<>();
        Filter capturePathParameterValues = (requestSpecification, responseSpecification, context) -> {
            try {
                dynamicallyResolvedClass.set(invokeGeneratedClassLookup(classHelper, requestSpecificationClassName));
            } catch (Throwable throwable) {
                throw new AssertionError(throwable);
            }
            observedPathParameterValues.set(new ArrayList<>(requestSpecification.getUnnamedPathParamValues()));
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/shops/tea/items/42",
                RequestSpecificationImplInner_newFilterContext_closure13Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(capturePathParameterValues)
                    .when()
                    .get("/shops/{shop}/items/{itemId}", "tea", 42);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/shops/tea/items/42");
            assertThat(dynamicallyResolvedClass.get().getName()).isEqualTo(requestSpecificationClassName);
            assertThat(observedPathParameterValues.get()).containsExactly("tea", "42");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static MethodHandle generatedClassLookup() throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException {
        Class<?> closureClass = RequestSpecificationImplInner_newFilterContext_closure13Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        return lookup.findStatic(closureClass, "class$", MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> invokeGeneratedClassLookup(MethodHandle classHelper, String className) throws Throwable {
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void sendObservedPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
