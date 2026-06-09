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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_extractRequestParamsIfNeeded_closure28Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_extractRequestParamsIfNeeded_closure28";

    @Test
    void extractsQueryParametersEmbeddedInRequestPath() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/search",
                RequestSpecificationImplInner_extractRequestParamsIfNeeded_closure28Test::handleSearch);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/search?flag&empty=&name=native");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).contains("flag", "empty=", "name=native");

            String dynamicallyLoadedClassName = RestAssured.class.getName();
            assertEquals(RestAssured.class, invokeGeneratedClassLookup(dynamicallyLoadedClassName));
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_extractRequestParamsIfNeeded_closure28Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private static void handleSearch(HttpExchange exchange) throws IOException {
        try {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            byte[] responseBytes = rawQuery.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
