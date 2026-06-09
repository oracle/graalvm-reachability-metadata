/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.Interceptor;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.ProxyMetaClass;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FormAuthFilterInner_filter_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.filter.FormAuthFilter"
            + "$_filter_closure1";
    private static final String USER_NAME = "relative-action-user";
    private static final String PASSWORD = "relative-action-password";
    private static final String SESSION_COOKIE_NAME = "FORMLOGIN";
    private static final String SESSION_COOKIE_VALUE = "relative-action-valid";
    private static final String SESSION_COOKIE = SESSION_COOKIE_NAME + "=" + SESSION_COOKIE_VALUE;

    @Test
    void formAuthenticationParsesRelativeFormActionBeforeSubmittingCredentials() throws Throwable {
        AtomicBoolean loginRequestSeen = new AtomicBoolean(false);
        AtomicBoolean authenticatedRequestSeen = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secured", exchange -> sendSecuredResponse(exchange, authenticatedRequestSeen));
        server.createContext("/login", exchange -> sendLoginResponse(exchange, loginRequestSeen));
        server.setExecutor(executor);
        server.start();
        AtomicReference<Closure<?>> generatedFilterClosure = new AtomicReference<>();
        Class<?> closureClass = FormAuthFilterInner_filter_closure1Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(closureClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(closureClass);
        proxyMetaClass.setInterceptor(new CapturingClosureCallInterceptor(generatedFilterClosure));
        registry.setMetaClass(closureClass, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .auth()
                    .form(USER_NAME, PASSWORD)
                    .when()
                    .get("/secured")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .body(equalTo("secured content"));

            assertThat(loginRequestSeen).isTrue();
            assertThat(authenticatedRequestSeen).isTrue();
            String dynamicallyLoadedClassName = "io.restassured.internal.support.FileReader";
            Closure<?> closure = generatedFilterClosure.get();
            Class<?> resolvedClass = invokeGeneratedClassLookup(closure, dynamicallyLoadedClassName);
            assertThat(resolvedClass.getName()).isEqualTo(dynamicallyLoadedClassName);
        } finally {
            registry.setMetaClass(closureClass, originalMetaClass);
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        assertThat(closure).isNotNull();
        assertThat(closure.getClass().getName()).isEqualTo(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closure.getClass(), MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closure.getClass(),
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void sendSecuredResponse(HttpExchange exchange, AtomicBoolean authenticatedRequestSeen)
            throws IOException {
        try {
            if (hasValidSession(exchange.getRequestHeaders())) {
                authenticatedRequestSeen.set(true);
                sendText(exchange, HttpURLConnection.HTTP_OK, "secured content");
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            sendText(exchange, HttpURLConnection.HTTP_OK, """
                    <html>
                      <body>
                        <form action="login" method="post">
                          <input type="text" name="username" />
                          <input type="password" name="password" />
                        </form>
                      </body>
                    </html>
                    """);
        } finally {
            exchange.close();
        }
    }

    private static void sendLoginResponse(HttpExchange exchange, AtomicBoolean loginRequestSeen) throws IOException {
        try {
            Map<String, String> formParams = parseFormParams(exchange);
            if ("POST".equals(exchange.getRequestMethod())
                    && USER_NAME.equals(formParams.get("username"))
                    && PASSWORD.equals(formParams.get("password"))) {
                loginRequestSeen.set(true);
                exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "; Path=/");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
                return;
            }

            sendText(exchange, HttpURLConnection.HTTP_UNAUTHORIZED, "login failed");
        } finally {
            exchange.close();
        }
    }

    private static boolean hasValidSession(Headers headers) {
        return headers.getOrDefault("Cookie", List.of()).stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .anyMatch(cookie -> SESSION_COOKIE_NAME.equals(cookie.getName())
                        && SESSION_COOKIE_VALUE.equals(cookie.getValue()));
    }

    private static Map<String, String> parseFormParams(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(body.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        parameter -> decode(parameter[0]),
                        parameter -> parameter.length == 2 ? decode(parameter[1]) : ""));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendText(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class CapturingClosureCallInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedFilterClosure;

        private CapturingClosureCallInterceptor(AtomicReference<Closure<?>> generatedFilterClosure) {
            this.generatedFilterClosure = generatedFilterClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (object instanceof Closure<?> closure && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedFilterClosure.set(closure);
            }
            return null;
        }

        @Override
        public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
            return result;
        }

        @Override
        public boolean doInvoke() {
            return true;
        }
    }
}
