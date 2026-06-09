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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_sendRequest_closure17Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_sendRequest_closure17";

    @Test
    void patchesBodyAndAppliesResponseAssertionClosure() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/resource",
                RequestSpecificationImplInner_sendRequest_closure17Test::handlePatch);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(new AssertionClosureRequestFilter())
                    .contentType(ContentType.TEXT)
                    .body("updated value")
                    .when()
                    .patch("/resource")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(Matchers.equalTo("patched: updated value"))
                    .extract()
                    .response();

            assertThat(response.asString()).isEqualTo("patched: updated value");
            assertThat(response.header("X-Assertion-Closure-Method")).isEqualTo("PATCH");
            assertThat(invokeGeneratedClassLookup(RestAssured.class.getName())).isEqualTo(RestAssured.class);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = Class.forName(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void handlePatch(HttpExchange exchange) throws IOException {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean expectedPatch = "PATCH".equals(exchange.getRequestMethod())
                    && "updated value".equals(requestBody);
            byte[] responseBytes = (expectedPatch
                    ? "patched: updated value"
                    : "unexpected request").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("X-Assertion-Closure-Method", exchange.getRequestMethod());
            exchange.sendResponseHeaders(
                    expectedPatch ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class AssertionClosureRequestFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpecification,
                FilterableResponseSpecification responseSpecification,
                FilterContext context) {
            return context.next(requestSpecification, responseSpecification);
        }
    }
}
