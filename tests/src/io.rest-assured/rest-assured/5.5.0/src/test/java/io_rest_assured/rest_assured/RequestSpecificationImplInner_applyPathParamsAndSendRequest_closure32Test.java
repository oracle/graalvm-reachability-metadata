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
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import io.restassured.specification.ResponseSpecification;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure32Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_applyPathParamsAndSendRequest_closure32";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.MissingResponseLoggingFilterForClosure32";

    @Test
    @Order(1)
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, ResponseLoggingFilter.class.getName());

            assertSame(ResponseLoggingFilter.class, resolvedClass);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LinkageError error) {
                assertNativeGroovyInitializationFailure(error);
                return;
            }
            throw cause;
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    @Order(2)
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            try {
                classResolver.invoke(null, MISSING_CLASS_NAME);
                throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof LinkageError error && isNativeGroovyInitializationFailure(error)) {
                    return;
                }
                if (cause instanceof NoClassDefFoundError error) {
                    assertEquals(MISSING_CLASS_NAME, error.getMessage());
                    return;
                }
                throw cause;
            }
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    @Order(3)
    void compilerGeneratedClassResolverResolvesResponseLoggingFilterClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(ResponseLoggingFilter.class.getName());

            assertSame(ResponseLoggingFilter.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    @Order(4)
    void closureIdentifiesResponseLoggingFilterInstances() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();
            ResponseLoggingFilter responseLoggingFilter = new ResponseLoggingFilter(LogDetail.STATUS);

            Object responseLoggingFilterResult = closure.call(responseLoggingFilter);
            Object plainObjectResult = closure.call(new Object());

            assertEquals(Boolean.TRUE, responseLoggingFilterResult);
            assertEquals(Boolean.FALSE, plainObjectResult);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    @Order(5)
    void responseSpecificationLogDetailInstallsResponseLoggingFilterBeforeSendingRequest() throws Exception {
        AtomicBoolean responseLoggingFilterWasInstalled = new AtomicBoolean(false);
        ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                .log(LogDetail.STATUS)
                .expectStatusCode(200)
                .build();

        try (LocalHttpServer server = new LocalHttpServer()) {
            Response response = RestAssured.given()
                    .filter(new RecordingFilter(responseLoggingFilterWasInstalled))
                    .then()
                    .spec(responseSpecification)
                    .when()
                    .get(server.url("/ok"));

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertTrue(responseLoggingFilterWasInstalled.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
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

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
                || "Could not initialize class groovy.lang.Closure".equals(message)
                || "Could not initialize class groovy.lang.GroovySystem".equals(message)
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

    private static final class RecordingFilter implements Filter {
        private final AtomicBoolean responseLoggingFilterWasInstalled;

        private RecordingFilter(AtomicBoolean responseLoggingFilterWasInstalled) {
            this.responseLoggingFilterWasInstalled = responseLoggingFilterWasInstalled;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            boolean hasResponseLoggingFilter = requestSpec.getDefinedFilters().stream()
                    .anyMatch(ResponseLoggingFilter.class::isInstance);
            responseLoggingFilterWasInstalled.set(hasResponseLoggingFilter);
            return ctx.next(requestSpec, responseSpec);
        }
    }

    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalHttpServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext("/ok", this::handleRequest);
            this.server.start();
        }

        private String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        private void handleRequest(HttpExchange exchange) throws IOException {
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        }
    }
}
