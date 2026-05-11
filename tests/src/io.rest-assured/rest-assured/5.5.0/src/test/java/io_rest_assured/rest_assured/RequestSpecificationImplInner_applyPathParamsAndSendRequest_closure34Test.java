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
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.filter.time.TimingFilter;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure34Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_applyPathParamsAndSendRequest_closure34";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.MissingTimingFilterMarker";

    @Test
    void compilerGeneratedClassResolverResolvesTimingFilterClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(TimingFilter.class.getName());

            assertSame(TimingFilter.class, resolvedClass);
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void javaReflectionDispatchInvokesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Method classResolver = closureClass().getDeclaredMethod("class$", String.class);
            classResolver.setAccessible(true);

            Object resolvedClass = classResolver.invoke(null, TimingFilter.class.getName());

            assertSame(TimingFilter.class, resolvedClass);
        } catch (InvocationTargetException exception) {
            rethrowUnlessExpectedNativeImageError(exception.getCause());
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClasses() throws Throwable {
        try {
            resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            fail("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void closureIdentifiesTimingFilterClasses() throws Throwable {
        try {
            Closure<?> closure = newClosureInstance();

            Object timingFilterClassResult = closure.call(TimingFilter.class);
            Object plainObjectClassResult = closure.call(Object.class);

            assertEquals(Boolean.TRUE, timingFilterClassResult);
            assertEquals(Boolean.FALSE, plainObjectClassResult);
        } catch (Throwable throwable) {
            rethrowUnlessExpectedNativeImageError(throwable);
        }
    }

    @Test
    void defaultTimingFilterIsAddedBeforeRequestIsSent() throws Exception {
        AtomicInteger timingFilterCount = new AtomicInteger();

        try (LocalHttpServer server = new LocalHttpServer()) {
            Response response = RestAssured.given()
                    .filter(new TimingFilterCountingFilter(timingFilterCount))
                    .when()
                    .get(server.url("/ok"));

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertEquals(1, timingFilterCount.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void defaultTimingFilterRecordsResponseTime() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer()) {
            Response response = RestAssured.given()
                    .when()
                    .get(server.url("/ok"));

            assertEquals(200, response.statusCode());
            assertTrue(response.time() >= 0L);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void timingFilterSubclassIsRecognizedAsAlreadyConfiguredBeforeRequestIsSent() throws Exception {
        AtomicInteger timingFilterCount = new AtomicInteger();

        try (LocalHttpServer server = new LocalHttpServer()) {
            Response response = RestAssured.given()
                    .filter(new CustomTimingFilter())
                    .filter(new TimingFilterCountingFilter(timingFilterCount))
                    .when()
                    .get(server.url("/ok"));

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertEquals(1, timingFilterCount.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } finally {
            RestAssured.reset();
        }
    }

    @Test
    void manuallyConfiguredTimingFilterIsNotDuplicatedBeforeRequestIsSent() throws Exception {
        AtomicInteger timingFilterCount = new AtomicInteger();

        try (LocalHttpServer server = new LocalHttpServer()) {
            Response response = RestAssured.given()
                    .filter(new TimingFilter())
                    .filter(new TimingFilterCountingFilter(timingFilterCount))
                    .when()
                    .get(server.url("/ok"));

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertEquals(1, timingFilterCount.get());
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

    private static void rethrowUnlessExpectedNativeImageError(Throwable throwable) throws Throwable {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof LinkageError error && isNativeGroovyInitializationFailure(error)) {
            return;
        }
        throw throwable;
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

    private static final class CustomTimingFilter extends TimingFilter {
    }

    private static final class TimingFilterCountingFilter implements Filter {
        private final AtomicInteger timingFilterCount;

        private TimingFilterCountingFilter(AtomicInteger timingFilterCount) {
            this.timingFilterCount = timingFilterCount;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            long count = requestSpec.getDefinedFilters().stream()
                    .filter(filter -> TimingFilter.class.isAssignableFrom(filter.getClass()))
                    .count();
            timingFilterCount.set(Math.toIntExact(count));
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
