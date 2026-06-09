/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
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
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestAssuredResponseOptionsGroovyImplInner_as_closure3Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl"
            + "$_as_closure3";
    private static final String DYNAMICALLY_LOADED_CLASS_NAME = "io.restassured.internal.support.FileReader";

    @Test
    void rejectsResponseMappingWithoutContentTypeOrDefaultParser() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/untyped", RestAssuredResponseOptionsGroovyImplInner_as_closure3Test::sendUntypedResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedAsClosure = new AtomicReference<>();
        Class<?> closureClass = RestAssuredResponseOptionsGroovyImplInner_as_closure3Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(closureClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(closureClass);
        proxyMetaClass.setInterceptor(new CapturingClosureCallInterceptor(generatedAsClosure));
        registry.setMetaClass(closureClass, proxyMetaClass);

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/untyped");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> response.as(Payload.class));

            assertTrue(exception.getMessage().contains("no content-type was present"));
            assertEquals(
                    DYNAMICALLY_LOADED_CLASS_NAME,
                    invokeGeneratedClassLookup(generatedAsClosure.get(), DYNAMICALLY_LOADED_CLASS_NAME).getName());
        } finally {
            registry.setMetaClass(closureClass, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendUntypedResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) {
        assertNotNull(closure);
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        Object resolvedClass = closure.getMetaClass()
                .invokeStaticMethod(closure, "class$", new Object[] {className});
        return (Class<?>) resolvedClass;
    }

    private record Payload(String message) {
    }

    private static final class CapturingClosureCallInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedAsClosure;

        private CapturingClosureCallInterceptor(AtomicReference<Closure<?>> generatedAsClosure) {
            this.generatedAsClosure = generatedAsClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (object instanceof Closure<?> closure && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedAsClosure.set(closure);
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
