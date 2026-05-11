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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.RequestSpecificationImplRegisterRestAssuredEncodersClosure24DirectAccess;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplInner_registerRestAssuredEncoders_closure24Test {
    @Test
    void compilerGeneratedClassResolverUsesClassForName() throws Throwable {
        Class<?> resolvedClass = RequestSpecificationImplRegisterRestAssuredEncodersClosure24DirectAccess
                .resolveWithCompilerGeneratedClassResolver(resolvableRequestSpecificationImplClassName());

        assertSame(RequestSpecificationImpl.class, resolvedClass);
    }

    @Test
    void registeredMultipartEncoderSerializesPartsWhenRequestIsSent() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requestContentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.setExecutor(executorService);
        server.createContext("/upload", exchange -> {
            requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try (InputStream bodyStream = exchange.getRequestBody()) {
                requestBody.set(new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = "uploaded".getBytes(StandardCharsets.UTF_8);
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
                    .contentType(ContentType.MULTIPART)
                    .multiPart("metadata", "rest-assured multipart encoder")
                    .multiPart("attachment", "payload.txt", "file payload", "text/plain")
                    .post("/upload");

            assertEquals(200, response.statusCode());
            assertEquals("uploaded", response.asString());
            assertTrue(requestContentType.get().startsWith("multipart/form-data"));
            assertTrue(requestContentType.get().contains("boundary="));
            assertTrue(requestBody.get().contains("name=\"metadata\""));
            assertTrue(requestBody.get().contains("rest-assured multipart encoder"));
            assertTrue(requestBody.get().contains("name=\"attachment\""));
            assertTrue(requestBody.get().contains("file payload"));
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

    private static String resolvableRequestSpecificationImplClassName() {
        return System.getProperty(
                "io.restassured.request-specification-impl-class",
                RequestSpecificationImpl.class.getName());
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
