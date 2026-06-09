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
import io.restassured.specification.FilterableRequestSpecification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_removeHeader_closure1Test {
    @Test
    void removesHeaderByNameBeforeSendingRequest() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/headers", RequestSpecificationImplInner_removeHeader_closure1Test::echoSelectedHeaders);
        server.start();
        RestAssured.reset();

        try {
            FilterableRequestSpecification specification = (FilterableRequestSpecification) RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header("X-Keep", "yes")
                    .header("X-Remove", "no");

            specification.removeHeader("x-remove");
            Response response = specification.when().get("/headers");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString())
                    .contains("X-Keep=yes")
                    .doesNotContain("X-Remove=no");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void echoSelectedHeaders(HttpExchange exchange) throws IOException {
        String keepHeader = exchange.getRequestHeaders().getFirst("X-Keep");
        String removeHeader = exchange.getRequestHeaders().getFirst("X-Remove");
        String response = "X-Keep=" + keepHeader + "\nX-Remove=" + removeHeader;
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
