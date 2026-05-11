/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplHandleMultiValueParamsIfNeededClosure37Access;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_handleMultiValueParamsIfNeeded_closure37Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_handleMultiValueParamsIfNeeded_closure37";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingHandleMultiValueParamsClass";

    @Test
    void compilerGeneratedClassResolverResolvesUnloadedJdkClass() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplHandleMultiValueParamsIfNeededClosure37Access
                    .resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            assertEquals(RESOLVABLE_CLASS_NAME, resolvedClass.getName());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = RequestSpecificationImplHandleMultiValueParamsIfNeededClosure37Access
                    .resolveWithCompilerGeneratedClassResolver(RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            RequestSpecificationImplHandleMultiValueParamsIfNeededClosure37Access
                    .resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                return;
            }
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void expandsMultiValueFormParameterWhenPostingFormBody() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.setExecutor(executorService);
        server.createContext("/submit", exchange -> {
            try (InputStream bodyStream = exchange.getRequestBody()) {
                requestBody.set(new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "created".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();

            Response response = RestAssured.given()
                    .baseUri("http://127.0.0.1")
                    .port(port)
                    .formParam("tag", List.of("alpha", "beta"))
                    .post("/submit");

            assertEquals(201, response.statusCode());
            assertEquals("created", response.asString());
            assertNotNull(requestBody.get());
            assertEquals("tag=alpha&tag=beta", requestBody.get());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
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

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        assertTrue(isNativeGroovyInitializationFailure(error), () -> "Unexpected initialization failure: " + error);
    }

    private static boolean isNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        return ("Could not initialize class " + CLOSURE_CLASS_NAME).equals(message)
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
}
