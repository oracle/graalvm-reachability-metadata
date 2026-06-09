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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_generateBoundary_closure25Test {
    @Test
    void multipartRequestReceivesGeneratedBoundaryWhenNoBoundaryIsConfigured() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/upload",
                RequestSpecificationImplInner_generateBoundary_closure25Test::handleUpload);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .multiPart("sample", "boundary coverage")
                    .when()
                    .post("/upload");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("generated multipart boundary received");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            String boundary = extractBoundary(contentType);
            boolean receivedGeneratedBoundary = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && boundary != null
                    && boundary.length() >= 30
                    && body.contains("--" + boundary)
                    && body.contains("name=\"sample\"")
                    && body.contains("boundary coverage");
            byte[] responseBytes = (receivedGeneratedBoundary
                    ? "generated multipart boundary received"
                    : "generated multipart boundary missing").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    receivedGeneratedBoundary ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
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
        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
            return boundary.substring(1, boundary.length() - 1);
        }
        int semicolonIndex = boundary.indexOf(';');
        return semicolonIndex < 0 ? boundary : boundary.substring(0, semicolonIndex).trim();
    }
}
