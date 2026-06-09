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
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.Matchers.equalTo;

public class ResponseSpecificationImplInner_body_closure11_closure21_closure22Test {
    @Test
    void validatesMergedAdditionalBodyExpectationsWithParameterizedAndPlainMatchers() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/catalog")
                    .then()
                    .body(
                            "items[%d].name", withArgs(0), equalTo("book"),
                            "items[%d].name", withArgs(1), equalTo("pen"),
                            "items[%d].name", withArgs(2), equalTo("bag"))
                    .body(
                            "summary.count", equalTo(3),
                            "summary.count", equalTo(3),
                            "summary.available", equalTo(true));
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
            byte[] body = """
                    {"items":[{"name":"book"},{"name":"pen"},{"name":"bag"}],"summary":{"count":3,"available":true}}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
