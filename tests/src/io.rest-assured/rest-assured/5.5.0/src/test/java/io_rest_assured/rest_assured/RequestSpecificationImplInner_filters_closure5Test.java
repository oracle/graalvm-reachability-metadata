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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_filters_closure5Test {
    @Test
    void requestSpecificationFilterVarargsAddAndExecuteAdditionalFilters() throws IOException {
        List<String> invocations = new ArrayList<>();
        Filter firstFilter = (requestSpecification, responseSpecification, context) -> {
            invocations.add("first");
            return context.next(requestSpecification, responseSpecification);
        };
        Filter additionalFilter = (requestSpecification, responseSpecification, context) -> {
            invocations.add("additional");
            return context.next(requestSpecification, responseSpecification);
        };

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/filters",
                RequestSpecificationImplInner_filters_closure5Test::sendOk);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filters(firstFilter, additionalFilter)
                    .when()
                    .get("/filters");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("ok");
            assertThat(invocations).containsExactly("first", "additional");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void sendOk(HttpExchange exchange) throws IOException {
        byte[] responseBytes = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
