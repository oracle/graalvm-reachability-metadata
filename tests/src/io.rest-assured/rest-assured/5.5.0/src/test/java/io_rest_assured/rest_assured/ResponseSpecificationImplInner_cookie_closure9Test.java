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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class ResponseSpecificationImplInner_cookie_closure9Test {
    @Test
    void recordsCookieMatcherExpectation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookies", ResponseSpecificationImplInner_cookie_closure9Test::sendCookieResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/cookies")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .cookie("session", equalTo("abc"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendCookieResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "session=abc; Path=/cookies; HttpOnly");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
        } finally {
            exchange.close();
        }
    }
}
