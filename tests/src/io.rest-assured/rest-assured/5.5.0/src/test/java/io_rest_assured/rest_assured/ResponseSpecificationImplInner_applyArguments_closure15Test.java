/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import io.restassured.http.ContentType;
import io.restassured.specification.Argument;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_applyArguments_closure15Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_applyArguments_closure15";

    @Test
    void appliesRootPathArgumentsWhenValidatingJsonBody() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();
        List<Argument> rootPathArguments = withArgs(0);
        AtomicReference<Closure<?>> generatedApplyArgumentsClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        Class<?> argumentsClass = rootPathArguments.getClass();
        MetaClass originalArgumentsMetaClass = registry.getMetaClass(argumentsClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(argumentsClass);
        proxyMetaClass.setInterceptor(new CapturingCollectClosureInterceptor(generatedApplyArgumentsClosure));
        registry.setMetaClass(argumentsClass, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/catalog")
                    .then()
                    .rootPath("items[%d]", rootPathArguments)
                    .body("name", equalTo("book"));

            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(
                    generatedApplyArgumentsClosure.get(),
                    String.class.getName());
            assertEquals(String.class, resolvedLookupClass);
        } finally {
            registry.setMetaClass(argumentsClass, originalArgumentsMetaClass);
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void appliesBodyPathArgumentsWhenValidatingJsonBody() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/catalog", this::sendJsonResponse);
        server.setExecutor(executor);
        server.start();

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/catalog")
                    .then()
                    .body("items[%d].name", withArgs(0), equalTo("book"));
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendJsonResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", ContentType.JSON.toString());
            byte[] body = "{\"items\":[{\"name\":\"book\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        assertNotNull(closure);
        Class<?> closureClass = closure.getClass();
        assertEquals(CLOSURE_CLASS_NAME, closureClass.getName());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classLookup.invoke(className);
    }

    private static final class CapturingCollectClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedApplyArgumentsClosure;

        private CapturingCollectClosureInterceptor(AtomicReference<Closure<?>> generatedApplyArgumentsClosure) {
            this.generatedApplyArgumentsClosure = generatedApplyArgumentsClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("collect".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedApplyArgumentsClosure.set(closure);
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

