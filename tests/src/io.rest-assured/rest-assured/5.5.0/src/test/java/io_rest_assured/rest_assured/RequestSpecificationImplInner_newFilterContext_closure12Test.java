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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_newFilterContext_closure12Test {
    @Test
    void requestWithUnnamedPathParameterCreatesFilterContext() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stores/coffee", RequestSpecificationImplInner_newFilterContext_closure12Test::sendObservedPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/stores/{department}", "coffee");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/stores/coffee");
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
