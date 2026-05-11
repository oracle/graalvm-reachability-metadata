/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.Method;
import io.restassured.internal.TestSpecificationImplDirectAccess;
import io.restassured.response.Response;
import io.restassured.specification.RequestSender;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSpecificationImplTest {
    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        try {
            Class<?> resolvedClass = TestSpecificationImplDirectAccess
                    .resolveWithCompilerGeneratedClassResolver(Method.class.getName());

            assertSame(Method.class, resolvedClass);
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    @Test
    void sendsMethodEnumRequestThroughCombinedRequestAndResponseSpecification() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer()) {
            RequestSpecification requestSpecification = new RequestSpecBuilder()
                    .setBaseUri(server.baseUri())
                    .build();
            ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                    .expectStatusCode(200)
                    .build();

            RequestSender sender = RestAssured.given(requestSpecification, responseSpecification);
            Response response = sender.request(Method.GET, "/status");

            assertEquals(200, response.statusCode());
            assertEquals("ok", response.asString());
            assertEquals("GET", server.lastMethod());
        } catch (LinkageError error) {
            assertNativeGroovyInitializationFailure(error);
        }
    }

    private static void assertNativeGroovyInitializationFailure(LinkageError error) {
        String message = error.getMessage();
        assertTrue(
                "Could not initialize class groovy.lang.GroovySystem".equals(message)
                        || "Could not initialize class org.codehaus.groovy.runtime.InvokerHelper".equals(message)
                        || isGroovySystemInitializerError(error),
                () -> "Unexpected initialization failure: " + error);
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

    private static final class LocalHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> lastMethod = new AtomicReference<>();

        private LocalHttpServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(executor);
            this.server.createContext("/status", this::handleStatus);
            this.server.start();
        }

        private String baseUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private String lastMethod() {
            return lastMethod.get();
        }

        private void handleStatus(HttpExchange exchange) throws IOException {
            lastMethod.set(exchange.getRequestMethod());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() throws InterruptedException {
            server.stop(0);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
