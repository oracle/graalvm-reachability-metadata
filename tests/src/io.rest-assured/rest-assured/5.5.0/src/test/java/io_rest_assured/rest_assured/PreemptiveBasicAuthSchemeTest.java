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
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.graalvm.internal.tck.NativeImageSupport;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreemptiveBasicAuthSchemeTest {
    private static final String USERNAME = "Aladdin";
    private static final String PASSWORD = "open sesame";
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.mapper.ObjectMapperDeserializationContext";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `PreemptiveBasicAuthScheme.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAEwEARGlvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL1ByZWVtcHRpdmVCYXNp
            Y0F1dGhTY2hlbWVEaXJlY3RJbnZva2VyBwABAQAQamF2YS9sYW5nL09iamVjdAcAAwEABjxpbml0
            PgEAAygpVgwABQAGCgAEAAcBAARDb2RlAQAIPGNsaW5pdD4BADdpby9yZXN0YXNzdXJlZC9hdXRo
            ZW50aWNhdGlvbi9QcmVlbXB0aXZlQmFzaWNBdXRoU2NoZW1lBwALAQAGY2xhc3MkAQAlKExqYXZh
            L2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL0NsYXNzOwwADQAOCgAMAA8BADhpby5yZXN0YXNzdXJl
            ZC5tYXBwZXIuT2JqZWN0TWFwcGVyRGVzZXJpYWxpemF0aW9uQ29udGV4dAgAEQAxAAIABAAAAAAA
            AgABAAUABgABAAkAAAARAAEAAQAAAAUqtwAIsQAAAAAACAAKAAYAAQAJAAAAEwABAAAAAAAHEhK4
            ABBXsQAAAAAAAA==
            """);

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PreemptiveBasicAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                PreemptiveBasicAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    PreemptiveBasicAuthScheme.class,
                    "class$",
                    new Object[] {DYNAMIC_RESOLUTION_TARGET});

            assertEquals(DYNAMIC_RESOLUTION_TARGET, ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    PreemptiveBasicAuthScheme.class,
                    MethodHandles.lookup());
            Class<?> directInvokerClass = lookup.defineClass(DIRECT_INVOKER_CLASS);

            lookup.ensureInitialized(directInvokerClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void factoryCreatesConfiguredPreemptiveBasicAuthenticationScheme() {
        AuthenticationScheme authenticationScheme = RestAssured.preemptive().basic(USERNAME, PASSWORD);

        PreemptiveBasicAuthScheme basicAuthScheme = assertInstanceOf(
                PreemptiveBasicAuthScheme.class,
                authenticationScheme);
        assertEquals(USERNAME, basicAuthScheme.getUserName());
        assertEquals(PASSWORD, basicAuthScheme.getPassword());
    }

    @Test
    void sendsBasicAuthorizationHeaderWithoutServerChallenge() throws Exception {
        String expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.ISO_8859_1));
        AtomicReference<String> observedAuthorization = new AtomicReference<>();

        try (LocalPreemptiveAuthServer server = new LocalPreemptiveAuthServer(observedAuthorization)) {
            RestAssured.given()
                    .auth()
                    .preemptive()
                    .basic(USERNAME, PASSWORD)
                    .when()
                    .get(server.url())
                    .then()
                    .statusCode(200)
                    .body(equalTo("authorized"));
        }

        assertEquals(expectedAuthorization, observedAuthorization.get());
    }

    private static void assertGroovyInitializationFailure(NoClassDefFoundError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
    }

    private static final class LocalPreemptiveAuthServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalPreemptiveAuthServer(AtomicReference<String> observedAuthorization) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext("/secure", exchange -> handleRequest(exchange, observedAuthorization));
            this.server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/secure";
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        private static void handleRequest(
                HttpExchange exchange,
                AtomicReference<String> observedAuthorization) throws IOException {
            observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            sendResponse(exchange, 200, "authorized");
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
