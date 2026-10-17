/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.LongString;
import com.rabbitmq.client.MapRpcServer;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.RpcClientParams;
import com.rabbitmq.client.RpcServer;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.StringRpcServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Serves request/reply RPC over RabbitMQ with `RpcClient` and the string and map
/// `RpcServer` variants.
///
/// The scenario is a small service tier: a text-normalising service and a price
/// quote service each listen on a named queue, and a client calls them over the
/// direct reply-to pseudo-queue against a real broker.
public class RequestReplyRpcTest {

    private static final String IMAGE = "rabbitmq:4.3";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final int CALL_TIMEOUT_MILLIS = 10_000;

    private static String containerId;

    private static ConnectionFactory factory;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5672", IMAGE);
        String mapping = docker("port", containerId, "5672/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(false);
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void stringServiceAnswersEachCallWithItsOwnReply() throws Exception {
        try (Connection connection = factory.newConnection("rpc-string-app")) {
            Channel serverChannel = connection.createChannel();
            String queue = "rpc.normalise";
            serverChannel.queueDeclare(queue, true, false, true, null);
            NormaliseService service = new NormaliseService(serverChannel, queue);
            assertThat(service.getQueueName()).isEqualTo(queue);
            CompletableFuture<ShutdownSignalException> serverLoop = serve(service);

            Channel clientChannel = connection.createChannel();
            RpcClient client = new RpcClient(new RpcClientParams()
                    .channel(clientChannel)
                    .exchange("")
                    .routingKey(queue)
                    .timeout(CALL_TIMEOUT_MILLIS));
            assertThat(client.getRoutingKey()).isEqualTo(queue);

            assertThat(client.stringCall("  Hello   Rabbit  ")).isEqualTo("hello rabbit");
            assertThat(client.stringCall("GraalVM Native Image")).isEqualTo("graalvm native image");

            AMQP.BasicProperties request = new AMQP.BasicProperties.Builder()
                    .contentType("text/plain")
                    .appId("rpc-string-app")
                    .build();
            RpcClient.Response response = client.doCall(request, bytes("  Mixed CASE  "));
            assertThat(text(response.getBody())).isEqualTo("mixed case");
            assertThat(response.getProperties().getCorrelationId()).isNotBlank();
            assertThat(response.getProperties().getHeaders().get("served-by").toString()).isEqualTo("normalise");
            assertThat(response.getEnvelope().getRoutingKey()).startsWith("amq.rabbitmq.reply-to");

            client.close();
            stop(service, serverLoop);
            assertThat(service.calls).isEqualTo(3);
        }
    }

    @Test
    void mapServiceExchangesStructuredTables() throws Exception {
        try (Connection connection = factory.newConnection("rpc-map-app")) {
            Channel serverChannel = connection.createChannel();
            String queue = serverChannel.queueDeclare().getQueue();
            QuoteService service = new QuoteService(serverChannel, queue);
            CompletableFuture<ShutdownSignalException> serverLoop = serve(service);

            RpcClient client = new RpcClient(new RpcClientParams()
                    .channel(connection.createChannel())
                    .exchange("")
                    .routingKey(queue)
                    .timeout(CALL_TIMEOUT_MILLIS));

            Map<String, Object> order = new HashMap<>();
            order.put("sku", "BOOK-42");
            order.put("quantity", 3);
            order.put("unitCents", 1250L);
            Map<String, Object> quote = client.mapCall(order);
            assertThat(quote.get("sku")).isInstanceOf(LongString.class);
            assertThat(quote.get("sku").toString()).isEqualTo("BOOK-42");
            assertThat(quote.get("totalCents")).isEqualTo(3750L);
            assertThat(quote.get("discounted")).isEqualTo(false);

            Map<String, Object> bulk = client.mapCall(new Object[] {"sku", "PEN-7", "quantity", 100, "unitCents", 90L});
            assertThat(bulk.get("sku").toString()).isEqualTo("PEN-7");
            assertThat(bulk.get("totalCents")).isEqualTo(8100L);
            assertThat(bulk.get("discounted")).isEqualTo(true);

            byte[] encoded = MapRpcServer.encode(Map.of("ping", 1));
            assertThat(MapRpcServer.decode(client.primitiveCall(encoded))).containsKey("error");

            client.close();
            stop(service, serverLoop);
        }
    }

    @Test
    void callWithoutServerTimesOut() throws Exception {
        try (Connection connection = factory.newConnection("rpc-timeout-app")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare().getQueue();
            RpcClient client = new RpcClient(new RpcClientParams()
                    .channel(channel)
                    .exchange("")
                    .routingKey(queue)
                    .timeout(500));

            assertThatThrownBy(() -> client.stringCall("anyone there?")).isInstanceOf(TimeoutException.class);
            assertThat(client.getContinuationMap()).isEmpty();
            assertThat(channel.messageCount(queue)).isEqualTo(1);
            client.close();
        }
    }

    /// Lower-cases text and collapses runs of whitespace, tagging each reply.
    private static final class NormaliseService extends StringRpcServer {

        private volatile int calls;

        NormaliseService(Channel channel, String queue) throws IOException {
            super(channel, queue);
        }

        @Override
        public String handleStringCall(String request, AMQP.BasicProperties replyProperties) {
            calls++;
            return request.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }

        @Override
        protected AMQP.BasicProperties postprocessReplyProperties(Delivery request,
                AMQP.BasicProperties.Builder builder) {
            return builder.headers(Map.of("served-by", "normalise")).build();
        }
    }

    /// Prices an order table, granting a ten percent discount on large orders.
    private static final class QuoteService extends MapRpcServer {

        QuoteService(Channel channel, String queue) throws IOException {
            super(channel, queue);
        }

        @Override
        public Map<String, Object> handleMapCall(Map<String, Object> request) {
            Map<String, Object> reply = new HashMap<>();
            if (!request.containsKey("sku")) {
                reply.put("error", "missing sku");
                return reply;
            }
            int quantity = (Integer) request.get("quantity");
            long unitCents = (Long) request.get("unitCents");
            boolean discounted = quantity >= 100;
            long total = quantity * unitCents;
            reply.put("sku", request.get("sku").toString());
            reply.put("totalCents", discounted ? total * 9 / 10 : total);
            reply.put("discounted", discounted);
            return reply;
        }
    }

    private static CompletableFuture<ShutdownSignalException> serve(RpcServer server) {
        CompletableFuture<ShutdownSignalException> loop = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                loop.complete(server.mainloop());
            } catch (IOException | RuntimeException e) {
                loop.completeExceptionally(e);
            }
        }, "rpc-server-" + server.getQueueName());
        thread.setDaemon(true);
        thread.start();
        return loop;
    }

    /// Stops a server the way an application shuts a service down: by closing its channel.
    private static void stop(RpcServer server, CompletableFuture<ShutdownSignalException> loop) throws Exception {
        server.terminateMainloop();
        server.getChannel().close();
        ShutdownSignalException signal = loop.get(10, TimeUnit.SECONDS);
        assertThat(signal).isNotNull();
        assertThat(signal.isInitiatedByApplication()).isTrue();
        assertThat(signal.isHardError()).isFalse();
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = factory.newConnection("startup-probe")) {
                if (connection.isOpen()) {
                    return;
                }
            } catch (IOException | TimeoutException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("RabbitMQ did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }
}
