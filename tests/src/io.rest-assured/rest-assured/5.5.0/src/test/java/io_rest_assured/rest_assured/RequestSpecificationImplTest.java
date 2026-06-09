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

public class RequestSpecificationImplTest {
    @Test
    void sendsRequestThroughPublicDsl() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/greeting", RequestSpecificationImplTest::sendGreeting);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header("X-Test-Client", "request-specification")
                    .queryParam("name", "native")
                    .when()
                    .get("/greeting");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.contentType()).contains("text/plain");
            assertThat(response.asString()).isEqualTo("GET name=native request-specification");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void sendGreeting(HttpExchange exchange) throws IOException {
        String response = String.join(" ",
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().getFirst("X-Test-Client"));
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
