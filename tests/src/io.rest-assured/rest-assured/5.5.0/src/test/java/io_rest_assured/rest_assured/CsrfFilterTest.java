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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CsrfFilterTest {
    private static final String CSRF_TOKEN = "csrf-token-from-filter";

    @Test
    void sendsCsrfTokenDiscoveredFromHtmlMetaTag() throws Throwable {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/csrf", CsrfFilterTest::sendCsrfPage);
        server.createContext("/submit", CsrfFilterTest::sendSubmittedCsrfHeader);
        server.start();
        RestAssured.reset();
        String baseUri = "http://127.0.0.1:" + server.getAddress().getPort();

        try {
            Response response = RestAssured
                    .given()
                    .baseUri(baseUri)
                    .csrf(baseUri + "/csrf")
                    .when()
                    .post("/submit");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo(CSRF_TOKEN);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void sendCsrfPage(HttpExchange exchange) throws IOException {
        String response = """
                <html>
                  <head>
                    <meta name="_csrf_header" content="%s"/>
                  </head>
                  <body>csrf token page</body>
                </html>
                """.formatted(CSRF_TOKEN);
        sendResponse(exchange, "text/html; charset=utf-8", response);
    }

    private static void sendSubmittedCsrfHeader(HttpExchange exchange) throws IOException {
        sendResponse(exchange, "text/plain; charset=utf-8", exchange.getRequestHeaders().getFirst("X-CSRF-TOKEN"));
    }

    private static void sendResponse(HttpExchange exchange, String contentType, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        } finally {
            exchange.close();
        }
    }
}
