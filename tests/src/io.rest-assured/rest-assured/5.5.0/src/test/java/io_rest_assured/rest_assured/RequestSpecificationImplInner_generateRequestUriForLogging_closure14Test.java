/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_generateRequestUriForLogging_closure14Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_generateRequestUriForLogging_closure14";

    @Test
    void generatesLoggingUriFromQueryParametersDefinedInPath() throws Throwable {
        String dynamicallyLoadedClassName = String.class.getName();
        assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());

        ByteArrayOutputStream logBuffer = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(logBuffer, true, StandardCharsets.UTF_8);
        RestAssuredConfig config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().defaultStream(logStream));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/logging",
                RequestSpecificationImplInner_generateRequestUriForLogging_closure14Test::echoRawQuery);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .log().uri()
                    .when()
                    .get("/logging?flag&empty=&name=native");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).contains("flag", "empty=", "name=native");
            assertThat(logBuffer.toString(StandardCharsets.UTF_8)).contains(
                    "/logging?",
                    "flag",
                    "empty=",
                    "name=native");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_generateRequestUriForLogging_closure14Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void echoRawQuery(HttpExchange exchange) throws IOException {
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
