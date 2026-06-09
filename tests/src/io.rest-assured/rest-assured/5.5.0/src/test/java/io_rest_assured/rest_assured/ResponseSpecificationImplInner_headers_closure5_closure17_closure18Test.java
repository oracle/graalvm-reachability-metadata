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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResponseSpecificationImplInner_headers_closure5_closure17_closure18Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.ResponseSpecificationImpl"
            + "$_headers_closure5_closure17_closure18";
    private static final String METHOD_HANDLE_LOADED_CLASS_NAME = "org.apache.commons.lang3.StringUtils";
    private static final String REFLECTION_LOADED_CLASS_NAME = "org.hamcrest.MatcherAssert";

    @Test
    void validatesEachMatcherInAListValuedHeaderExpectation() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/headers",
                ResponseSpecificationImplInner_headers_closure5_closure17_closure18Test::sendHeaderResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            List<Object> cacheMatchers = new ArrayList<>();
            cacheMatchers.add(equalTo("cache-hit"));
            cacheMatchers.add(containsString("hit"));

            Map<String, Object> expectedHeaders = new LinkedHashMap<>();
            expectedHeaders.put("X-Cache", cacheMatchers);

            given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/headers")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .headers(expectedHeaders);

            assertEquals(METHOD_HANDLE_LOADED_CLASS_NAME, invokeGeneratedClassLookupWithMethodHandle().getName());
            assertEquals(REFLECTION_LOADED_CLASS_NAME, invokeGeneratedClassLookupWithReflection().getName());
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookupWithMethodHandle() throws Throwable {
        Class<?> closureClass = loadClosureClass();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classLookup = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classLookup.invoke(METHOD_HANDLE_LOADED_CLASS_NAME);
    }

    private static Class<?> invokeGeneratedClassLookupWithReflection() throws Exception {
        Method classLookup = loadClosureClass().getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);
        return (Class<?>) classLookup.invoke(null, REFLECTION_LOADED_CLASS_NAME);
    }

    private static Class<?> loadClosureClass() throws ClassNotFoundException {
        Class<?> closureClass = ResponseSpecificationImplInner_headers_closure5_closure17_closure18Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        assertNotNull(closureClass);
        return closureClass;
    }

    private static void sendHeaderResponse(HttpExchange exchange) throws IOException {
        try {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.add("X-Cache", "cache-hit");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }
}
