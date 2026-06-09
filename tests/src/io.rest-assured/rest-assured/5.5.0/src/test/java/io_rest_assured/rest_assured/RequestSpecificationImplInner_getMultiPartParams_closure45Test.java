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
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.MultiPartSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_getMultiPartParams_closure45Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_getMultiPartParams_closure45";

    @Test
    void filterCanInspectMultipartSpecificationsBeforeRequestIsSent() throws Throwable {
        String dynamicallyLoadedClassName = RestAssured.class.getName();
        assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/upload",
                RequestSpecificationImplInner_getMultiPartParams_closure45Test::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .filter((requestSpec, responseSpec, context) -> {
                        List<MultiPartSpecification> multiParts = requestSpec.getMultiPartParams();
                        assertThat(multiParts).hasSize(1);
                        MultiPartSpecification multiPart = multiParts.get(0);
                        assertThat(multiPart.getControlName()).isEqualTo("description");
                        assertThat(multiPart.getContent()).isEqualTo("filter-visible body");
                        assertThat(multiPart.getMimeType()).isEqualTo("text/plain");
                        return context.next(requestSpec, responseSpec);
                    })
                    .multiPart("description", "filter-visible body", "text/plain")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("multipart specification inspected");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_getMultiPartParams_closure45Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            boolean receivedExpectedPart = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && body.contains("name=\"description\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains("filter-visible body");
            byte[] responseBytes = (receivedExpectedPart
                    ? "multipart specification inspected"
                    : "multipart specification missing").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    receivedExpectedPart ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
