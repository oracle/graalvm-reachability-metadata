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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestAssuredHttpBuilderGroovyHelperTest {
    private static final String HELPER_CLASS_NAME = "io.restassured.internal.RestAssuredHttpBuilderGroovyHelper";

    @Test
    void sendsMultipleHeaderValuesAsIndividualHeaders() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/headers", RestAssuredHttpBuilderGroovyHelperTest::echoTraceHeaders);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            String traceHeaders = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .header("X-Trace", "alpha", "beta", 3)
                    .when()
                    .post("/headers")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .extract()
                    .asString();

            assertThat(traceHeaders).isEqualTo("alpha,beta,3");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void generatedClassLookupResolvesAndInstantiatesHelperClass() throws Throwable {
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup(HELPER_CLASS_NAME);
        Object helperInstance = dynamicallyResolvedClass.getDeclaredConstructor().newInstance();

        assertThat(dynamicallyResolvedClass.getName()).isEqualTo(HELPER_CLASS_NAME);
        assertThat(helperInstance).isInstanceOf(dynamicallyResolvedClass);
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        ClassLoader testClassLoader = RestAssuredHttpBuilderGroovyHelperTest.class.getClassLoader();
        Class<?> helperClass = testClassLoader.loadClass(HELPER_CLASS_NAME);
        MethodHandles.Lookup helperLookup = MethodHandles.privateLookupIn(helperClass, MethodHandles.lookup());
        MethodHandle classHelper = helperLookup.findStatic(
                helperClass,
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
}
