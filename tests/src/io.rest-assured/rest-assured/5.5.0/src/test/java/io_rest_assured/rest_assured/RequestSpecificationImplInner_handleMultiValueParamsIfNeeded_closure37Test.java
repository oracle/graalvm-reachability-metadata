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
import java.util.Arrays;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_handleMultiValueParamsIfNeeded_closure37Test {
    private static final String REQUEST_SPECIFICATION_IMPL = "io.restassured.internal."
            + "RequestSpecificationImpl";
    private static final String CLOSURE_CLASS_NAME = REQUEST_SPECIFICATION_IMPL
            + "$_handleMultiValueParamsIfNeeded_closure37";
    private static final String REST_ASSURED_CLASS_NAME = "io.restassured.RestAssured";

    @Test
    void sendsCollectionFormParameterAsRepeatedUrlEncodedParameters() throws Throwable {
        Class<?> loadedClass = invokeGeneratedClassLookup(REST_ASSURED_CLASS_NAME);
        assertEquals(REST_ASSURED_CLASS_NAME, loadedClass.getName());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/submit", exchange -> handleSubmit(exchange));
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .formParam("category", Arrays.asList("metadata", "coverage"))
                    .when()
                    .post("/submit");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("multi-value form parameter received");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> testClass = RequestSpecificationImplInner_handleMultiValueParamsIfNeeded_closure37Test.class;
        ClassLoader classLoader = testClass.getClassLoader();
        Class<?> closureClass = classLoader.loadClass(CLOSURE_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                closureClass,
                MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                closureClass,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invokeExact(className);
    }

    private static void handleSubmit(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String body = new String(requestBody, StandardCharsets.UTF_8);
            boolean containsRepeatedFormParameter = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("application/x-www-form-urlencoded")
                    && "category=metadata&category=coverage".equals(body);
            byte[] responseBytes = (containsRepeatedFormParameter
                    ? "multi-value form parameter received"
                    : "missing multi-value form parameter").getBytes(StandardCharsets.UTF_8);

            int statusCode = containsRepeatedFormParameter
                    ? HttpURLConnection.HTTP_OK
                    : HttpURLConnection.HTTP_BAD_REQUEST;
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
