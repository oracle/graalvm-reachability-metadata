/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
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

public class RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26_closure53Test {
    @Test
    void convertsEachValueOfCollectionFormParameterToMultipartPart() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/upload",
                RequestSpecificationImplInner_convertFormParamsToMultiPartParams_closure26_closure53Test::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .multiPart("document", "native-image")
                    .formParam("category", Arrays.asList("metadata", "coverage"))
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("collection form parameter converted");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            boolean convertedCollectionValues = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && body.contains("name=\"document\"")
                    && body.contains("native-image")
                    && countOccurrences(body, "name=\"category\"") == 2
                    && body.contains("metadata")
                    && body.contains("coverage");
            byte[] responseBytes = (convertedCollectionValues
                    ? "collection form parameter converted"
                    : "missing converted collection values").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    convertedCollectionValues ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static int countOccurrences(String value, String token) {
        int count = 0;
        int index = value.indexOf(token);
        while (index >= 0) {
            count++;
            index = value.indexOf(token, index + token.length());
        }
        return count;
    }
}
