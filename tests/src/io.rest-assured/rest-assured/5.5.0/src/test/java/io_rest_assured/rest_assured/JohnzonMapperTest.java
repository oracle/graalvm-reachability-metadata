/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class JohnzonMapperTest {
    @Test
    void serializesAndDeserializesJsonWithExplicitJohnzonObjectMapper() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/payload", JohnzonMapperTest::handlePayload);
        server.start();
        RestAssured.reset();

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("message", "request");
            requestBody.put("count", 7);

            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body(requestBody, ObjectMapperType.JOHNZON)
                    .when()
                    .post("/payload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            Map<?, ?> payload = response.as(Map.class, ObjectMapperType.JOHNZON);
            assertThat(payload.get("message")).isEqualTo("accepted");
            assertThat(((Number) payload.get("count")).intValue()).isEqualTo(8);
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void handlePayload(HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean johnzonMapperWasUsed = "POST".equals(exchange.getRequestMethod())
                    && body.contains("\"message\"")
                    && body.contains("\"request\"")
                    && body.contains("\"count\"")
                    && body.contains("7");
            byte[] responseBytes = (johnzonMapperWasUsed
                    ? "{\"message\":\"accepted\",\"count\":8}"
                    : "{\"message\":\"unexpected\",\"count\":0}").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(
                    johnzonMapperWasUsed ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
