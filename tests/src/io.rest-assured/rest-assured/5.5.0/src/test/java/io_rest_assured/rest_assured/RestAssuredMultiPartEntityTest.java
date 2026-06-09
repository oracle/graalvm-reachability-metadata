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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.internal.multipart.RestAssuredMultiPartEntity;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RestAssuredMultiPartEntityTest {
    private static final String EXPLICIT_BOUNDARY = "rest-assured-entity-boundary";

    @Test
    void groovyRuntimeResolvesRestAssuredMultipartEntityClassHelper() throws Throwable {
        assertThat(invokeGeneratedClassLookup(RestAssuredMultiPartEntity.class.getName()))
                .isSameAs(RestAssuredMultiPartEntity.class);
    }

    @Test
    void multipartRequestWithCharsetUsesRestAssuredMultipartEntity() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", RestAssuredMultiPartEntityTest::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType("multipart/mixed; charset=UTF-8; boundary=\"" + EXPLICIT_BOUNDARY + "\"")
                    .multiPart(
                            "message",
                            "message.txt",
                            "multipart entity body".getBytes(StandardCharsets.UTF_8),
                            "text/plain")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("custom multipart entity received");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                RestAssuredMultiPartEntity.class,
                MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                RestAssuredMultiPartEntity.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
            boolean receivedExpectedMultipart = "POST".equals(exchange.getRequestMethod())
                    && normalizedContentType.startsWith("multipart/mixed")
                    && normalizedContentType.contains("charset=utf-8")
                    && EXPLICIT_BOUNDARY.equals(extractBoundary(contentType))
                    && body.contains("--" + EXPLICIT_BOUNDARY)
                    && body.contains("name=\"message\"; filename=\"message.txt\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains("multipart entity body");
            byte[] responseBytes = (receivedExpectedMultipart
                    ? "custom multipart entity received"
                    : "custom multipart entity missing").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    receivedExpectedMultipart ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) {
            return null;
        }
        String marker = "boundary=";
        int markerIndex = contentType.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        String boundary = contentType.substring(markerIndex + marker.length()).trim();
        int semicolonIndex = boundary.indexOf(';');
        if (semicolonIndex >= 0) {
            boundary = boundary.substring(0, semicolonIndex).trim();
        }
        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
            return boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
    }
}
