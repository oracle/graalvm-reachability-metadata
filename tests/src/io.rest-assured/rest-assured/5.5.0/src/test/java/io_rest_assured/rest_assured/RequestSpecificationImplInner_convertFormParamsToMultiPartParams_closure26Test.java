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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.response.Response;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$_convertFormParamsToMultiPartParams_closure26";
    private static final String RESOLVABLE_CLASS_NAME = "java.util.concurrent.Phaser";
    private static final String MISSING_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImplMissingConvertFormParamsClass";

    @Test
    void compilerGeneratedClassResolverResolvesUnloadedJdkClass() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(RESOLVABLE_CLASS_NAME);

            assertEquals(RESOLVABLE_CLASS_NAME, resolvedClass.getName());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                    RequestSpecificationImpl.class.getName());

            assertSame(RequestSpecificationImpl.class, resolvedClass);
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClass() throws Throwable {
        try {
            resolveWithCompilerGeneratedClassResolver(MISSING_CLASS_NAME);
            throw new AssertionError("Missing classes should be reported as NoClassDefFoundError");
        } catch (NoClassDefFoundError error) {
            assertEquals(MISSING_CLASS_NAME, error.getMessage());
        } catch (Error error) {
            rethrowUnlessUnsupportedNativeImageError(error);
        }
    }

    @Test
    void convertsFormParametersIntoMultipartPartsWhenMultipartRequestIsSent() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestContentType = new AtomicReference<>();
        server.setExecutor(executorService);
        server.createContext("/upload", exchange -> {
            requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try (InputStream bodyStream = exchange.getRequestBody()) {
                requestBody.set(new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "accepted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
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
                    .contentType(ContentType.MULTIPART)
                    .formParam("description", "converted-form-value")
                    .multiPart("attachment", "payload.txt", "multipart-file-value", "text/plain")
                    .post("/upload");

            assertEquals(200, response.statusCode());
            assertEquals("accepted", response.asString());
            assertTrue(requestContentType.get().startsWith("multipart/form-data"));
            assertTrue(requestBody.get().contains("name=\"description\""));
            assertTrue(requestBody.get().contains("converted-form-value"));
            assertTrue(requestBody.get().contains("name=\"attachment\""));
            assertTrue(requestBody.get().contains("multipart-file-value"));
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

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        Class<?> resolvedClass = (Class<?>) classResolver().invokeExact(className);
        return assertInstanceOf(Class.class, resolvedClass);
    }

    private static MethodHandle classResolver()
            throws IllegalAccessException, NoSuchMethodException, ClassNotFoundException {
        Class<?> closureClass = RequestSpecificationImpl.class.getClassLoader().loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        return closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
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
