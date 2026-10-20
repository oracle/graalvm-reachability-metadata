/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Declares and uses the broker's queue types: quorum queues with delivery limits,
/// streams replayed from an offset, classic priority queues, and consumer cancel
/// notifications when a queue disappears.
///
/// Each queue type is selected through `queueDeclare` arguments, the way an
/// application opts into replicated or append-only storage against a real broker.
public class QueueTypesTest {

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
    void quorumQueueDeadLettersPoisonMessageAfterDeliveryLimit() throws Exception {
        try (Connection connection = factory.newConnection("quorum-orders")) {
            Channel channel = connection.createChannel();
            channel.exchangeDeclare("orders.dlx", BuiltinExchangeType.FANOUT, true);
            channel.queueDeclare("orders.poison", true, false, false, null);
            channel.queueBind("orders.poison", "orders.dlx", "");

            Map<String, Object> arguments = Map.of(
                    "x-queue-type", "quorum",
                    "x-delivery-limit", 2,
                    "x-dead-letter-exchange", "orders.dlx");
            AMQP.Queue.DeclareOk declared = channel.queueDeclare("orders.quorum", true, false, false, arguments);
            assertThat(declared.getQueue()).isEqualTo("orders.quorum");

            channel.confirmSelect();
            AMQP.BasicProperties properties = MessageProperties.PERSISTENT_TEXT_PLAIN.builder()
                    .messageId("order-17")
                    .build();
            channel.basicPublish("", "orders.quorum", properties, bytes("malformed order"));
            channel.waitForConfirmsOrDie(10_000);

            // A worker that crashes mid-task leaves its delivery unacknowledged; the quorum
            // queue counts each such return until the delivery limit dead-letters the order.
            List<Object> deliveryCounts = new ArrayList<>();
            Channel worker = connection.createChannel();
            GetResponse response = awaitMessage(worker, "orders.quorum");
            while (response != null) {
                assertThat(text(response.getBody())).isEqualTo("malformed order");
                Map<String, Object> headers = response.getProps().getHeaders();
                deliveryCounts.add(headers == null ? null : headers.get("x-delivery-count"));
                worker.close();
                worker = connection.createChannel();
                response = pollOnce(worker, "orders.quorum");
            }
            worker.close();
            assertThat(deliveryCounts).hasSize(3);
            assertThat(deliveryCounts.get(1)).isEqualTo(1L);
            assertThat(deliveryCounts.get(2)).isEqualTo(2L);

            GetResponse deadLettered = awaitMessage(channel, "orders.poison");
            assertThat(deadLettered.getProps().getMessageId()).isEqualTo("order-17");
            assertThat(text(deadLettered.getBody())).isEqualTo("malformed order");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deaths = (List<Map<String, Object>>) deadLettered.getProps()
                    .getHeaders().get("x-death");
            assertThat(deaths).hasSize(1);
            assertThat(deaths.get(0).get("queue").toString()).isEqualTo("orders.quorum");
            assertThat(deaths.get(0).get("reason").toString()).isEqualTo("delivery_limit");
            channel.basicAck(deadLettered.getEnvelope().getDeliveryTag(), false);

            Channel mismatched = connection.createChannel();
            assertThatThrownBy(() -> mismatched.queueDeclare("orders.quorum", true, false, false, null))
                    .isInstanceOf(IOException.class)
                    .getCause()
                    .isInstanceOfSatisfying(ShutdownSignalException.class, signal -> {
                        AMQP.Channel.Close close = (AMQP.Channel.Close) signal.getReason();
                        assertThat(close.getReplyCode()).isEqualTo(AMQP.PRECONDITION_FAILED);
                        assertThat(close.getReplyText()).contains("inequivalent arg");
                    });
            assertThat(mismatched.isOpen()).isFalse();

            channel.queueDelete("orders.quorum");
            channel.queueDelete("orders.poison");
            channel.exchangeDelete("orders.dlx");
        }
    }

    @Test
    void streamReplaysEventsFromRequestedOffset() throws Exception {
        try (Connection connection = factory.newConnection("stream-audit")) {
            Channel producer = connection.createChannel();
            producer.queueDeclare("audit.events", true, false, false, Map.of("x-queue-type", "stream"));
            producer.confirmSelect();
            for (int i = 0; i < 10; i++) {
                producer.basicPublish("", "audit.events", MessageProperties.PERSISTENT_TEXT_PLAIN,
                        bytes("event-" + i));
            }
            producer.waitForConfirmsOrDie(10_000);

            List<StreamEvent> all = readStream(connection, "first", 10);
            assertThat(all).extracting(StreamEvent::body)
                    .containsExactly("event-0", "event-1", "event-2", "event-3", "event-4",
                            "event-5", "event-6", "event-7", "event-8", "event-9");
            for (int i = 1; i < all.size(); i++) {
                assertThat(all.get(i).offset()).isGreaterThan(all.get(i - 1).offset());
            }

            long resumeFrom = all.get(6).offset();
            List<StreamEvent> tail = readStream(connection, resumeFrom, 4);
            assertThat(tail).extracting(StreamEvent::body)
                    .containsExactly("event-6", "event-7", "event-8", "event-9");
            assertThat(tail.get(0).offset()).isEqualTo(resumeFrom);

            producer.queueDelete("audit.events");
        }
    }

    @Test
    void priorityQueueDeliversMostUrgentMessageFirst() throws Exception {
        try (Connection connection = factory.newConnection("priority-alerts")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare("alerts.priority", true, false, true,
                    Map.of("x-max-priority", 10)).getQueue();
            channel.confirmSelect();
            publishWithPriority(channel, queue, 1, "disk 70% full");
            publishWithPriority(channel, queue, 9, "datacenter on fire");
            publishWithPriority(channel, queue, 5, "latency degraded");
            channel.waitForConfirmsOrDie(10_000);

            List<String> order = new ArrayList<>();
            List<Integer> priorities = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                GetResponse response = awaitMessage(channel, queue);
                order.add(text(response.getBody()));
                priorities.add(response.getProps().getPriority());
                channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            }
            assertThat(order).containsExactly("datacenter on fire", "latency degraded", "disk 70% full");
            assertThat(priorities).containsExactly(9, 5, 1);
        }
    }

    @Test
    void consumerIsNotifiedWhenItsQueueIsDeleted() throws Exception {
        try (Connection connection = factory.newConnection("cancel-notify")) {
            Channel consumerChannel = connection.createChannel();
            String queue = consumerChannel.queueDeclare("jobs.ephemeral", true, false, false, null).getQueue();
            CancelAwareConsumer consumer = new CancelAwareConsumer(consumerChannel);
            String tag = consumerChannel.basicConsume(queue, false, "jobs-consumer", consumer);
            assertThat(tag).isEqualTo("jobs-consumer");
            assertThat(consumerChannel.consumerCount(queue)).isEqualTo(1);

            Channel admin = connection.createChannel();
            admin.queueDelete(queue);

            assertThat(consumer.cancelled.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(consumer.cancelledTags).containsExactly("jobs-consumer");
            assertThat(consumerChannel.isOpen()).isTrue();
        }
    }

    private static List<StreamEvent> readStream(Connection connection, Object offset, int expected)
            throws Exception {
        Channel channel = connection.createChannel();
        channel.basicQos(100);
        CountDownLatch received = new CountDownLatch(expected);
        List<StreamEvent> events = new CopyOnWriteArrayList<>();
        String tag = channel.basicConsume("audit.events", false, Map.of("x-stream-offset", offset),
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope,
                            AMQP.BasicProperties properties, byte[] body) throws IOException {
                        long position = (Long) properties.getHeaders().get("x-stream-offset");
                        events.add(new StreamEvent(position, text(body)));
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                        received.countDown();
                    }
                });
        assertThat(received.await(15, TimeUnit.SECONDS)).isTrue();
        channel.basicCancel(tag);
        channel.close();
        return events.subList(0, expected);
    }

    private static void publishWithPriority(Channel channel, String queue, int priority, String body)
            throws IOException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .priority(priority)
                .build();
        channel.basicPublish("", queue, properties, bytes(body));
    }

    /// An event read back from a stream together with its position in the log.
    private record StreamEvent(long offset, String body) {
    }

    /// A consumer that records server-initiated cancellations.
    private static final class CancelAwareConsumer extends DefaultConsumer {

        private final CountDownLatch cancelled = new CountDownLatch(1);

        private final List<String> cancelledTags = new CopyOnWriteArrayList<>();

        CancelAwareConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleCancel(String consumerTag) {
            cancelledTags.add(consumerTag);
            cancelled.countDown();
        }
    }

    private static GetResponse pollOnce(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            GetResponse response = channel.basicGet(queue, false);
            if (response != null) {
                return response;
            }
            Thread.sleep(50);
        }
        return null;
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
