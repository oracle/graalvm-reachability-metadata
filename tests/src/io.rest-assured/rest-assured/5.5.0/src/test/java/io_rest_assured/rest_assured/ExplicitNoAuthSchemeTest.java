/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.GroovyObject;
import io.restassured.RestAssured;
import io.restassured.authentication.ExplicitNoAuthScheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExplicitNoAuthSchemeTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ExplicitNoAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                ExplicitNoAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.authentication.PreemptiveOAuth2HeaderScheme");

        assertEquals("io.restassured.authentication.PreemptiveOAuth2HeaderScheme", resolvedClass.getName());
    }

    @Test
    void canInstantiateAsGroovyAuthenticationScheme() {
        ExplicitNoAuthScheme authenticationScheme = new ExplicitNoAuthScheme();

        assertDoesNotThrow(() -> authenticationScheme.authenticate(null));
        assertEquals(ExplicitNoAuthScheme.class, ((GroovyObject) authenticationScheme).getMetaClass().getTheClass());
    }

    @Test
    void explicitNoAuthenticationOverridesDefaultPreemptiveAuthentication() throws Exception {
        AtomicReference<String> observedAuthorization = new AtomicReference<>();
        RestAssured.authentication = RestAssured.preemptive().basic("Aladdin", "open sesame");

        try (LocalHeaderServer server = new LocalHeaderServer(observedAuthorization)) {
            RestAssured.given()
                    .auth()
                    .none()
                    .when()
                    .get(server.url())
                    .then()
                    .statusCode(200);
        } finally {
            RestAssured.reset();
        }

        assertNull(observedAuthorization.get());
    }

    private static final class LocalHeaderServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalHeaderServer(AtomicReference<String> observedAuthorization) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext("/", exchange -> handleRequest(exchange, observedAuthorization));
            this.server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        private static void handleRequest(HttpExchange exchange, AtomicReference<String> observedAuthorization)
                throws IOException {
            observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }
    }
}
