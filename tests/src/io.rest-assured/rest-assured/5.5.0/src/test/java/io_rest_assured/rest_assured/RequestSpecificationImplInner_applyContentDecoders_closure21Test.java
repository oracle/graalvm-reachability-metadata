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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.DecoderConfig.ContentDecoder.GZIP;
import static io.restassured.config.DecoderConfig.decoderConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyContentDecoders_closure21Test {
    @Test
    void configuredGzipContentDecoderIsAppliedToRequestAndResponse() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/compressed",
                RequestSpecificationImplInner_applyContentDecoders_closure21Test::sendGzipResponse);
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        try {
            Response response = given()
                    .config(config().decoderConfig(decoderConfig().contentDecoders(GZIP)))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/compressed");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).contains("accept-encoding=gzip");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void sendGzipResponse(HttpExchange exchange) throws IOException {
        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        byte[] responseBytes = gzip("accept-encoding=" + acceptEncoding);

        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
