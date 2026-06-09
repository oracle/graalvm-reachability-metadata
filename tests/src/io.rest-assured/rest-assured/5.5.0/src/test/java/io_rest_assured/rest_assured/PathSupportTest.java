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

import static org.assertj.core.api.Assertions.assertThat;

public class PathSupportTest {
    @Test
    void mergesBaseUriPathAndRequestPathForPublicDslRequests() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/resource", PathSupportTest::sendObservedUri);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1:" + server.getAddress().getPort() + "/api/")
                    .queryParam("from", "query")
                    .when()
                    .get("/resource?embedded=true");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString())
                    .startsWith("/api/resource\n")
                    .contains("embedded=true")
                    .contains("from=query");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    @Test
    void fullyQualifiedRequestUriDoesNotMergeConfiguredBasePath() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/absolute", PathSupportTest::sendObservedUri);
        server.start();
        RestAssured.reset();

        try {
            String targetUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/absolute?absolute=true";
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1:" + server.getAddress().getPort() + "/ignored/")
                    .when()
                    .get(targetUri);

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("/absolute\nabsolute=true");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    private static void sendObservedUri(HttpExchange exchange) throws IOException {
        String response = exchange.getRequestURI().getRawPath()
                + "\n"
                + exchange.getRequestURI().getRawQuery();
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }
}
