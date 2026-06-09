/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
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

public class RequestSpecificationImplInner_getUnnamedPathParams_closure41Test {
    @Test
    void filterReadsUnnamedPathParametersByPlaceholderName() throws IOException {
        AtomicReference<Map<String, String>> unnamedPathParameters = new AtomicReference<>();
        Filter captureUnnamedPathParameters = (requestSpecification,
                responseSpecification,
                context) -> {
            Map<String, String> capturedParameters = new LinkedHashMap<>(
                    requestSpecification.getUnnamedPathParams());
            unnamedPathParameters.set(capturedParameters);
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/library/fiction/books/9780134685991",
                RequestSpecificationImplInner_getUnnamedPathParams_closure41Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(captureUnnamedPathParameters)
                    .when()
                    .get("/library/{section}/books/{isbn}", "fiction", "9780134685991");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/library/fiction/books/9780134685991");
            assertThat(unnamedPathParameters.get()).containsExactly(
                    Map.entry("section", "fiction"),
                    Map.entry("isbn", "9780134685991"));
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void sendObservedPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
