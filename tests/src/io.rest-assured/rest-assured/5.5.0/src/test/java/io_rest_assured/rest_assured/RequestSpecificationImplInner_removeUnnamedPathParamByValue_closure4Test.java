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

public class RequestSpecificationImplInner_removeUnnamedPathParamByValue_closure4Test {
    @Test
    void filterRemovesFirstUnnamedPathParameterMatchingValue() throws IOException {
        AtomicReference<List<String>> remainingPathParameterValues = new AtomicReference<>();
        Filter removeRedundantPathParameter = (requestSpecification, responseSpecification, context) -> {
            requestSpecification.removeUnnamedPathParamByValue("redundant-value");
            remainingPathParameterValues.set(new ArrayList<>(requestSpecification.getUnnamedPathParamValues()));
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/library/reference/books/12345",
                RequestSpecificationImplInner_removeUnnamedPathParamByValue_closure4Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(removeRedundantPathParameter)
                    .when()
                    .get("/library/{section}/books/{bookId}", "reference", "12345", "redundant-value");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/library/reference/books/12345");
            assertThat(remainingPathParameterValues.get()).containsExactly("reference", "12345");
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
