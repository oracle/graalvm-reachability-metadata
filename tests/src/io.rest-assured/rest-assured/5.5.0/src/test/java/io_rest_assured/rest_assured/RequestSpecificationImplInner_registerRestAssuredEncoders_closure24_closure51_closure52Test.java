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
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.MultiPartSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.MultiPartConfig.multiPartConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51_closure52Test {
    private static final String BOUNDARY = "RestAssuredClosure52Boundary";
    private static final String PART_HEADER_NAME = "X-Part-Trace";
    private static final String PART_HEADER_VALUE = "closure52-header";

    @Test
    void sendsMultipartRequestWithCustomPartHeaders() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/multipart-with-headers",
                RequestSpecificationImplInner_registerRestAssuredEncoders_closure24_closure51_closure52Test
                        ::handleMultipartWithHeaders);
        server.start();
        RestAssured.reset();

        try {
            MultiPartSpecification part = new MultiPartSpecBuilder(
                    "multipart part with custom headers".getBytes(StandardCharsets.UTF_8))
                    .controlName("document")
                    .fileName("document.txt")
                    .mimeType("text/plain")
                    .header(PART_HEADER_NAME, PART_HEADER_VALUE)
                    .build();

            Response response = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .config(config().multiPartConfig(multiPartConfig()
                            .defaultSubtype("form-data")
                            .defaultBoundary(BOUNDARY)))
                    .multiPart(part)
                    .when()
                    .post("/multipart-with-headers");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("custom multipart header serialized");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void handleMultipartWithHeaders(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            boolean customHeaderWasSerialized = "POST".equals(exchange.getRequestMethod())
                    && contentType != null
                    && contentType.startsWith("multipart/form-data")
                    && contentType.contains(BOUNDARY)
                    && body.contains("--" + BOUNDARY)
                    && body.contains("name=\"document\"")
                    && body.contains("filename=\"document.txt\"")
                    && body.contains("Content-Type: text/plain")
                    && body.contains(PART_HEADER_NAME + ": " + PART_HEADER_VALUE)
                    && body.contains("multipart part with custom headers")
                    && !body.contains("Content-Transfer-Encoding");
            byte[] responseBytes = (customHeaderWasSerialized
                    ? "custom multipart header serialized"
                    : "custom multipart header not serialized").getBytes(StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(
                    customHeaderWasSerialized ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_REQUEST,
                    responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
