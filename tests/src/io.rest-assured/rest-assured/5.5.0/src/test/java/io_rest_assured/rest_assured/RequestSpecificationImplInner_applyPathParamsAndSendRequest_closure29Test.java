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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.authentication.FormAuthConfig;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.spi.AuthFilter;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure29Test {
    @Test
    void formAuthenticationReplacesExistingAuthFilterBeforeSendingRequest() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Map<String, AtomicBoolean> requestState = new ConcurrentHashMap<>();
        requestState.put("login", new AtomicBoolean());
        requestState.put("resource", new AtomicBoolean());
        server.createContext("/login", exchange -> handleLogin(exchange, requestState.get("login")));
        server.createContext("/resource", exchange -> handleResource(exchange, requestState.get("resource")));
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        AtomicBoolean replacedAuthFilterWasInvoked = new AtomicBoolean();
        AuthFilter replacedAuthFilter = new RecordingAuthFilter(replacedAuthFilterWasInvoked);

        try {
            Response response = given()
                    .filter(replacedAuthFilter)
                    .auth().form("alice", "secret", new FormAuthConfig("/login", "username", "password"))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/resource");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("authorized");
            assertThat(requestState.get("login")).isTrue();
            assertThat(requestState.get("resource")).isTrue();
            assertThat(replacedAuthFilterWasInvoked).isFalse();
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }


    private static void handleLogin(HttpExchange exchange, AtomicBoolean loginWasCalled) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean validCredentials = "POST".equals(exchange.getRequestMethod())
                    && body.contains("username=alice")
                    && body.contains("password=secret");
            loginWasCalled.set(validCredentials);

            byte[] responseBytes = (validCredentials ? "logged-in" : "forbidden").getBytes(StandardCharsets.UTF_8);
            if (validCredentials) {
                exchange.getResponseHeaders().set("Set-Cookie", "SESSION=logged-in; Path=/");
            }
            exchange.sendResponseHeaders(
                    validCredentials ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_FORBIDDEN,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static void handleResource(HttpExchange exchange, AtomicBoolean resourceWasCalled) throws IOException {
        try {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            boolean authorized = cookieHeader != null && cookieHeader.contains("SESSION=logged-in");
            resourceWasCalled.set(authorized);

            byte[] responseBytes = (authorized ? "authorized" : "unauthorized").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(
                    authorized ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_UNAUTHORIZED,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class RecordingAuthFilter implements AuthFilter {
        private final AtomicBoolean invoked;

        private RecordingAuthFilter(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            invoked.set(true);
            return ctx.next(requestSpec, responseSpec);
        }
    }
}
