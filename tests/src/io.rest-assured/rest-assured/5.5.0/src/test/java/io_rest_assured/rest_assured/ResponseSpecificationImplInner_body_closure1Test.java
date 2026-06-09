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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.Interceptor;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.ProxyMetaClass;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.internal.ResponseSpecificationImpl;
import io.restassured.internal.assertion.BodyMatcher;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_body_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_body_closure1";

    @Test
    void validatesWholeBodyMatcherThroughGeneratedBodyClosure() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/message", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedBodyClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(ResponseSpecificationImpl.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ResponseSpecificationImpl.class);
        proxyMetaClass.setInterceptor(new CapturingValidateResponseClosureInterceptor(generatedBodyClosure));
        registry.setMetaClass(ResponseSpecificationImpl.class, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/message")
                    .then()
                    .body(equalTo("{\"message\":\"hello\"}"));

            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(
                    generatedBodyClosure.get(),
                    BodyMatcher.class.getName());
            assertEquals(BodyMatcher.class, resolvedLookupClass);
        } finally {
            registry.setMetaClass(ResponseSpecificationImpl.class, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        assertNotNull(closure);
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        Object resolvedClass = closure.getMetaClass()
                .invokeStaticMethod(closure, "class$", new Object[] {className});
        return (Class<?>) resolvedClass;
    }

    private static final class CapturingValidateResponseClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedBodyClosure;

        private CapturingValidateResponseClosureInterceptor(AtomicReference<Closure<?>> generatedBodyClosure) {
            this.generatedBodyClosure = generatedBodyClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("validateResponseIfRequired".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedBodyClosure.set(closure);
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
