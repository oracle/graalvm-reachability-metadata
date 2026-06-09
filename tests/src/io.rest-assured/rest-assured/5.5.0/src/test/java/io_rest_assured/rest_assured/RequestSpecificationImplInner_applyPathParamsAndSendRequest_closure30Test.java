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
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.config.LogConfig.logConfig;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure30Test {
    @Test
    void validationFailureLoggingConfigurationChecksExistingNonRequestLoggingFilters() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items", exchange -> handleItems(exchange));
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        AtomicBoolean customFilterWasInvoked = new AtomicBoolean();
        ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(logOutput, true, StandardCharsets.UTF_8);
        RestAssuredConfig config = RestAssuredConfig.config().logConfig(
                logConfig().defaultStream(logStream).enableLoggingOfRequestAndResponseIfValidationFails());

        try {
            Response response = given()
                    .config(config)
                    .filter(new RecordingFilter(customFilterWasInvoked))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/items/{itemId}", "42");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("item-42");
            assertThat(customFilterWasInvoked).isTrue();
        } finally {
            logStream.close();
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void handleItems(HttpExchange exchange) throws IOException {
        try {
            byte[] responseBytes = "item-42".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class RecordingFilter implements Filter {
        private final AtomicBoolean invoked;

        private RecordingFilter(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            invoked.set(true);
            return ctx.next(requestSpec, responseSpec);
        }
    }
}
