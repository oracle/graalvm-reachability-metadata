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
import io.restassured.internal.multipart.MultiPartInternal;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiPartInternalTest {
    private static final String MULTI_PART_INTERNAL_CLASS_NAME =
            "io.restassured.internal.multipart.MultiPartInternal";

    @Test
    void sendsStringAndBinaryMultipartContent() throws Throwable {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", MultiPartInternalTest::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .multiPart("description", "native-image multipart", "text/plain")
                    .multiPart(
                            "payload",
                            "payload.bin",
                            "binary payload".getBytes(StandardCharsets.UTF_8),
                            "application/octet-stream")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("multipart content received");
            assertEquals(
                    MULTI_PART_INTERNAL_CLASS_NAME,
                    invokeGeneratedClassLookup(MULTI_PART_INTERNAL_CLASS_NAME).getName());
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                MultiPartInternal.class,
                MethodHandles.lookup());
        MethodHandle classHelper = lookup.findStatic(
                MultiPartInternal.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
        return (Class<?>) classHelper.invoke(className);
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            boolean receivedExpectedParts = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && body.contains("name=\"description\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains("native-image multipart")
                    && body.contains("name=\"payload\"; filename=\"payload.bin\"")
                    && body.contains("Content-Type: application/octet-stream")
                    && body.contains("binary payload");
            byte[] responseBytes = (receivedExpectedParts
                    ? "multipart content received"
                    : "multipart content missing").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    receivedExpectedParts ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
