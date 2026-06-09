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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_cookies_closure9Test {
    @Test
    void sendsCookiesAddedThroughMap() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookies", RequestSpecificationImplInner_cookies_closure9Test::echoCookieHeader);
        server.start();
        RestAssured.reset();

        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("flavor", "vanilla");
        cookies.put("shape", "round");

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .cookies(cookies)
                    .when()
                    .get("/cookies");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString())
                    .contains("flavor=vanilla")
                    .contains("shape=round");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void echoCookieHeader(HttpExchange exchange) throws IOException {
        String response = exchange.getRequestHeaders().getFirst("Cookie");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
