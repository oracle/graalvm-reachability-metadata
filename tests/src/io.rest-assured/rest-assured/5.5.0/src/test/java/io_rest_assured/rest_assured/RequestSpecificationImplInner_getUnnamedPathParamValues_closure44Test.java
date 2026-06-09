/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
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

public class RequestSpecificationImplInner_getUnnamedPathParamValues_closure44Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_getUnnamedPathParamValues_closure44";

    @Test
    void filterReadsCollectedUnnamedPathParameterValues() throws Throwable {
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup("io.restassured.internal.RequestSpecificationImpl");
        assertThat(dynamicallyResolvedClass.getName()).isEqualTo("io.restassured.internal.RequestSpecificationImpl");

        AtomicReference<List<String>> collectedUnnamedValues = new AtomicReference<>();
        Filter captureUnnamedValues = (requestSpecification, responseSpecification, context) -> {
            collectedUnnamedValues.set(new ArrayList<>(requestSpecification.getUnnamedPathParamValues()));
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/catalog/fiction/books/7",
                RequestSpecificationImplInner_getUnnamedPathParamValues_closure44Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(captureUnnamedValues)
                    .when()
                    .get("/catalog/{category}/books/{bookId}", "fiction", 7);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/catalog/fiction/books/7");
            assertThat(collectedUnnamedValues.get()).containsExactly("fiction", "7");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_getUnnamedPathParamValues_closure44Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private static void sendObservedPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
