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
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_header_closure8Test {
    @Test
    void sendsAdditionalHeaderValuesAddedThroughVarargs() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/headers", RequestSpecificationImplInner_header_closure8Test::echoTraceHeaders);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header("X-Trace", "alpha", "bravo")
                    .when()
                    .get("/headers");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString())
                    .contains("alpha")
                    .contains("bravo");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
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
