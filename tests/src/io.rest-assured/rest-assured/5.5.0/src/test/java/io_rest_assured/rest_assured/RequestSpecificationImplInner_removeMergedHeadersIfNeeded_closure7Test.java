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
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.HeaderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_removeMergedHeadersIfNeeded_closure7";

    @Test
    void overwritesConfiguredHeadersWhenSeveralValuesAreAdded() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/headers",
                RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7Test::echoTraceHeaders);
        server.start();
        RestAssured.reset();

        try {
            RestAssuredConfig config = RestAssured.config()
                    .headerConfig(HeaderConfig.headerConfig().overwriteHeadersWithName("X-Trace"));

            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .config(config)
                    .header("X-Trace", "alpha")
                    .header("x-trace", "bravo")
                    .when()
                    .get("/headers");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("bravo");
            assertThat(invokeGeneratedClassLookup(RestAssured.class.getName())).isEqualTo(RestAssured.class);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_removeMergedHeadersIfNeeded_closure7Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void echoTraceHeaders(HttpExchange exchange) throws IOException {
        List<String> headers = exchange.getRequestHeaders().get("X-Trace");
        String response = headers == null ? "" : String.join(",", headers);
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
