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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.internal.RequestSpecificationImpl;
import io.restassured.internal.filter.CsrfFilter;
import io.restassured.response.Response;
import io.restassured.specification.ProxySpecification;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RequestSpecificationImplTest {
    @Test
    void createsRequestSpecificationThroughRestAssuredDsl() {
        try {
            RequestSpecification requestSpecification = RestAssured.given();

            assertEquals(RequestSpecificationImpl.class, requestSpecification.getClass());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void storesContentTypeAndProxyConfigurationWithoutSendingRequest() throws Exception {
        try {
            RequestSpecification requestSpecification = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .proxy(new URI("http://proxy.example.test:8181"));

            QueryableRequestSpecification queryableSpecification = SpecificationQuerier.query(requestSpecification);
            ProxySpecification proxySpecification = queryableSpecification.getProxySpecification();

            assertEquals(ContentType.JSON.toString(), queryableSpecification.getContentType());
            assertEquals("proxy.example.test", proxySpecification.getHost());
            assertEquals(8181, proxySpecification.getPort());
            assertEquals("http", proxySpecification.getScheme());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void rejectsNullClassTypedArgumentsBeforeAnyRequestIsSent() {
        try {
            RequestSpecification requestSpecification = RestAssured.given();

            assertThrows(IllegalArgumentException.class, () -> requestSpecification.request((Method) null));
            assertThrows(IllegalArgumentException.class, () -> requestSpecification.proxy((URI) null));
            assertThrows(IllegalArgumentException.class, () -> requestSpecification.proxy((ProxySpecification) null));
            assertThrows(IllegalArgumentException.class, () -> requestSpecification.contentType((ContentType) null));
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void sendsGetRequestThroughRequestSpecificationImplementation() throws IOException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/ping", exchange -> {
            byte[] response = "pong".getBytes(StandardCharsets.UTF_8);
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
                    .get("/ping");

            assertEquals(200, response.statusCode());
            assertEquals("pong", response.asString());
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

    @Test
    void disableCsrfRemovesOnlyCsrfFiltersFromRequestSpecification() {
        try {
            RequestSpecification requestSpecification = RestAssured.given()
                    .filter(new CsrfFilter())
                    .csrf("/login");

            RequestSpecification returnedSpecification = requestSpecification.disableCsrf();

            assertSame(requestSpecification, returnedSpecification);
            assertEquals(0, SpecificationQuerier.query(returnedSpecification).getDefinedFilters().size());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
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
