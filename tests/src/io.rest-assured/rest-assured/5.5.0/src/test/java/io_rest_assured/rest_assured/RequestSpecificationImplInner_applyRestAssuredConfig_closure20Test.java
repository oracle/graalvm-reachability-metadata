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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_applyRestAssuredConfig_closure20Test {
    @Test
    void requestLevelRedirectParameterIsAppliedToHttpClient() throws Throwable {
        String dynamicallyLoadedClassName = String.class.getName();
        assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/redirect",
                RequestSpecificationImplInner_applyRestAssuredConfig_closure20Test::sendRedirect);
        server.createContext("/target", RequestSpecificationImplInner_applyRestAssuredConfig_closure20Test::sendTarget);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .redirects().follow(false)
                    .when()
                    .get("/redirect");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_MOVED_TEMP);
            assertThat(response.header("Location")).isEqualTo("/target");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_applyRestAssuredConfig_closure20Test.class
                .getClassLoader()
                .loadClass("io.restassured.internal.RequestSpecificationImpl$_applyRestAssuredConfig_closure20");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void sendRedirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/target");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1);
        exchange.close();
    }

    private static void sendTarget(HttpExchange exchange) throws IOException {
        byte[] responseBytes = "redirect target".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
