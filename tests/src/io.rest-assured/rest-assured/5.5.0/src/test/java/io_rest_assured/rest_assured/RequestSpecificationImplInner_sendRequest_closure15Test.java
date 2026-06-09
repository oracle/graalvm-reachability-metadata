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
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_sendRequest_closure15Test {
    @Test
    void sendsConfiguredCookiesWithRequest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/cookies",
                RequestSpecificationImplInner_sendRequest_closure15Test::handleCookies);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .cookie("session", "native")
                    .cookie("mode", "coverage")
                    .when()
                    .get("/cookies");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).contains("session=native", "mode=coverage");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void handleCookies(HttpExchange exchange) throws IOException {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            byte[] responseBytes = cookieHeader.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
