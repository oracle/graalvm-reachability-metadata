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
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.Closure;
import io.restassured.RestAssured;
import io.restassured.internal.RestAssuredHttpBuilderGroovyHelper;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestAssuredHttpBuilderGroovyHelperInner_flattenToString_closure1Test {
    private static final String CLOSURE_CLASS_NAME =
            "io.restassured.internal.RestAssuredHttpBuilderGroovyHelper$_flattenToString_closure1";

    @Test
    void headerValuesAreFlattenedAndConvertedToStrings() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/headers", RestAssuredHttpBuilderGroovyHelperInner_flattenToString_closure1Test
                ::echoTraceHeaders);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            String traceHeaders = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header("X-Trace", "alpha", "beta", 4)
                    .when()
                    .post("/headers")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .extract()
                    .asString();

            assertEquals("alpha,beta,4", traceHeaders);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void helperClosureConvertsFlattenedValuesToStrings() throws Throwable {
        CapturingCollection collection = new CapturingCollection("alpha", 42, null);

        Collection<String> flattenedValues = RestAssuredHttpBuilderGroovyHelper.flattenToString(collection);

        assertEquals(Arrays.asList("alpha", "42", null), flattenedValues);
        Closure<?> closure = collection.getFlattenedValues().getClosure();
        assertEquals(CLOSURE_CLASS_NAME, closure.getClass().getName());
        assertEquals(CLOSURE_CLASS_NAME, invokeGeneratedClassLookup(closure.getClass(), CLOSURE_CLASS_NAME).getName());
    }

    private static Class<?> invokeGeneratedClassLookup(Class<?> closureClass, String className) throws Throwable {
        MethodHandles.Lookup closureLookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = closureLookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void echoTraceHeaders(HttpExchange exchange) throws IOException {
        try {
            List<String> traceHeaders = exchange.getRequestHeaders().get("X-Trace");
            byte[] body = String.join(",", traceHeaders).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class CapturingCollection extends AbstractList<Object> {
        private final List<Object> values;
        private final CapturingFlattenedValues flattenedValues;

        private CapturingCollection(Object... values) {
            this.values = Arrays.asList(values);
            this.flattenedValues = new CapturingFlattenedValues(this.values);
        }

        public CapturingFlattenedValues flatten() {
            return flattenedValues;
        }

        private CapturingFlattenedValues getFlattenedValues() {
            return flattenedValues;
        }

        @Override
        public Object get(int index) {
            return values.get(index);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    public static final class CapturingFlattenedValues {
        private final List<Object> values;
        private transient Closure<?> closure;

        private CapturingFlattenedValues(List<Object> values) {
            this.values = values;
        }

        public List<String> collect(Closure<?> candidate) {
            closure = candidate;
            return values.stream()
                    .map(value -> (String) candidate.call(value))
                    .toList();
        }

        private Closure<?> getClosure() {
            return closure;
        }
    }
}

