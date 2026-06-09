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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestAssuredResponseOptionsGroovyImplInner_cookies_closure4Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RestAssuredResponseOptionsGroovyImpl"
            + "$_cookies_closure4";
    private static final String DYNAMICALLY_LOADED_CLASS_NAME = "io.restassured.internal.support.FileReader";

    @Test
    void responseCookiesReturnsCookieValuesFromSetCookieHeaders() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/cookies", RestAssuredResponseOptionsGroovyImplInner_cookies_closure4Test::sendCookieResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/cookies");

            Map<String, String> cookies = response.cookies();

            assertEquals("abc", cookies.get("session"));
            assertEquals("light", cookies.get("theme"));
            assertEquals(
                    DYNAMICALLY_LOADED_CLASS_NAME,
                    invokeGeneratedClassLookup(DYNAMICALLY_LOADED_CLASS_NAME).getName());
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendCookieResponse(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", "session=abc; Path=/cookies; HttpOnly");
            headers.add("Set-Cookie", "theme=light; Path=/cookies");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
        } finally {
            exchange.close();
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RestAssuredResponseOptionsGroovyImplInner_cookies_closure4Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }
}
