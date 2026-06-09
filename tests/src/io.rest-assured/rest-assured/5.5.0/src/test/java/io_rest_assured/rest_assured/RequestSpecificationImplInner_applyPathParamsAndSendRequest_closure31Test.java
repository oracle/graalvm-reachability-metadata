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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.FilterContext;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.LogConfig.logConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure31Test {
    @Test
    void validationFailureLoggingConfigurationRecognizesExistingResponseLoggingFilter() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages", exchange -> handleMessages(exchange));
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        ByteArrayOutputStream responseLogOutput = new ByteArrayOutputStream();
        PrintStream responseLogStream = new PrintStream(responseLogOutput, true, StandardCharsets.UTF_8);
        AtomicBoolean responseLoggingFilterWasInvoked = new AtomicBoolean();
        RestAssuredConfig config = RestAssuredConfig.config().logConfig(
                logConfig().defaultStream(responseLogStream).enableLoggingOfRequestAndResponseIfValidationFails());

        try {
            Response response = given()
                    .config(config)
                    .filter(new RecordingResponseLoggingFilter(responseLogStream, responseLoggingFilterWasInvoked))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/messages/{messageId}", "42")
                    .then()
                    .statusCode(HttpURLConnection.HTTP_OK)
                    .extract()
                    .response();

            assertThat(response.asString()).isEqualTo("message-42");
            assertThat(responseLoggingFilterWasInvoked).isTrue();
            assertThat(responseLogOutput.toString(StandardCharsets.UTF_8)).contains("message-42");
        } finally {
            responseLogStream.close();
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void handleMessages(HttpExchange exchange) throws IOException {
        try {
            byte[] responseBytes = "message-42".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class RecordingResponseLoggingFilter extends ResponseLoggingFilter {
        private final AtomicBoolean invoked;

        private RecordingResponseLoggingFilter(PrintStream printStream, AtomicBoolean invoked) {
            super(printStream);
            this.invoked = invoked;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            invoked.set(true);
            return super.filter(requestSpec, responseSpec, ctx);
        }
    }
}
