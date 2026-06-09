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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_cookie_closure10Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_cookie_closure10";

    @Test
    void sendsAdditionalCookieValuesAddedThroughVarargs() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cookies", RequestSpecificationImplInner_cookie_closure10Test::echoCookieHeader);
        server.start();
        RestAssured.reset();

        String dynamicallyLoadedClassName = "io.restassured.http.Cookie$Builder";
        assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .cookie("flavor", "vanilla", "chocolate")
                    .when()
                    .get("/cookies");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString())
                    .contains("flavor=vanilla")
                    .contains("flavor=chocolate");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_cookie_closure10Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void echoCookieHeader(HttpExchange exchange) throws IOException {
        String response = exchange.getRequestHeaders().getFirst("Cookie");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
