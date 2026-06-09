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

public class RequestSpecificationImplInner_getUnnamedPathParamValues_closure43Test {
    @Test
    void filterCanReadUnnamedPathParameterValuesInRequestOrder() throws IOException {
        AtomicReference<List<String>> unnamedPathParameterValues = new AtomicReference<>();
        Filter captureUnnamedPathParameterValues = (requestSpecification, responseSpecification, context) -> {
            requestSpecification.removeUnnamedPathParamByValue("unused-extra-value");
            unnamedPathParameterValues.set(new ArrayList<>(requestSpecification.getUnnamedPathParamValues()));
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/library/sci-fi/books/42",
                RequestSpecificationImplInner_getUnnamedPathParamValues_closure43Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(captureUnnamedPathParameterValues)
                    .when()
                    .get("/library/{category}/books/{bookId}", "sci-fi", 42, "unused-extra-value");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/library/sci-fi/books/42");
            assertThat(unnamedPathParameterValues.get()).containsExactly("sci-fi", "42");
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
