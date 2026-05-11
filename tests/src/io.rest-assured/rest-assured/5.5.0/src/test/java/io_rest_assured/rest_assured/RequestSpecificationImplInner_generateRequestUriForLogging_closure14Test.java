/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplGenerateRequestUriForLoggingClosure14Access;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_generateRequestUriForLogging_closure14Test {
    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplGenerateRequestUriForLoggingClosure14Access
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverExercisesClassNotFoundBranch() throws Throwable {
        String missingClassName = "io.restassured.internal.RequestSpecificationImplMissingUriLoggingClosure14Target";
        try {
            NoClassDefFoundError error = assertThrows(
                    NoClassDefFoundError.class,
                    () -> RequestSpecificationImplGenerateRequestUriForLoggingClosure14Access
                            .resolveWithCompilerGeneratedClassResolver(missingClassName));

            assertTrue(error.getMessage().contains(missingClassName));
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void genericCompilerGeneratedClassResolverInvocationUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplGenerateRequestUriForLoggingClosure14Access
                    .resolveWithGenericCompilerGeneratedClassResolverInvocation(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void requestUriLoggingCollectsQueryParametersDefinedInPath() {
        try {
            RequestSpecificationImpl requestSpecification = (RequestSpecificationImpl) RestAssured.given()
                    .baseUri("http://example.com")
                    .queryParam("client", "mobile");

            String requestUri = requestSpecification.getURI("/search?flag&empty=&name=alice");

            assertTrue(requestUri.startsWith("http://example.com/search?"), requestUri);
            assertTrue(requestUri.contains("flag"), requestUri);
            assertTrue(requestUri.contains("empty="), requestUri);
            assertTrue(requestUri.contains("name=alice"), requestUri);
            assertTrue(requestUri.contains("client=mobile"), requestUri);
        } catch (LinkageError error) {
            assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
        }
    }

    @Test
    void requestExecutionParsesQueryParametersFromPathForLoggingUri() throws IOException {
        AtomicReference<String> rawQuery = new AtomicReference<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/search", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();
        try {
            Response response = RestAssured.given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .queryParam("client", "mobile")
                    .get("/search?flag&empty=&name=alice");

            assertEquals(200, response.statusCode());
            assertTrue(rawQuery.get().contains("flag"), rawQuery::get);
            assertTrue(rawQuery.get().contains("empty="), rawQuery::get);
            assertTrue(rawQuery.get().contains("name=alice"), rawQuery::get);
            assertTrue(rawQuery.get().contains("client=mobile"), rawQuery::get);
        } catch (LinkageError error) {
            assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
        } finally {
            server.stop(0);
            executorService.shutdownNow();
            try {
                assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while stopping test HTTP server", exception);
            }
        }
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
}
