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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class RestAssuredHttpBuilderGroovyHelperInner_createClosureThatCalls_closure2Test {
    @Test
    void postFailureResponseInvokesAssertionClosureWrapper() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fail", RestAssuredHttpBuilderGroovyHelperInner_createClosureThatCalls_closure2Test
                ::sendJsonFailure);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .post("/fail")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                    .body("message", equalTo("failure"));
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendJsonFailure(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "{\"message\":\"failure\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
