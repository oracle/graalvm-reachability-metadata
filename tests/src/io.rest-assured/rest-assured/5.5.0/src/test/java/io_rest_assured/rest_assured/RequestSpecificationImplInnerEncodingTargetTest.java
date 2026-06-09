/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInnerEncodingTargetTest {
    private static final String ENCODING_TARGET_CLASS_NAME =
            "io.restassured.internal.RequestSpecificationImpl$EncodingTarget";

    @Test
    void encodesPathAndFormParametersThroughRequestSpecification() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/submit", RequestSpecificationImplInnerEncodingTargetTest::echoRequest);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .formParam("message text", "hello world")
                    .when()
                    .post("/submit/{id}", "a b");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString())
                    .contains("path=/submit/a%20b")
                    .contains("message%20text=hello%20world");
            assertThat(invokeGeneratedClassLookup(RestAssured.class.getName())).isEqualTo(RestAssured.class);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> encodingTargetClass = RequestSpecificationImplInnerEncodingTargetTest.class
                .getClassLoader()
                .loadClass(ENCODING_TARGET_CLASS_NAME);
        return (Class<?>) InvokerHelper.invokeStaticMethod(encodingTargetClass, "class$", className);
    }

    private static void echoRequest(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String response = "path=" + exchange.getRequestURI().getRawPath()
                + "\nbody=" + new String(requestBody, StandardCharsets.UTF_8);
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
