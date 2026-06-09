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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.RequestSpecificationImpl"
            + "$_convertFormParamsToMultiPartParams_closure26";

    @Test
    void convertsFormParametersToMultipartPartsWhenMultipartRequestIsSent() throws Throwable {
        String dynamicallyLoadedClassName = String.class.getName();
        assertEquals(dynamicallyLoadedClassName, invokeGeneratedClassLookup(dynamicallyLoadedClassName).getName());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/upload",
                RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26Test::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .multiPart("document", "native-image")
                    .formParam("tag", "metadata", "coverage")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("multipart form parameters converted");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26Test.class
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
            boolean containsMultipartFormParameters = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && body.contains("name=\"document\"")
                    && body.contains("native-image")
                    && body.contains("name=\"tag\"")
                    && body.contains("metadata")
                    && body.contains("coverage");
            byte[] responseBytes = (containsMultipartFormParameters
                    ? "multipart form parameters converted"
                    : "missing multipart form parameters").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    containsMultipartFormParameters ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
