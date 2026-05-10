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
import io.restassured.authentication.FormAuthConfig;
import io.restassured.authentication.FormAuthScheme;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FormAuthSchemeTest {
    private static final String USERNAME = "Aladdin";
    private static final String PASSWORD = "open sesame";
    private static final String USER_INPUT = "j_username";
    private static final String PASSWORD_INPUT = "j_password";

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                FormAuthScheme.class,
                MethodHandles.lookup());
        MethodHandle classResolver = lookup.findStatic(
                FormAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classResolver.invokeExact(
                "io.restassured.authentication.FormAuthScheme");

        assertSame(FormAuthScheme.class, resolvedClass);
    }

    @Test
    void resolvesCompilerGeneratedClassResolverThroughGroovyStaticDispatch() {
        try {
            Object resolvedClass = InvokerHelper.invokeStaticMethod(
                    FormAuthScheme.class,
                    "class$",
                    new Object[] {"io.restassured.authentication.FormAuthScheme"});

            assertSame(FormAuthScheme.class, resolvedClass);
        } catch (NoClassDefFoundError error) {
            String message = error.getMessage();
            assertTrue(
                    "Could not initialize class groovy.lang.GroovySystem".equals(message)
                            || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message));
        }
    }

    @Test
    void reportsUnknownCompilerGeneratedClassAsNoClassDefFoundError() throws Throwable {
        NoClassDefFoundError error = assertThrows(
                NoClassDefFoundError.class,
                () -> classResolver().invokeWithArguments(
                        "io.restassured.authentication.FormAuthSchemeMissingClass"));

        assertEquals("io.restassured.authentication.FormAuthSchemeMissingClass", error.getMessage());
    }

    @Test
    void factoryCreatesConfiguredFormAuthenticationScheme() {
        FormAuthConfig config = new FormAuthConfig("/login", USER_INPUT, PASSWORD_INPUT);

        AuthenticationScheme authenticationScheme = RestAssured.form(USERNAME, PASSWORD, config);

        FormAuthScheme formAuthScheme = assertInstanceOf(FormAuthScheme.class, authenticationScheme);
        assertEquals(USERNAME, formAuthScheme.getUserName());
        assertEquals(PASSWORD, formAuthScheme.getPassword());
        assertSame(config, formAuthScheme.getConfig());
    }

    @Test
    void postsConfiguredFormCredentialsAndUsesReturnedSessionCookie() throws Exception {
        AtomicReference<Map<String, String>> observedLoginForm = new AtomicReference<>();

        try (LocalFormAuthServer server = new LocalFormAuthServer(observedLoginForm)) {
            RestAssured.given()
                    .auth()
                    .form(USERNAME, PASSWORD, new FormAuthConfig("/login", USER_INPUT, PASSWORD_INPUT))
                    .when()
                    .get(server.secureUrl())
                    .then()
                    .statusCode(200)
                    .body(equalTo("welcome"));
        }

        assertEquals(USERNAME, observedLoginForm.get().get(USER_INPUT));
        assertEquals(PASSWORD, observedLoginForm.get().get(PASSWORD_INPUT));
    }

    private static MethodHandle classResolver() throws IllegalAccessException, NoSuchMethodException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                FormAuthScheme.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                FormAuthScheme.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static final class LocalFormAuthServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<Map<String, String>> observedLoginForm;

        private LocalFormAuthServer(AtomicReference<Map<String, String>> observedLoginForm) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.observedLoginForm = observedLoginForm;
            this.server.setExecutor(executor);
            this.server.createContext("/login", this::handleLoginRequest);
            this.server.createContext("/secure", this::handleSecureRequest);
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

        private void handleLoginRequest(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "method not allowed");
                return;
            }

            Map<String, String> form = decodeForm(exchange);
            observedLoginForm.set(form);
            if (USERNAME.equals(form.get(USER_INPUT)) && PASSWORD.equals(form.get(PASSWORD_INPUT))) {
                exchange.getResponseHeaders().add("Set-Cookie", "auth=ok; Path=/");
                sendResponse(exchange, 200, "logged in");
                return;
            }

            sendResponse(exchange, 403, "forbidden");
        }

        private void handleSecureRequest(HttpExchange exchange) throws IOException {
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            if (cookie != null && cookie.contains("auth=ok")) {
                sendResponse(exchange, 200, "welcome");
                return;
            }

            sendResponse(exchange, 401, "login required");
        }

        private static Map<String, String> decodeForm(HttpExchange exchange) throws IOException {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = new HashMap<>();
            if (requestBody.isEmpty()) {
                return form;
            }
            for (String pair : requestBody.split("&")) {
                String[] nameAndValue = pair.split("=", 2);
                String name = decode(nameAndValue[0]);
                String value = nameAndValue.length == 2 ? decode(nameAndValue[1]) : "";
                form.put(name, value);
            }
            return form;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
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
