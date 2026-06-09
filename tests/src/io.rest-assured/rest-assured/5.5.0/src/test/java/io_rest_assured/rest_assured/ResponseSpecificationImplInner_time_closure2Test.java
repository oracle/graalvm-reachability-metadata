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
import io.restassured.specification.ResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResponseSpecificationImplInner_time_closure2Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_time_closure2";

    @Test
    void validatesResponseTimeExpectationThroughPublicApi() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/timed", ResponseSpecificationImplInner_time_closure2Test::sendTimedResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();
        AtomicReference<Closure<?>> generatedTimeClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(ResponseSpecificationImpl.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ResponseSpecificationImpl.class);
        proxyMetaClass.setInterceptor(new CapturingValidateResponseClosureInterceptor(generatedTimeClosure));
        registry.setMetaClass(ResponseSpecificationImpl.class, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/timed")
                    .then()
                    .time(lessThan(10L), TimeUnit.SECONDS);

            Closure<?> capturedClosure = generatedTimeClosure.get();
            assertNotNull(capturedClosure);
            assertEquals(CLOSURE_CLASS_NAME, capturedClosure.getClass().getName());
            assertSame(
                    ResponseSpecification.class,
                    invokeGeneratedClassLookup(capturedClosure.getClass(), ResponseSpecification.class.getName()));
        } finally {
            registry.setMetaClass(ResponseSpecificationImpl.class, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Class<?> closureClass, String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void sendTimedResponse(HttpExchange exchange) throws IOException {
        byte[] body = "timed".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static final class CapturingValidateResponseClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedTimeClosure;

        private CapturingValidateResponseClosureInterceptor(AtomicReference<Closure<?>> generatedTimeClosure) {
            this.generatedTimeClosure = generatedTimeClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("validateResponseIfRequired".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedTimeClosure.set(closure);
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
