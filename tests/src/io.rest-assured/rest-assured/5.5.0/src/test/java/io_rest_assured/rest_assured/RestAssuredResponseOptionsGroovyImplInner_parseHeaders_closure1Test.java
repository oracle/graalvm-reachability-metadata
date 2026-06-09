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
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestAssuredResponseOptionsGroovyImplInner_parseHeaders_closure1Test {

    @Test
    void responseHeadersReturnsHeaderObjectsParsedFromHttpResponse() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/headers", RestAssuredResponseOptionsGroovyImplInner_parseHeaders_closure1Test::sendHeaderResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/headers");

            assertEquals("one", response.header("X-Test-Header"));
            assertEquals("two", response.headers().getValue("X-Second-Header"));
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendHeaderResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/plain; charset=utf-8");
            headers.add("X-Test-Header", "one");
            headers.add("X-Second-Header", "two");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

}
