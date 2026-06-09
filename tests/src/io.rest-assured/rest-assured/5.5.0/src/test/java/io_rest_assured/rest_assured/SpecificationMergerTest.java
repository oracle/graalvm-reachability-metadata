/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class SpecificationMergerTest {
    @Test
    void mergesReusableRequestAndResponseSpecifications() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/merge", this::sendMergedSpecificationResponse);
        server.setExecutor(executor);
        server.start();

        try {
            RequestSpecification requestSpecification = new RequestSpecBuilder()
                    .setBaseUri("http://127.0.0.1")
                    .setPort(server.getAddress().getPort())
                    .setBasePath("/merge")
                    .addQueryParam("q", "merged")
                    .addHeader("X-Merged", "yes")
                    .addCookie("client", "api")
                    .build();
            RequestSpecification mergedRequestSpecification = new RequestSpecBuilder()
                    .addRequestSpecification(requestSpecification)
                    .build();

            ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                    .expectStatusCode(HttpURLConnection.HTTP_OK)
                    .expectContentType(ContentType.JSON)
                    .expectHeader("X-Response", equalTo("ok"))
                    .expectBody("status", equalTo("merged"))
                    .build();
            ResponseSpecification mergedResponseSpecification = new ResponseSpecBuilder()
                    .addResponseSpecification(responseSpecification)
                    .build();

            given()
                    .spec(mergedRequestSpecification)
                    .when()
                    .get()
                    .then()
                    .spec(mergedResponseSpecification);
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendMergedSpecificationResponse(HttpExchange exchange) throws IOException {
        try {
            boolean requestMatched = requestHasMergedSpecificationValues(exchange);
            int statusCode = requestMatched ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST;
            String responseBody = requestMatched
                    ? "{\"status\":\"merged\"}"
                    : "{\"status\":\"unexpected request\"}";

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            headers.add("X-Response", "ok");
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private boolean requestHasMergedSpecificationValues(HttpExchange exchange) {
        Headers requestHeaders = exchange.getRequestHeaders();
        List<String> cookieHeaders = requestHeaders.getOrDefault("Cookie", List.of());
        return "q=merged".equals(exchange.getRequestURI().getRawQuery())
                && "yes".equals(requestHeaders.getFirst("X-Merged"))
                && cookieHeaders.stream().anyMatch(cookie -> cookie.contains("client=api"));
    }
}
