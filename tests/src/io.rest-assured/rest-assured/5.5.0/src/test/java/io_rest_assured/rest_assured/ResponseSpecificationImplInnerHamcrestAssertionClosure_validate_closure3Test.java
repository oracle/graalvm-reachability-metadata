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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResponseSpecificationImplInnerHamcrestAssertionClosure_validate_closure3Test {
    @Test
    void reportsEachCollectedValidationErrorMessage() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/validation-errors", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                    .expectStatusCode(HttpURLConnection.HTTP_CREATED)
                    .expectContentType(ContentType.XML)
                    .expectHeader("X-Validation", equalTo("rejected"))
                    .build();

            AssertionError error = assertThrows(AssertionError.class, () -> given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/validation-errors")
                    .then()
                    .spec(responseSpecification));

            assertTrue(error.getMessage().contains("3 expectations failed"));
            assertTrue(error.getMessage().contains("status code"));
            assertTrue(error.getMessage().contains("content-type"));
            assertTrue(error.getMessage().contains("X-Validation"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            headers.add("X-Validation", "accepted");
            byte[] body = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
