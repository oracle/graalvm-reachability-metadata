/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.Interceptor;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.ProxyMetaClass;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.config.CsrfConfig.CsrfPrioritization.HEADER;
import static io.restassured.config.CsrfConfig.csrfConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CsrfTokenFinderInner_findCsrfHeaderToken_closure3Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.csrf.CsrfTokenFinder"
            + "$_findCsrfHeaderToken_closure3";
    private static final String CSRF_TOKEN = "csrf-token-from-meta-header";
    private static final String DYNAMICALLY_LOADED_CLASS_NAME = "io.restassured.internal.support.FileReader";

    @Test
    void sendsCsrfTokenDiscoveredFromPrioritizedHtmlMetaTag() throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/csrf", CsrfTokenFinderInner_findCsrfHeaderToken_closure3Test::sendCsrfHeaderPage);
        server.createContext("/submit", CsrfTokenFinderInner_findCsrfHeaderToken_closure3Test::sendSubmittedCsrfHeader);
        server.start();
        RestAssured.reset();
        String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicReference<Closure<?>> generatedHeaderClosure = new AtomicReference<>();
        Class<?> closureClass = CsrfTokenFinderInner_findCsrfHeaderToken_closure3Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(closureClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(closureClass);
        proxyMetaClass.setInterceptor(new CapturingClosureCallInterceptor(generatedHeaderClosure));
        registry.setMetaClass(closureClass, proxyMetaClass);

        try {
            Response response = RestAssured
                    .given()
                    .config(RestAssuredConfig.config().csrfConfig(csrfConfig().csrfPrioritization(HEADER)))
                    .baseUri(baseUri)
                    .csrf(baseUri + "/csrf")
                    .when()
                    .post("/submit");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo(CSRF_TOKEN);
            assertEquals(
                    DYNAMICALLY_LOADED_CLASS_NAME,
                    invokeGeneratedClassLookup(generatedHeaderClosure.get(), DYNAMICALLY_LOADED_CLASS_NAME).getName());
        } finally {
            registry.setMetaClass(closureClass, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) {
        assertNotNull(closure);
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        Object resolvedClass = closure.getMetaClass()
                .invokeStaticMethod(closure, "class$", new Object[] {className});
        return (Class<?>) resolvedClass;
    }

    private static void sendCsrfHeaderPage(HttpExchange exchange) throws IOException {
        String response = """
                <html>
                  <head>
                    <meta name="_csrf_header" content="%s"/>
                  </head>
                  <body>csrf token page</body>
                </html>
                """.formatted(CSRF_TOKEN);
        sendResponse(exchange, "text/html; charset=utf-8", response);
    }

    private static void sendSubmittedCsrfHeader(HttpExchange exchange) throws IOException {
        sendResponse(exchange, "text/plain; charset=utf-8", exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
    }

    private static void sendResponse(HttpExchange exchange, String contentType, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private static final class CapturingClosureCallInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedHeaderClosure;

        private CapturingClosureCallInterceptor(AtomicReference<Closure<?>> generatedHeaderClosure) {
            this.generatedHeaderClosure = generatedHeaderClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (object instanceof Closure<?> closure && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedHeaderClosure.set(closure);
            }
            return null;
        }

        @Override
        public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
            return result;
        }

        @Override
        public boolean doInvoke() {
            return true;
        }
    }
}
