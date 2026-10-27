/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.Return;
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
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/// Routes events through topic, headers, and fanout exchanges, exchange-to-exchange
/// bindings, an alternate exchange, mandatory returns, and TTL dead-lettering.
///
/// The scenario is an order-events backbone: a topic exchange fans orders out to
/// regional queues, a headers exchange selects audit traffic by message headers,
/// unroutable events fall back to an alternate exchange or come back to the
/// publisher, and expired messages are dead-lettered into a parking queue.
public class ExchangeRoutingTest {

    private static final String IMAGE = "rabbitmq:4.3";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

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
    void topicAndExchangeBindingsRouteByPattern() throws Exception {
        try (Connection connection = factory.newConnection("order-router")) {
            Channel channel = connection.createChannel();
            String orders = "orders.topic";
            String everything = "orders.firehose";
            assertThat(channel.exchangeDeclare(orders, BuiltinExchangeType.TOPIC, true)).isNotNull();
            channel.exchangeDeclare(everything, BuiltinExchangeType.FANOUT, false, true, null);
            channel.exchangeBind(everything, orders, "#");

            String europe = declareBoundQueue(channel, orders, "order.eu.*");
            String created = declareBoundQueue(channel, orders, "order.*.created");
            String firehose = declareBoundQueue(channel, everything, "");

            BlockingQueue<String> firehoseEvents = new LinkedBlockingQueue<>();
            CountDownLatch cancelled = new CountDownLatch(1);
            DeliverCallback onDelivery = (tag, delivery) -> {
                firehoseEvents.add(delivery.getEnvelope().getExchange() + "|"
                        + delivery.getEnvelope().getRoutingKey() + "|" + text(delivery.getBody()));
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            CancelCallback onCancel = tag -> cancelled.countDown();
            String consumerTag = channel.basicConsume(firehose, false, onDelivery, onCancel);

            channel.basicPublish(orders, "order.eu.created", null, bytes("o-1"));
            channel.basicPublish(orders, "order.us.created", null, bytes("o-2"));
            channel.basicPublish(orders, "order.eu.shipped", null, bytes("o-3"));
            channel.basicPublish(orders, "invoice.eu.created.late", null, bytes("i-1"));

            assertThat(drain(channel, europe, 2)).containsExactly("o-1", "o-3");
            assertThat(drain(channel, created, 2)).containsExactly("o-1", "o-2");
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String event = firehoseEvents.poll(10, TimeUnit.SECONDS);
                assertThat(event).isNotNull();
                seen.add(event);
            }
            assertThat(seen).containsExactly(
                    "orders.topic|order.eu.created|o-1",
                    "orders.topic|order.us.created|o-2",
                    "orders.topic|order.eu.shipped|o-3",
                    "orders.topic|invoice.eu.created.late|i-1");

            channel.exchangeUnbind(everything, orders, "#");
            channel.basicPublish(orders, "order.eu.cancelled", null, bytes("o-4"));
            assertThat(drain(channel, europe, 1)).containsExactly("o-4");
            assertThat(firehoseEvents.poll(500, TimeUnit.MILLISECONDS)).isNull();

            channel.queueDelete(firehose);
            assertThat(cancelled.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(consumerTag).isNotBlank();
            channel.exchangeDelete(orders);
        }
    }

    @Test
    void headersExchangeMatchesAllOrAnyHeader() throws Exception {
        try (Connection connection = factory.newConnection("audit-router")) {
            Channel channel = connection.createChannel();
            String audit = "audit.headers";
            channel.exchangeDeclare(audit, BuiltinExchangeType.HEADERS, false, true, null);

            String critical = channel.queueDeclare().getQueue();
            Map<String, Object> allOf = new HashMap<>();
            allOf.put("x-match", "all");
            allOf.put("severity", "critical");
            allOf.put("source", "payments");
            channel.queueBind(critical, audit, "", allOf);

            String anyPayments = channel.queueDeclare().getQueue();
            Map<String, Object> anyOf = new HashMap<>();
            anyOf.put("x-match", "any");
            anyOf.put("source", "payments");
            anyOf.put("retention-days", 90);
            channel.queueBind(anyPayments, audit, "", anyOf);

            channel.basicPublish(audit, "", headers(Map.of("severity", "critical", "source", "payments")),
                    bytes("card declined burst"));
            channel.basicPublish(audit, "", headers(Map.of("severity", "info", "source", "payments")),
                    bytes("refund issued"));
            channel.basicPublish(audit, "", headers(Map.of("severity", "critical", "retention-days", 90)),
                    bytes("disk nearly full"));
            channel.basicPublish(audit, "", headers(Map.of("severity", "critical", "source", "search")),
                    bytes("index rebuilt"));

            assertThat(drain(channel, critical, 1)).containsExactly("card declined burst");
            assertThat(drain(channel, anyPayments, 3))
                    .containsExactly("card declined burst", "refund issued", "disk nearly full");
            assertThat(channel.messageCount(critical)).isZero();
            assertThat(channel.messageCount(anyPayments)).isZero();
        }
    }

    @Test
    void unroutableEventsFallBackOrReturnToPublisher() throws Exception {
        try (Connection connection = factory.newConnection("fallback-router")) {
            Channel channel = connection.createChannel();
            String unrouted = "events.unrouted";
            channel.exchangeDeclare(unrouted, BuiltinExchangeType.FANOUT, false, true, null);
            String parked = declareBoundQueue(channel, unrouted, "");

            String guarded = "events.guarded";
            channel.exchangeDeclare(guarded, BuiltinExchangeType.DIRECT, false, true,
                    Map.of("alternate-exchange", unrouted));
            String billing = declareBoundQueue(channel, guarded, "billing");

            String strict = "events.strict";
            channel.exchangeDeclare(strict, BuiltinExchangeType.DIRECT, false, true, null);

            BlockingQueue<Return> returns = new LinkedBlockingQueue<>();
            channel.addReturnListener(returns::add);
            channel.confirmSelect();

            channel.basicPublish(guarded, "billing", true, null, bytes("charge"));
            channel.basicPublish(guarded, "shipping", true, null, bytes("label"));
            AMQP.BasicProperties traced = new AMQP.BasicProperties.Builder()
                    .correlationId("trace-7")
                    .contentType("text/plain")
                    .build();
            channel.basicPublish(strict, "nowhere", true, traced, bytes("lost event"));
            channel.waitForConfirmsOrDie(10_000);

            assertThat(drain(channel, billing, 1)).containsExactly("charge");
            GetResponse fallback = awaitMessage(channel, parked);
            assertThat(text(fallback.getBody())).isEqualTo("label");
            assertThat(fallback.getEnvelope().getExchange()).isEqualTo(guarded);
            assertThat(fallback.getEnvelope().getRoutingKey()).isEqualTo("shipping");
            channel.basicAck(fallback.getEnvelope().getDeliveryTag(), false);

            Return returned = returns.poll(10, TimeUnit.SECONDS);
            assertThat(returned).isNotNull();
            assertThat(returned.getReplyCode()).isEqualTo(312);
            assertThat(returned.getReplyText()).isEqualTo("NO_ROUTE");
            assertThat(returned.getExchange()).isEqualTo(strict);
            assertThat(returned.getRoutingKey()).isEqualTo("nowhere");
            assertThat(returned.getProperties().getCorrelationId()).isEqualTo("trace-7");
            assertThat(text(returned.getBody())).isEqualTo("lost event");
            assertThat(returns.poll(300, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    @Test
    void expiredMessagesAreDeadLettered() throws Exception {
        try (Connection connection = factory.newConnection("dead-letter-router")) {
            Channel channel = connection.createChannel();
            String deadLetters = "orders.dlx";
            channel.exchangeDeclare(deadLetters, BuiltinExchangeType.DIRECT, false, true, null);
            String parking = declareBoundQueue(channel, deadLetters, "expired");

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("x-message-ttl", 200);
            arguments.put("x-dead-letter-exchange", deadLetters);
            arguments.put("x-dead-letter-routing-key", "expired");
            String pending = channel.queueDeclare("orders.pending", false, true, true, arguments).getQueue();

            channel.basicPublish("", pending, headers(Map.of("order-id", "o-99")), bytes("awaiting payment"));
            AMQP.BasicProperties shortLived = new AMQP.BasicProperties.Builder().expiration("50").build();
            channel.basicPublish("", pending, shortLived, bytes("already late"));

            GetResponse expired = awaitMessage(channel, parking);
            assertThat(text(expired.getBody())).isEqualTo("awaiting payment");
            assertThat(expired.getEnvelope().getRoutingKey()).isEqualTo("expired");
            Map<String, Object> deadHeaders = expired.getProps().getHeaders();
            assertThat(String.valueOf(deadHeaders.get("order-id"))).isEqualTo("o-99");
            assertThat(String.valueOf(deadHeaders.get("x-first-death-reason"))).isEqualTo("expired");
            assertThat(String.valueOf(deadHeaders.get("x-first-death-queue"))).isEqualTo(pending);
            assertThat(deadHeaders.get("x-death")).isInstanceOf(List.class);
            channel.basicAck(expired.getEnvelope().getDeliveryTag(), false);

            GetResponse late = awaitMessage(channel, parking);
            assertThat(text(late.getBody())).isEqualTo("already late");
            assertThat(late.getProps().getExpiration()).isNull();
            assertThat(String.valueOf(late.getProps().getHeaders().get("x-first-death-reason")))
                    .isEqualTo("expired");
            channel.basicAck(late.getEnvelope().getDeliveryTag(), false);

            assertThat(channel.messageCount(pending)).isZero();
            assertThat(channel.basicGet(parking, false)).isNull();
        }
    }

    private static String declareBoundQueue(Channel channel, String exchange, String routingKey)
            throws IOException {
        String queue = channel.queueDeclare().getQueue();
        channel.queueBind(queue, exchange, routingKey);
        return queue;
    }

    private static AMQP.BasicProperties headers(Map<String, Object> headers) {
        return new AMQP.BasicProperties.Builder().headers(headers).build();
    }

    private static List<String> drain(Channel channel, String queue, int count) throws Exception {
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GetResponse response = awaitMessage(channel, queue);
            bodies.add(text(response.getBody()));
            channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
        }
        return bodies;
    }

    private static GetResponse awaitMessage(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            GetResponse response = channel.basicGet(queue, false);
            if (response != null) {
                return response;
            }
            Thread.sleep(50);
        }
        throw new TimeoutException("No message arrived on " + queue);
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
