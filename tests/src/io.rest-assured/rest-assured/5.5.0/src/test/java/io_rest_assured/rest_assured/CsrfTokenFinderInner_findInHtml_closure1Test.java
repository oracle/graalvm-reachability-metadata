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
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CsrfTokenFinderInner_findInHtml_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.csrf.CsrfTokenFinder"
            + "$_findInHtml_closure1";
    private static final String CSRF_TOKEN = "csrf-token-from-find-in-html";
    private static final String DYNAMICALLY_LOADED_CLASS_NAME = "io.restassured.internal.support.FileReader";

    @Test
    void sendsCsrfTokenDiscoveredThroughGeneratedFindInHtmlClosure() throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/csrf", CsrfTokenFinderInner_findInHtml_closure1Test::sendCsrfFormPage);
        server.createContext("/submit", CsrfTokenFinderInner_findInHtml_closure1Test::sendSubmittedCsrfFormValue);
        server.start();
        RestAssured.reset();
        String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
        AtomicReference<Closure<?>> generatedFindInHtmlClosure = new AtomicReference<>();
        Class<?> closureClass = CsrfTokenFinderInner_findInHtml_closure1Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass originalMetaClass = registry.getMetaClass(closureClass);
        ProxyMetaClass proxyMetaClass = ProxyMetaClass.getInstance(closureClass);
        proxyMetaClass.setInterceptor(new CapturingClosureCallInterceptor(generatedFindInHtmlClosure));
        registry.setMetaClass(closureClass, proxyMetaClass);

        try {
            Response response = RestAssured
                    .given()
                    .baseUri(baseUri)
                    .csrf(baseUri + "/csrf")
                    .when()
                    .post("/submit");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo(CSRF_TOKEN);
            Closure<?> closure = generatedFindInHtmlClosure.get();
            Class<?> resolvedClass = invokeGeneratedClassLookup(closure, DYNAMICALLY_LOADED_CLASS_NAME);
            Class<?> reflectivelyResolvedClass = invokeGeneratedClassLookupReflectively(
                    closure,
                    DYNAMICALLY_LOADED_CLASS_NAME);
            assertEquals(DYNAMICALLY_LOADED_CLASS_NAME, resolvedClass.getName());
            assertEquals(DYNAMICALLY_LOADED_CLASS_NAME, reflectivelyResolvedClass.getName());
        } finally {
            registry.setMetaClass(closureClass, originalMetaClass);
            RestAssured.reset();
            server.stop(0);
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(Closure<?> closure, String className) throws Throwable {
        assertCapturedClosure(closure);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closure.getClass(), MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closure.getClass(),
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static Class<?> invokeGeneratedClassLookupReflectively(
            Closure<?> closure,
            String className) throws Exception {
        assertCapturedClosure(closure);
        Method classHelper = closure.getClass().getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private static void assertCapturedClosure(Closure<?> closure) {
        assertNotNull(closure);
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
    }

    private static void sendCsrfFormPage(HttpExchange exchange) throws IOException {
        String response = """
                <html>
                  <body>
                    <form method="post" action="/submit">
                      <input type="hidden" name="_csrf" value="%s"/>
                    </form>
                  </body>
                </html>
                """.formatted(CSRF_TOKEN);
        sendResponse(exchange, "text/html; charset=utf-8", response);
    }

    private static void sendSubmittedCsrfFormValue(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String csrfToken = findFormValue(requestBody, "_csrf");
        sendResponse(exchange, "text/plain; charset=utf-8", csrfToken);
    }

    private static String findFormValue(String formBody, String name) {
        for (String pair : formBody.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && decode(keyValue[0]).equals(name)) {
                return decode(keyValue[1]);
            }
        }
        return "";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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
        private final AtomicReference<Closure<?>> generatedFindInHtmlClosure;

        private CapturingClosureCallInterceptor(AtomicReference<Closure<?>> generatedFindInHtmlClosure) {
            this.generatedFindInHtmlClosure = generatedFindInHtmlClosure;
        }

        @Override
        public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
            if (object instanceof Closure<?> closure && CLOSURE_CLASS_NAME.equals(closure.getClass().getName())) {
                generatedFindInHtmlClosure.set(closure);
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
