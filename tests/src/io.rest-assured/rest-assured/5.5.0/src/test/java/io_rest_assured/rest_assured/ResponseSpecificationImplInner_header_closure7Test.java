/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
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
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.internal.ResponseSpecificationImpl;
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_header_closure7Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_header_closure7";

    @Test
    void validatesHeaderMatcherExpectationFromResponseSpecification() throws Exception {
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedHeaderClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(ResponseSpecificationImpl.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ResponseSpecificationImpl.class);
        proxyMetaClass.setInterceptor(new CapturingValidateResponseClosureInterceptor(generatedHeaderClosure));
        registry.setMetaClass(ResponseSpecificationImpl.class, proxyMetaClass);
        ResponseSpecification responseSpecification = new ResponseSpecBuilder()
                .expectHeader("X-Trace-Id", startsWith("trace-"))
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/trace", ResponseSpecificationImplInner_header_closure7Test::sendTraceResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/trace")
                    .then()
                    .spec(responseSpecification);
            Closure<?> capturedClosure = generatedHeaderClosure.get();
            assertNotNull(capturedClosure);
            assertEquals(CLOSURE_CLASS_NAME, capturedClosure.getClass().getName());
            assertEquals(String.class, invokeGeneratedClassLookup(capturedClosure, String.class.getName()));
        } finally {
            registry.setMetaClass(ResponseSpecificationImpl.class, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Exception {
        Method classHelper = closure.getClass().getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private static void sendTraceResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("X-Trace-Id", "trace-12345");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class CapturingValidateResponseClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedHeaderClosure;

        private CapturingValidateResponseClosureInterceptor(AtomicReference<Closure<?>> generatedHeaderClosure) {
            this.generatedHeaderClosure = generatedHeaderClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("validateResponseIfRequired".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
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
