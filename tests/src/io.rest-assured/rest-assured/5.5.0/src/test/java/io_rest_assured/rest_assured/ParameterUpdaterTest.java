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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterUpdaterTest {
    @Test
    void appliesMapQueryParametersThroughPublicRequestSpecification() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", ParameterUpdaterTest::echoQueryParameters);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("category", "books");
            parameters.put("tag", List.of("java", "native"));

            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .queryParams(parameters)
                    .when()
                    .get("/search");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).contains("category=books", "tag=java", "tag=native");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void echoQueryParameters(HttpExchange exchange) throws IOException {
        try {
            String response = String.join("\n", decodeQueryPairs(exchange.getRequestURI().getRawQuery()));
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static List<String> decodeQueryPairs(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return List.of();
        }

        List<String> decodedPairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            decodedPairs.add(URLDecoder.decode(pair, StandardCharsets.UTF_8));
        }
        return decodedPairs;
    }
}
