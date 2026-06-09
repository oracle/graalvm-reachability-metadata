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
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_sendRequest_closure16Test {

    @Test
    void postsBodyAndAppliesResponseAssertionClosure() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/echo",
                RequestSpecificationImplInner_sendRequest_closure16Test::handleEcho);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter(new AssertionClosureRequestFilter())
                    .contentType(ContentType.TEXT)
                    .body("send request closure body")
                    .when()
                    .post("/echo")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(Matchers.equalTo("received: send request closure body"))
                    .extract()
                    .response();

            assertThat(response.asString()).isEqualTo("received: send request closure body");
            assertThat(response.header("X-Assertion-Closure-Path")).isEqualTo("/echo");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void handleEcho(HttpExchange exchange) throws IOException {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean postWithBody = "POST".equals(exchange.getRequestMethod())
                    && "send request closure body".equals(requestBody);
            byte[] responseBytes = (postWithBody
                    ? "received: send request closure body"
                    : "unexpected request").getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("X-Assertion-Closure-Path", exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(
                    postWithBody ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class AssertionClosureRequestFilter implements Filter {
        @Override
        public Response filter(
                FilterableRequestSpecification requestSpecification,
                FilterableResponseSpecification responseSpecification,
                FilterContext context) {
            return context.next(requestSpecification, responseSpecification);
        }
    }
}
