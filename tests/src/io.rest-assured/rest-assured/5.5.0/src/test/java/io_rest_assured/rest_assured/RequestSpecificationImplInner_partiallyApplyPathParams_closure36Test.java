/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_partiallyApplyPathParams_closure36Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_partiallyApplyPathParams_closure36";

    @Test
    void unnamedPathParametersAreAppliedAndEncodedBeforeRequestIsSent() throws Throwable {
        String dynamicallyLoadedClassName = "io.restassured.internal.support.Prettifier";
        Class<?> dynamicallyResolvedClass = invokeGeneratedClassLookup(dynamicallyLoadedClassName);
        assertThat(dynamicallyResolvedClass.getName()).isEqualTo(dynamicallyLoadedClassName);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", RequestSpecificationImplInner_partiallyApplyPathParams_closure36Test::sendRawPath);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/stores/{department}/items/{itemId}", "coffee beans", "42");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo("/stores/coffee%20beans/items/42");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_partiallyApplyPathParams_closure36Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        CallSite callSite = LambdaMetafactory.metafactory(
                lookup,
                "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class),
                classHelper,
                MethodType.methodType(Class.class, String.class));
        Function<String, Class<?>> generatedClassLookup = (Function<String, Class<?>>) callSite
                .getTarget()
                .invoke();
        return generatedClassLookup.apply(className);
    }

    private static void sendRawPath(HttpExchange exchange) throws IOException {
        byte[] responseBytes = exchange.getRequestURI().getRawPath().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
