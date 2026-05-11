/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import io.restassured.RestAssured;
import io.restassured.authentication.FormAuthConfig;
import io.restassured.filter.FilterContext;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.spi.AuthFilter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure29Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_applyPathParamsAndSendRequest_closure29";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingApplyPathParamsClass";
    private static final String USERNAME = "Aladdin";
    private static final String PASSWORD = "open sesame";
    private static final String USER_INPUT = "j_username";
    private static final String PASSWORD_INPUT = "j_password";

    @Test
    @Order(1)
    void compilerGeneratedClassResolverResolvesAuthFilterClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(AuthFilter.class.getName());

            assertSame(AuthFilter.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    @Order(2)
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, AuthFilter.class.getName());

            assertSame(AuthFilter.class, resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    @Order(3)
    void closureIdentifiesAuthFilterImplementations() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            Object authFilterResult = closure.call(new RecordingAuthFilter(new AtomicBoolean()));
            Object plainObjectResult = closure.call(new Object());

            assertEquals(Boolean.TRUE, authFilterResult);
            assertEquals(Boolean.FALSE, plainObjectResult);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedNativeImageError(exception.getCause());
        } catch (LinkageError error) {
            if (!isNativeGroovyInitializationFailure(error)) {
                rethrowUnlessUnsupportedNativeImageError(error);
            }
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    @Order(4)
    void formAuthenticationRemovesExistingAuthFiltersBeforeSendingRequest() throws Exception {
        AtomicBoolean obsoleteAuthFilterInvoked = new AtomicBoolean(false);

        try (LocalFormAuthServer server = new LocalFormAuthServer()) {
            RestAssured.given()
                    .filter(new RecordingAuthFilter(obsoleteAuthFilterInvoked))
                    .auth()
                    .form(USERNAME, PASSWORD, new FormAuthConfig(server.loginPath(), USER_INPUT, PASSWORD_INPUT))
                    .when()
                    .get(server.secureUrl())
                    .then()
                    .statusCode(200)
                    .body(equalTo("welcome"));
        } catch (LinkageError error) {
            assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
        }

        assertFalse(obsoleteAuthFilterInvoked.get());
    }

    @Test
    @Order(5)
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            InvocationTargetException exception = assertThrows(
                    InvocationTargetException.class,
                    () -> classResolver.invoke(null, MISSING_CLASS_NAME));

            Throwable cause = exception.getCause();
            assertTrue(cause instanceof NoClassDefFoundError);
            assertEquals(MISSING_CLASS_NAME, cause.getMessage());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return (Class<?>) classResolver().invokeExact(className);
    }

    private static Closure<?> newClosureInstance() throws Throwable {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle constructor = closureLookup.findConstructor(
                closureClass,
                MethodType.methodType(void.class, Object.class, Object.class));
        return (Closure<?>) constructor.invoke(new Object(), new Object());
    }

    private static MethodHandle classResolver()
            throws ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        Class<?> closureClass = closureClass();
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> closureClass() throws ClassNotFoundException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RequestSpecificationImpl.class,
                MethodHandles.lookup());
        return lookup.findClass(CLOSURE_CLASS_NAME);
    }

    private static void rethrowUnlessUnsupportedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        throw throwable;
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return "Could not initialize class groovy.lang.GroovySystem".equals(message)
                || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                || "Could not initialize class io.restassured.RestAssured".equals(message)
                || isGroovySystemInitializerError(error);
    }

    private static boolean isGroovySystemInitializerError(LinkageError error) {
        if (!(error instanceof ExceptionInInitializerError initializerError)) {
            return false;
        }
        Throwable cause = initializerError.getException();
        return cause instanceof NullPointerException
                && cause.getStackTrace().length > 0
                && "groovy.lang.GroovySystem".equals(cause.getStackTrace()[0].getClassName());
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

    private static final class LocalFormAuthServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalFormAuthServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext(loginPath(), this::handleLoginRequest);
            this.server.createContext("/secure", this::handleSecureRequest);
            this.server.start();
        }

        private String loginPath() {
            return "/login";
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
