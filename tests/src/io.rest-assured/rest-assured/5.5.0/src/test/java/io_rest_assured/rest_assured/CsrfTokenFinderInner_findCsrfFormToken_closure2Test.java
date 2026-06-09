/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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

public class CsrfTokenFinderInner_findCsrfFormToken_closure2Test {
    private static final String CSRF_TOKEN = "csrf-token-from-form";

    @Test
    void sendsCsrfTokenDiscoveredFromHtmlFormField() throws IOException, InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/csrf", CsrfTokenFinderInner_findCsrfFormToken_closure2Test::sendCsrfFormPage);
        server.createContext(
                "/submit",
                CsrfTokenFinderInner_findCsrfFormToken_closure2Test::sendSubmittedCsrfFormValue);
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

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.asString()).isEqualTo(CSRF_TOKEN);
        } finally {
            RestAssured.reset();
            server.stop(0);
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void sendCsrfFormPage(HttpExchange exchange) throws IOException {
        String response = """
                <html>
                  <body>
                    <form method="post" action="/submit">
                      <input type="hidden" name="_csrf" value="%s"/>
                    </form>
                  </body>
                </html>
                """.formatted(CSRF_TOKEN);
        sendResponse(exchange, "text/html; charset=utf-8", response);
    }

    private static void sendSubmittedCsrfFormValue(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String csrfToken = findFormValue(requestBody, "_csrf");
        sendResponse(exchange, "text/plain; charset=utf-8", csrfToken);
    }

    private static String findFormValue(String formBody, String name) {
        for (String pair : formBody.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && decode(keyValue[0]).equals(name)) {
                return decode(keyValue[1]);
            }
        }
        return "";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, String contentType, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
