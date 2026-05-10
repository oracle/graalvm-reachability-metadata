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
import io.restassured.authentication.PreemptiveOAuth2HeaderScheme;
import io.restassured.config.DecoderConfig;
import io.restassured.config.EncoderConfig;
import io.restassured.config.OAuthConfig;
import io.restassured.internal.http.HTTPBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PreemptiveOAuth2HeaderSchemeTest {
    private static final String ACCESS_TOKEN = "access-token";
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.mapper.ObjectMapperDeserializationContext";

    /**
     * Minimal same-package helper whose class initializer directly invokes
     * `PreemptiveOAuth2HeaderScheme.class$(String)`.
     */
    private static final byte[] DIRECT_INVOKER_CLASS = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAEwEAR2lvL3Jlc3Rhc3N1cmVkL2F1dGhlbnRpY2F0aW9uL1ByZWVtcHRpdmVPQXV0
            aDJIZWFkZXJTY2hlbWVEaXJlY3RJbnZva2VyBwABAQAQamF2YS9sYW5nL09iamVjdAcAAwEABjxp
            bml0PgEAAygpVgwABQAGCgAEAAcBAARDb2RlAQAIPGNsaW5pdD4BADppby9yZXN0YXNzdXJlZC9h
            dXRoZW50aWNhdGlvbi9QcmVlbXB0aXZlT0F1dGgySGVhZGVyU2NoZW1lBwALAQAGY2xhc3MkAQAl
            KExqYXZhL2xhbmcvU3RyaW5nOylMamF2YS9sYW5nL0NsYXNzOwwADQAOCgAMAA8BADhpby5yZXN0
            YXNzdXJlZC5tYXBwZXIuT2JqZWN0TWFwcGVyRGVzZXJpYWxpemF0aW9uQ29udGV4dAgAEQAhAAIA
            BAAAAAAAAgABAAUABgABAAkAAAARAAEAAQAAAAUqtwAIsQAAAAAACAAKAAYAAQAJAAAAEwABAAAA
            AAAHEhK4ABBXsQAAAAAAAA==
            """);

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PreemptiveOAuth2HeaderScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                PreemptiveOAuth2HeaderScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    PreemptiveOAuth2HeaderScheme.class,
                    "class$",
                    new Object[] {DYNAMIC_RESOLUTION_TARGET});

            assertEquals(DYNAMIC_RESOLUTION_TARGET, ((Class<?>) resolvedClass).getName());
        } catch (NoClassDefFoundError error) {
            assertGroovyInitializationFailure(error);
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PreemptiveOAuth2HeaderScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                PreemptiveOAuth2HeaderScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> {
                    Class<?> ignored = (Class<?>) classResolver.invokeExact("example.DoesNotExist");
                });

        assertEquals("example.DoesNotExist", error.getMessage());
    }

    @Test
    void directInvokerReachesCompilerGeneratedClassResolver() throws IllegalAccessException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    PreemptiveOAuth2HeaderScheme.class,
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
    void factoryCreatesConfiguredPreemptiveOAuth2HeaderScheme() {
        AuthenticationScheme authenticationScheme = RestAssured.oauth2(ACCESS_TOKEN);

        PreemptiveOAuth2HeaderScheme oauth2Scheme = assertInstanceOf(
                PreemptiveOAuth2HeaderScheme.class,
                authenticationScheme);
        assertEquals(ACCESS_TOKEN, oauth2Scheme.getAccessToken());
        assertEquals("Bearer " + ACCESS_TOKEN, oauth2Scheme.generateAuthToken());
    }

    @Test
    void authenticateWritesBearerAuthorizationHeaderToHttpBuilder() {
        PreemptiveOAuth2HeaderScheme scheme = new PreemptiveOAuth2HeaderScheme();
        scheme.setAccessToken(ACCESS_TOKEN);
        try (HeaderOnlyHTTPBuilder httpBuilder = new HeaderOnlyHTTPBuilder()) {
            scheme.authenticate(httpBuilder);

            assertEquals("Bearer " + ACCESS_TOKEN, httpBuilder.getHeaders().get("Authorization"));
        }
    }

    @Test
    void sendsBearerAuthorizationHeaderWithoutServerChallenge() throws Exception {
        String expectedAuthorization = "Bearer " + ACCESS_TOKEN;
        AtomicReference<String> observedAuthorization = new AtomicReference<>();

        try (LocalPreemptiveOAuth2Server server = new LocalPreemptiveOAuth2Server(observedAuthorization)) {
            RestAssured.given()
                    .auth()
                    .preemptive()
                    .oauth2(ACCESS_TOKEN)
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

    private static final class HeaderOnlyHTTPBuilder extends HTTPBuilder implements AutoCloseable {
        private HeaderOnlyHTTPBuilder() {
            super(
                    true,
                    EncoderConfig.encoderConfig(),
                    DecoderConfig.decoderConfig(),
                    OAuthConfig.oauthConfig(),
                    new DefaultHttpClient());
        }

        @Override
        protected Object doRequest(RequestConfigDelegate delegate) {
            return null;
        }

        @Override
        public void close() {
            getClient().getConnectionManager().shutdown();
        }
    }

    private static final class LocalPreemptiveOAuth2Server implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalPreemptiveOAuth2Server(AtomicReference<String> observedAuthorization) throws IOException {
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
