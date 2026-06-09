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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import io.restassured.internal.assertion.CookieMatcher;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_cookies_closure8_closure19_closure20Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_cookies_closure8_closure19_closure20";

    @Test
    void recordsListCookieExpectationsFromMap() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/cookies",
                ResponseSpecificationImplInner_cookies_closure8_closure19_closure20Test::sendCookieResponse);
        server.setExecutor(executor);
        server.start();
        AtomicReference<Closure<?>> generatedCookieListClosure = new AtomicReference<>();
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalArrayListMetaClass = registry.getMetaClass(ArrayList.class);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(ArrayList.class);
        proxyMetaClass.setInterceptor(new CapturingEachClosureInterceptor(generatedCookieListClosure));
        registry.setMetaClass(ArrayList.class, proxyMetaClass);

        try {
            List<Object> sessionMatchers = new ArrayList<>();
            sessionMatchers.add(equalTo("abc"));
            sessionMatchers.add(startsWith("a"));

            List<Object> themeMatchers = new ArrayList<>();
            themeMatchers.add("light-blue");
            themeMatchers.add(endsWith("blue"));

            Map<String, Object> expectedCookies = new LinkedHashMap<>();
            expectedCookies.put("session", sessionMatchers);
            expectedCookies.put("theme", themeMatchers);

            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/cookies")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .cookies(expectedCookies);

            assertEquals(CookieMatcher.class, invokeGeneratedClassLookup(
                    generatedCookieListClosure.get(),
                    CookieMatcher.class.getName()));
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

    private static final class CapturingEachClosureInterceptor implements Interceptor {
        private final AtomicReference<Closure<?>> generatedCookieListClosure;

        private CapturingEachClosureInterceptor(AtomicReference<Closure<?>> generatedCookieListClosure) {
            this.generatedCookieListClosure = generatedCookieListClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if ("each".equals(methodName) && arguments != null && arguments.length == 1
                    && arguments[0] instanceof Closure<?> closure
                    && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedCookieListClosure.set(closure);
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

    private static void sendCookieResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "session=abc; Path=/cookies; HttpOnly");
            headers.add("Set-Cookie", "theme=light-blue; Path=/cookies");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
        } finally {
            exchange.close();
        }
    }
}
