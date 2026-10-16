/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.service.Discovery;
import io.nats.service.EndpointResponse;
import io.nats.service.Group;
import io.nats.service.InfoResponse;
import io.nats.service.PingResponse;
import io.nats.service.Service;
import io.nats.service.ServiceEndpoint;
import io.nats.service.ServiceMessage;
import io.nats.service.StatsResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Serves a pricing API as a NATS microservice through the jnats service framework and
 * discovers it with the framework's ping, info, and stats requests.
 *
 * A client sends JSON quote requests to the `pricing.quote` endpoint and receives a
 * priced quote, a standard error the handler chose, or the error the framework answers
 * when the handler throws; an operator then checks that the service is discoverable and
 * that its endpoint statistics count the requests and the thrown failure.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PricingMicroserviceTest {

    private static final String NATS_IMAGE = "nats:2.15.0";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final Map<String, Long> UNIT_PRICES_IN_CENTS = Map.of(
            "apple", 120L,
            "pear", 95L);

    private final ObjectMapper mapper = new ObjectMapper();

    private final String containerName = "real-use-jnats-" + UUID.randomUUID();

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":4222", NATS_IMAGE)
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();
        awaitServer();
    }

    @AfterAll
    void stopServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void pricesQuotesAndReportsItselfToDiscovery() throws Exception {
        try (Connection serviceConnection = connect(); Connection clientConnection = connect()) {
            Service service = pricingService(serviceConnection);
            CompletableFuture<Boolean> running = service.startService();

            Message priced = clientConnection.request("pricing.quote",
                    mapper.writeValueAsBytes(new QuoteRequest("apple", 3)), REQUEST_TIMEOUT);
            assertThat(priced.hasHeaders()).isFalse();
            Quote quote = mapper.readValue(priced.getData(), Quote.class);
            assertThat(quote).isEqualTo(new Quote("apple", 3, 360L, "EUR"));

            Message rejected = clientConnection.request("pricing.quote",
                    mapper.writeValueAsBytes(new QuoteRequest("durian", 1)), REQUEST_TIMEOUT);
            assertThat(rejected.getHeaders().getFirst(ServiceMessage.NATS_SERVICE_ERROR_CODE)).isEqualTo("404");
            assertThat(rejected.getHeaders().getFirst(ServiceMessage.NATS_SERVICE_ERROR))
                    .isEqualTo("Unknown product durian");

            Message failed = clientConnection.request("pricing.quote",
                    mapper.writeValueAsBytes(new QuoteRequest("pear", 0)), REQUEST_TIMEOUT);
            assertThat(failed.getHeaders().getFirst(ServiceMessage.NATS_SERVICE_ERROR_CODE)).isEqualTo("500");
            assertThat(failed.getHeaders().getFirst(ServiceMessage.NATS_SERVICE_ERROR))
                    .contains("Quantity must be positive");

            Discovery discovery = new Discovery(clientConnection, 1000, 5);
            List<PingResponse> pings = discovery.ping("pricing");
            assertThat(pings).singleElement().satisfies(ping -> {
                assertThat(ping.getId()).isEqualTo(service.getId());
                assertThat(ping.getVersion()).isEqualTo("1.2.0");
            });

            InfoResponse info = discovery.info("pricing", service.getId());
            assertThat(info.getDescription()).isEqualTo("Quotes basket prices");
            assertThat(info.getMetadata()).containsEntry("team", "checkout");
            assertThat(info.getSubjects()).containsExactly("pricing.quote");

            StatsResponse stats = discovery.stats("pricing", service.getId());
            assertThat(stats.getEndpointStats()).singleElement().satisfies(endpoint -> {
                assertThat(endpoint.getName()).isEqualTo("quote");
                assertThat(endpoint.getNumRequests()).isEqualTo(3);
                assertThat(endpoint.getNumErrors()).isEqualTo(1);
                assertThat(endpoint.getLastError()).contains("Quantity must be positive");
            });
            EndpointResponse localStats = service.getEndpointStats("quote");
            assertThat(localStats.getNumRequests()).isEqualTo(3);

            service.stop();
            assertThat(running.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Service pricingService(Connection connection) {
        ServiceEndpoint quoteEndpoint = ServiceEndpoint.builder()
                .group(new Group("pricing"))
                .endpointName("quote")
                .handler(message -> answerQuote(connection, message))
                .build();
        return Service.builder()
                .connection(connection)
                .name("pricing")
                .version("1.2.0")
                .description("Quotes basket prices")
                .metadata(Map.of("team", "checkout"))
                .addServiceEndpoint(quoteEndpoint)
                .build();
    }

    private void answerQuote(Connection connection, ServiceMessage message) {
        try {
            QuoteRequest request = mapper.readValue(message.getData(), QuoteRequest.class);
            if (request.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            Long unitPrice = UNIT_PRICES_IN_CENTS.get(request.product());
            if (unitPrice == null) {
                message.respondStandardError(connection, "Unknown product " + request.product(), 404);
                return;
            }
            Quote quote = new Quote(request.product(), request.quantity(), unitPrice * request.quantity(), "EUR");
            message.respond(connection, mapper.writeValueAsBytes(quote));
        } catch (IOException e) {
            message.respondStandardError(connection, "Malformed request", 400);
        }
    }

    private Connection connect() throws Exception {
        Options options = new Options.Builder()
                .server(serverUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .build();
        return Nats.connect(options);
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = connect()) {
                return;
            } catch (IOException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("NATS did not accept connections at " + serverUrl, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// A client's request for the price of some quantity of one product.
    record QuoteRequest(String product, int quantity) {
    }

    /// The priced answer the pricing endpoint returns.
    record Quote(String product, int quantity, long totalInCents, String currency) {
    }
}
