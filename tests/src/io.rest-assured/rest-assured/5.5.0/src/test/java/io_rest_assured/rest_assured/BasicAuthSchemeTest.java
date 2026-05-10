/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.BasicAuthScheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BasicAuthSchemeTest {
    private static final String USERNAME = "Aladdin";
    private static final String PASSWORD = "open sesame";

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                BasicAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                BasicAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.authentication.BasicAuthScheme");

        assertSame(BasicAuthScheme.class, resolvedClass);
    }

    @Test
    void factoryCreatesConfiguredBasicAuthenticationScheme() {
        AuthenticationScheme authenticationScheme = RestAssured.basic(USERNAME, PASSWORD);

        BasicAuthScheme basicAuthScheme = assertInstanceOf(BasicAuthScheme.class, authenticationScheme);
        assertEquals(USERNAME, basicAuthScheme.getUserName());
        assertEquals(PASSWORD, basicAuthScheme.getPassword());
    }

    @Test
    void sendsBasicAuthorizationHeaderAfterServerChallenge() throws Exception {
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.ISO_8859_1));
        AtomicReference<String> observedAuthorization = new AtomicReference<>();

        try (LocalBasicAuthServer server = new LocalBasicAuthServer(expectedAuthorization, observedAuthorization)) {
            RestAssured.given()
                    .auth()
                    .basic(USERNAME, PASSWORD)
                    .when()
                    .get(server.secureUrl())
                    .then()
                    .statusCode(200)
                    .body(equalTo("authorized"));
        }

        assertEquals(expectedAuthorization, observedAuthorization.get());
    }

    private static final class LocalBasicAuthServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalBasicAuthServer(String expectedAuthorization, AtomicReference<String> observedAuthorization)
                throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext("/secure", exchange -> handleSecureRequest(
                    exchange,
                    expectedAuthorization,
                    observedAuthorization));
            this.server.start();
        }

        private String secureUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/secure";
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        private static void handleSecureRequest(
                HttpExchange exchange,
                String expectedAuthorization,
                AtomicReference<String> observedAuthorization) throws IOException {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (expectedAuthorization.equals(authorization)) {
                observedAuthorization.set(authorization);
                sendResponse(exchange, 200, "authorized");
                return;
            }

            exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"rest-assured-test\"");
            sendResponse(exchange, 401, "unauthorized");
        }

        private static void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }
    }
}
