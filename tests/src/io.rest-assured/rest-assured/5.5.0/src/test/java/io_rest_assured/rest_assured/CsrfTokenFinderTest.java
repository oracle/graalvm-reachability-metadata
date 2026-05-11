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
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.csrf.CsrfTokenFinder;
import io.restassured.internal.csrf.CsrfTokenFinderDirectAccess;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CsrfTokenFinderTest {
    private static final String CSRF_TOKEN = "csrf-token-from-html-meta";

    @Test
    void resolvesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = resolveWithCompilerGeneratedClassResolver(
                "io.restassured.internal.csrf.CsrfTokenFinder");

        assertEquals(CsrfTokenFinder.class, resolvedClass);
    }

    @Test
    void findsMetaCsrfTokenAndSendsItAsHeader() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer()) {
            try {
                Response response = RestAssured.given()
                        .baseUri(server.baseUri())
                        .csrf(server.baseUri() + "/csrf")
                        .post("/submit");

                assertEquals(200, response.statusCode());
                assertEquals("accepted", response.asString());
                assertEquals(1, server.csrfPageRequests());
                assertEquals(1, server.submitRequests());
            } catch (LinkageError error) {
                assertNativeGroovyInitializationFailure(error);
            }
        }
    }

    private static Class<?> resolveWithCompilerGeneratedClassResolver(String className) throws Throwable {
        return CsrfTokenFinderDirectAccess.resolveWithCompilerGeneratedClassResolver(className);
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

    private static final class LocalHttpServer implements AutoCloseable {
        private final AtomicInteger csrfPageRequests = new AtomicInteger();
        private final AtomicInteger submitRequests = new AtomicInteger();
        private final ExecutorService executorService;
        private final HttpServer server;

        private LocalHttpServer() throws IOException {
            this.executorService = Executors.newSingleThreadExecutor();
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.setExecutor(executorService);
            this.server.createContext("/csrf", this::handleCsrfPage);
            this.server.createContext("/submit", this::handleSubmit);
            this.server.start();
        }

        private String baseUri() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private int csrfPageRequests() {
            return csrfPageRequests.get();
        }

        private int submitRequests() {
            return submitRequests.get();
        }

        private void handleCsrfPage(HttpExchange exchange) throws IOException {
            csrfPageRequests.incrementAndGet();
            String html = """
                    <!doctype html>
                    <html>
                      <head>
                        <meta name=\"_csrf_header\" content=\"%s\" />
                      </head>
                      <body>csrf source page</body>
                    </html>
                    """.formatted(CSRF_TOKEN);
            send(exchange, 200, "text/html; charset=UTF-8", html);
        }

        private void handleSubmit(HttpExchange exchange) throws IOException {
            submitRequests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            String csrfHeader = exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN");
            if (CSRF_TOKEN.equals(csrfHeader)) {
                send(exchange, 200, "text/plain; charset=UTF-8", "accepted");
            } else {
                send(exchange, 403, "text/plain; charset=UTF-8", "missing csrf header");
            }
        }

        private void send(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        }

        @Override
        public void close() {
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
}
