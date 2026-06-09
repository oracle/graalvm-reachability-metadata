/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.internal.http.CharsetExtractor;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CharsetExtractorTest {
    private static final String BODY = "Crème brûlée";

    @Test
    void generatedGroovyClassHelperResolvesClassName() throws Exception {
        Method classHelper = CharsetExtractor.class.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        String contentTypeExtractorClassName = String.join(
                ".", "io", "restassured", "internal", "http", "ContentTypeSubTypeExtractor");

        Class<?> resolvedClass = (Class<?>) classHelper.invoke(null, contentTypeExtractorClassName);

        assertEquals(contentTypeExtractorClassName, resolvedClass.getName());
    }

    @Test
    void decodesResponseBodyUsingCharsetDeclaredInContentType() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latin1", this::sendLatin1Response);
        server.setExecutor(executor);
        server.start();

        try {
            String decodedBody = given()
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/latin1")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .extract()
                    .asString();

            assertEquals(BODY, decodedBody);
        } finally {
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void sendLatin1Response(HttpExchange exchange) throws IOException {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/plain; charset=ISO-8859-1");
            byte[] body = BODY.getBytes(StandardCharsets.ISO_8859_1);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
