/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.codahale.metrics.MetricRegistry;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.impl.MicrometerMetricsCollector;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/// Collects client-side metrics through the Micrometer and Dropwizard Metrics
/// collectors plugged into a connection factory.
///
/// An application wires a `MetricsCollector` into its `ConnectionFactory` and
/// reads connection and channel gauges plus publish, confirm, return, consume,
/// ack, and reject counters from its monitoring registry while talking to a
/// real broker.
public class ClientMetricsTest {

    private static final String IMAGE = "rabbitmq:4.3";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static String containerId;

    private static int port;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5672", IMAGE);
        String mapping = docker("port", containerId, "5672/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        awaitBroker(newFactory());
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void micrometerRegistryReflectsMessagingTraffic() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMetricsCollector collector = new MicrometerMetricsCollector(registry, "orders.amqp",
                List.of(Tag.of("service", "order-service")));
        ConnectionFactory factory = newFactory();
        factory.setMetricsCollector(collector);

        try (Connection connection = factory.newConnection("metrics-micrometer")) {
            Channel publisher = connection.createChannel();
            Channel consumer = connection.createChannel();
            assertThat(registry.get("orders.amqp.connections").tag("service", "order-service").gauge().value())
                    .isEqualTo(1.0);
            assertThat(registry.get("orders.amqp.channels").gauge().value()).isEqualTo(2.0);

            String queue = publisher.queueDeclare().getQueue();
            // Waiting after each publish keeps the broker from folding confirms into one multiple ack.
            publisher.confirmSelect();
            CountDownLatch returned = new CountDownLatch(1);
            publisher.addReturnListener(message -> returned.countDown());
            for (int i = 0; i < 3; i++) {
                publisher.basicPublish("", queue, MessageProperties.TEXT_PLAIN, bytes("order " + i));
                publisher.waitForConfirmsOrDie(10_000);
            }
            publisher.basicPublish("", "no.such.queue", true, MessageProperties.TEXT_PLAIN, bytes("lost order"));
            publisher.waitForConfirmsOrDie(10_000);
            assertThat(returned.await(10, TimeUnit.SECONDS)).isTrue();

            consumeAndSettle(consumer, queue);

            assertThat(count(registry, "orders.amqp.published")).isEqualTo(4.0);
            assertThat(count(registry, "orders.amqp.acknowledged_published")).isEqualTo(4.0);
            assertThat(count(registry, "orders.amqp.not_acknowledged_published")).isZero();
            assertThat(count(registry, "orders.amqp.failed_to_publish")).isZero();
            assertThat(count(registry, "orders.amqp.consumed")).isEqualTo(3.0);
            assertThat(count(registry, "orders.amqp.acknowledged")).isEqualTo(1.0);
            assertThat(count(registry, "orders.amqp.rejected")).isEqualTo(2.0);
            awaitCount(() -> count(registry, "orders.amqp.unrouted_published"), 1.0);

            assertThat(collector.getPublishedMessages().count()).isEqualTo(4.0);
            assertThat(collector.getConsumedMessages().count()).isEqualTo(3.0);

            // Shutdown listeners decrement the gauges, so they may lag just behind close().
            consumer.close();
            awaitCount(() -> collector.getChannels().get(), 1.0);
            publisher.close();
            awaitCount(() -> registry.get("orders.amqp.channels").gauge().value(), 0.0);
        }
        awaitCount(() -> registry.get("orders.amqp.connections").gauge().value(), 0.0);
    }

    @Test
    void dropwizardRegistryReflectsMessagingTraffic() throws Exception {
        MetricRegistry registry = new MetricRegistry();
        StandardMetricsCollector collector = new StandardMetricsCollector(registry, "billing");
        ConnectionFactory factory = newFactory();
        factory.setMetricsCollector(collector);

        try (Connection connection = factory.newConnection("metrics-dropwizard")) {
            Channel publisher = connection.createChannel();
            Channel consumer = connection.createChannel();
            assertThat(registry.counter("billing.connections").getCount()).isEqualTo(1L);
            assertThat(registry.counter("billing.channels").getCount()).isEqualTo(2L);

            String queue = publisher.queueDeclare().getQueue();
            // Waiting after each publish keeps the broker from folding confirms into one multiple ack.
            publisher.confirmSelect();
            CountDownLatch returned = new CountDownLatch(1);
            publisher.addReturnListener(message -> returned.countDown());
            for (int i = 0; i < 3; i++) {
                publisher.basicPublish("", queue, MessageProperties.TEXT_PLAIN, bytes("invoice " + i));
                publisher.waitForConfirmsOrDie(10_000);
            }
            publisher.basicPublish("", "no.such.queue", true, MessageProperties.TEXT_PLAIN, bytes("lost invoice"));
            publisher.waitForConfirmsOrDie(10_000);
            assertThat(returned.await(10, TimeUnit.SECONDS)).isTrue();

            consumeAndSettle(consumer, queue);

            assertThat(registry.meter("billing.published").getCount()).isEqualTo(4L);
            assertThat(registry.meter("billing.publish_ack").getCount()).isEqualTo(4L);
            assertThat(registry.meter("billing.publish_nack").getCount()).isZero();
            assertThat(registry.meter("billing.failed_to_publish").getCount()).isZero();
            assertThat(registry.meter("billing.consumed").getCount()).isEqualTo(3L);
            assertThat(registry.meter("billing.acknowledged").getCount()).isEqualTo(1L);
            assertThat(registry.meter("billing.rejected").getCount()).isEqualTo(2L);
            awaitCount(() -> (double) registry.meter("billing.publish_unrouted").getCount(), 1.0);

            assertThat(collector.getMetricRegistry()).isSameAs(registry);
            assertThat(registry.getMeters()).containsKeys("billing.published", "billing.consumed");

            consumer.close();
            awaitCount(() -> collector.getChannels().getCount(), 1.0);
            publisher.close();
            awaitCount(() -> registry.counter("billing.channels").getCount(), 0.0);
        }
        awaitCount(() -> collector.getConnections().getCount(), 0.0);
    }

    /// Fetches the three messages on the queue, acknowledging the first, rejecting
    /// the second, and nacking the third without requeue.
    private static void consumeAndSettle(Channel channel, String queue) throws Exception {
        GetResponse first = awaitMessage(channel, queue);
        channel.basicAck(first.getEnvelope().getDeliveryTag(), false);
        GetResponse second = awaitMessage(channel, queue);
        channel.basicReject(second.getEnvelope().getDeliveryTag(), false);
        GetResponse third = awaitMessage(channel, queue);
        channel.basicNack(third.getEnvelope().getDeliveryTag(), false, false);
        assertThat(text(first.getBody())).endsWith(" 0");
        assertThat(text(third.getBody())).endsWith(" 2");
        assertThat(channel.basicGet(queue, false)).isNull();
    }

    private static ConnectionFactory newFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }

    private static double count(SimpleMeterRegistry registry, String name) {
        return registry.get(name).tag("service", "order-service").counter().count();
    }

    /// A metric read that can be polled until it reaches an expected value.
    @FunctionalInterface
    private interface MetricRead {
        double value();
    }

    private static void awaitCount(MetricRead read, double expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && read.value() != expected) {
            Thread.sleep(50);
        }
        assertThat(read.value()).isEqualTo(expected);
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

    private static void awaitBroker(ConnectionFactory factory) throws Exception {
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
