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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import groovy.lang.MetaClass;
import io.restassured.RestAssured;
import io.restassured.internal.csrf.CsrfTokenFinder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CsrfTokenFinderTest {
    private static final String CSRF_TOKEN = "csrf-token-from-page";

    @Test
    void groovyMetaClassResolvesGeneratedClassHelper() {
        CsrfTokenFinder csrfTokenFinder = new CsrfTokenFinder();
        MetaClass metaClass = csrfTokenFinder.getMetaClass();

        Object resolvedClass = metaClass.invokeStaticMethod(
                CsrfTokenFinder.class,
                "class$",
                new Object[] {CsrfTokenFinder.class.getName()});

        assertThat(resolvedClass).isSameAs(CsrfTokenFinder.class);
    }

    @Test
    void sendsCsrfTokenDiscoveredFromHtmlMetaTag() throws IOException, InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executorService);
        server.createContext("/csrf", CsrfTokenFinderTest::sendCsrfPage);
        server.createContext("/submit", CsrfTokenFinderTest::sendSubmittedCsrfHeader);
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
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
