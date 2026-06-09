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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestSpecificationImplInner_applyPathParamsAndSendRequest_closure33Test {
    @Test
    void orderedFiltersAreSortedBeforeSendingPathParameterizedRequest() throws Throwable {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/records", exchange -> handleRecords(exchange));
        server.setExecutor(executor);
        server.start();
        RestAssured.reset();

        List<String> invocationOrder = new ArrayList<>();

        try {
            Response response = given()
                    .filter(new RecordingOrderedFilter("late", 20, invocationOrder))
                    .filter(new RecordingOrderedFilter("early", 10, invocationOrder))
                    .baseUri("http://127.0.0.1")
                    .port(server.getAddress().getPort())
                    .when()
                    .get("/records/{recordId}", "42");

            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);
            assertThat(response.asString()).isEqualTo("record-42");
            assertThat(invocationOrder).containsExactly("early", "late");
        } finally {
            RestAssured.reset();
            server.stop(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void handleRecords(HttpExchange exchange) throws IOException {
        try {
            String requestPath = exchange.getRequestURI().getPath();
            String recordId = requestPath.substring(requestPath.lastIndexOf('/') + 1);
            byte[] responseBytes = ("record-" + recordId).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }

    private static final class RecordingOrderedFilter implements OrderedFilter {
        private final String name;
        private final int order;
        private final List<String> invocations;

        private RecordingOrderedFilter(String name, int order, List<String> invocations) {
            this.name = name;
            this.order = order;
            this.invocations = invocations;
        }

        @Override
        public Response filter(
                FilterableRequestSpecification requestSpec,
                FilterableResponseSpecification responseSpec,
                FilterContext ctx) {
            invocations.add(name);
            return ctx.next(requestSpec, responseSpec);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
