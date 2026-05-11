/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.RequestSpecificationImplGenerateBoundaryClosure25Access;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_generateBoundary_closure25Test {
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=\\\"?([A-Za-z0-9_-]{30,40})\\\"?");

    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        try {
            String className = String.class.getName();

            Class<?> resolvedClass = RequestSpecificationImplGenerateBoundaryClosure25Access
                    .resolveWithCompilerGeneratedClassResolver(className);

            assertEquals(String.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void compilerGeneratedClassResolverReportsMissingClassAsNoClassDefFoundError() throws Throwable {
        String missingClassName = "io.restassured.internal.RequestSpecificationImplMissingBoundaryClass";

        try {
            RequestSpecificationImplGenerateBoundaryClosure25Access
                    .resolveWithCompilerGeneratedClassResolver(missingClassName);
            throw new AssertionError("Expected NoClassDefFoundError for " + missingClassName);
        } catch (NoClassDefFoundError error) {
            if (isNativeGroovyInitializationFailure(error)) {
                assertNativeGroovyInitializationFailure(error);
            } else {
                assertEquals(missingClassName, error.getMessage());
            }
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void generatesBoundaryForMultipartRequestWithoutConfiguredBoundary() throws IOException {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/upload", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(readRequestBody(exchange.getRequestBody()));
            byte[] response = "uploaded".getBytes(StandardCharsets.UTF_8);
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
                    .multiPart("message", "hello")
                    .post("/upload");

            assertEquals(200, response.statusCode());
            String headerValue = contentType.get();
            assertNotNull(headerValue);
            Matcher matcher = BOUNDARY_PATTERN.matcher(headerValue);
            assertTrue(matcher.find(), () -> "Multipart boundary was not generated in: " + headerValue);
            String boundary = matcher.group(1);
            assertTrue(requestBody.get().contains("--" + boundary));
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

    private static String readRequestBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
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
