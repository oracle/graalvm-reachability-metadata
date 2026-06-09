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
import java.util.ArrayList;
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
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInnerHamcrestAssertionClosure_validateHeadersAndCookies_closure6Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal."
            + "ResponseSpecificationImpl$_HamcrestAssertionClosure_validateHeadersAndCookies_closure6";

    @Test
    void validatesExpectedCookiesThroughGeneratedCookieAssertionClosure() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookies", this::sendResponseWithCookies);
        server.setExecutor(executor);
        server.start();
        AtomicReference<Closure<?>> generatedCookieValidationClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalArrayListMetaClass = registry.getMetaClass(ArrayList.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ArrayList.class);
        proxyMetaClass.setInterceptor(new CapturingCollectClosureInterceptor(generatedCookieValidationClosure));
        registry.setMetaClass(ArrayList.class, proxyMetaClass);

        try {
            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/cookies")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_NO_CONTENT)
                    .cookie("session", equalTo("abc"))
                    .cookies("theme", equalTo("light"), "locale", "en-US");

            String lookupClassName = "io.restassured.response.Response";
            Class<?> resolvedLookupClass = invokeGeneratedClassLookup(
                    generatedCookieValidationClosure.get(),
                    lookupClassName);
            assertEquals(lookupClassName, resolvedLookupClass.getName());
        } finally {
            registry.setMetaClass(ArrayList.class, originalArrayListMetaClass);
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
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
        private final AtomicReference<Closure<?>> generatedCookieValidationClosure;

        private CapturingCollectClosureInterceptor(AtomicReference<Closure<?>> generatedCookieValidationClosure) {
            this.generatedCookieValidationClosure = generatedCookieValidationClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("collect".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedCookieValidationClosure.set(closure);
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

    private void sendResponseWithCookies(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "session=abc; Path=/; HttpOnly");
            headers.add("Set-Cookie", "theme=light; Path=/; SameSite=Lax");
            headers.add("Set-Cookie", "locale=en-US; Path=/");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
        } finally {
            exchange.close();
        }
    }
}
