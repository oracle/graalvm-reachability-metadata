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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.MultiPartConfig.multiPartConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_registerRestAssuredEncoders_closure24Test {
    private static final String BOUNDARY = "RestAssuredNativeImageBoundary";
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_registerRestAssuredEncoders_closure24";

    @Test
    void resolvesGeneratedGroovyClassLiteralHelper() throws Throwable {
        Class<?> resolvedClass = invokeGeneratedClassLookup(RestAssured.class.getName());

        assertThat(resolvedClass).isEqualTo(RestAssured.class);
    }

    @Test
    void sendsMultipartRequestWithRestAssuredRegisteredEncoder() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/upload",
                RequestSpecificationImplInner_registerRestAssuredEncoders_closure24Test::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .config(config().multiPartConfig(multiPartConfig()
                            .defaultSubtype("mixed")
                            .defaultBoundary(BOUNDARY)))
                    .multiPart("notes", "notes.txt", "multipart encoder".getBytes(StandardCharsets.UTF_8), "text/plain")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("registered multipart encoder used");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_registerRestAssuredEncoders_closure24Test.class
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
            boolean registeredMultipartEncoderWasUsed = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/mixed")
                    && contentType.contains(BOUNDARY)
                    && body.contains("--" + BOUNDARY)
                    && body.contains("name=\"notes\"")
                    && body.contains("filename=\"notes.txt\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains("multipart encoder");
            byte[] responseBytes = (registeredMultipartEncoderWasUsed
                    ? "registered multipart encoder used"
                    : "registered multipart encoder not used").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    registeredMultipartEncoderWasUsed ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
