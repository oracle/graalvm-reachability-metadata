/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import io.restassured.internal.ResponseSpecificationImpl;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_contentType_closure14Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_contentType_closure14";
    private static final String LOOKUP_CLASS_NAME = "io.restassured.specification.ProxySpecification";

    @Test
    void validatesMatcherContentTypeExpectation() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", ResponseSpecificationImplInner_contentType_closure14Test::sendJsonResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedContentTypeClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(ResponseSpecificationImpl.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ResponseSpecificationImpl.class);
        proxyMetaClass.setInterceptor(new CapturingValidateResponseClosureInterceptor(generatedContentTypeClosure));
        registry.setMetaClass(ResponseSpecificationImpl.class, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/json")
                    .then()
                    .contentType(startsWith("application/json"));

            Closure<?> capturedClosure = generatedContentTypeClosure.get();
            assertNotNull(capturedClosure);
            assertEquals(CLOSURE_CLASS_NAME, capturedClosure.getClass().getName());
            assertEquals(LOOKUP_CLASS_NAME, invokeGeneratedClassLookup(capturedClosure.getClass()).getName());
        } finally {
            registry.setMetaClass(ResponseSpecificationImpl.class, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Class<?> closureClass) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(LOOKUP_CLASS_NAME);
    }

    private static void sendJsonResponse(HttpExchange exchange) throws IOException {
        byte[] body = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static final class CapturingValidateResponseClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedContentTypeClosure;

        private CapturingValidateResponseClosureInterceptor(AtomicReference<Closure<?>> generatedContentTypeClosure) {
            this.generatedContentTypeClosure = generatedContentTypeClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("validateResponseIfRequired".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedContentTypeClosure.set(closure);
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
