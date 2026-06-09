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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

public class ResponseSpecificationImplInner_cookies_closure8Test {
    @Test
    void recordsMultipleCookieExpectationsFromMap() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookies", ResponseSpecificationImplInner_cookies_closure8Test::sendCookieResponse);
        server.setExecutor(executor);
        server.start();

        try {
            Map<String, Object> expectedCookies = new LinkedHashMap<>();
            expectedCookies.put("session", equalTo("abc"));
            expectedCookies.put("theme", startsWith("light"));

            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/cookies")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .cookies(expectedCookies);
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
            headers.add("Set-Cookie", "theme=light-blue; Path=/cookies");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
        } finally {
            exchange.close();
        }
    }
}
