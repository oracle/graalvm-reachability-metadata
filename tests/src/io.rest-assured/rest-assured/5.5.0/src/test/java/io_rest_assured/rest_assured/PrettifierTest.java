/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrettifierTest {
    @Test
    void prettyPrintFormatsJsonResponsesThroughPublicDsl() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", PrettifierTest::sendJsonBody);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/json");

            String prettyBody = response.prettyPrint();

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(prettyBody)
                    .contains("\n")
                    .contains("\"message\"")
                    .contains("\"hello\"")
                    .contains("\"count\"");
        } finally {
            RestAssured.reset();
            server.stop(0);
        }
    }

    @Test
    void requestBodyLoggingFormatsJsonThroughPublicDsl() throws IOException {
        ByteArrayOutputStream loggedBody = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(loggedBody, true, StandardCharsets.UTF_8);
        RestAssuredConfig config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().defaultStream(logStream));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", PrettifierTest::sendOk);
        server.start();
        RestAssured.reset();

        try {
            Response response = RestAssured
                    .given()
                    .config(config)
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .contentType(ContentType.JSON)
                    .body("{\"message\":\"hello\",\"count\":1}")
                    .log()
                    .body()
                    .when()
                    .post("/echo");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(loggedBody.toString(StandardCharsets.UTF_8))
                    .contains("\n")
                    .contains("\"message\"")
                    .contains("\"hello\"")
                    .contains("\"count\"");
        } finally {
            RestAssured.reset();
            logStream.close();
            server.stop(0);
        }
    }

    private static void sendJsonBody(HttpExchange exchange) throws IOException {
        byte[] responseBytes = "{\"message\":\"hello\",\"count\":1}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private static void sendOk(HttpExchange exchange) throws IOException {
        byte[] responseBytes = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }
}
