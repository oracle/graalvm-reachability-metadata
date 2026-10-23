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
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/// Distributes persistent work items over a durable queue to competing consumers
/// that acknowledge, reject, and requeue deliveries by hand.
///
/// The scenario is the classic RabbitMQ work queue: a producer publishes
/// persistent tasks with publisher confirms, and two workers with a prefetch of
/// one share the load against a real broker.
public class WorkQueueTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final int TASK_COUNT = 20;

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
    void competingWorkersProcessEveryTaskExactlyOnce() throws Exception {
        try (Connection connection = factory.newConnection("work-queue-app")) {
            String queue = "tasks.process";
            Channel producer = connection.createChannel();
            AMQP.Queue.DeclareOk declared = producer.queueDeclare(queue, true, false, false, null);
            assertThat(declared.getQueue()).isEqualTo(queue);

            producer.confirmSelect();
            for (int i = 0; i < TASK_COUNT; i++) {
                Map<String, Object> headers = new HashMap<>();
                headers.put("task-index", i);
                AMQP.BasicProperties properties = MessageProperties.PERSISTENT_TEXT_PLAIN.builder()
                        .messageId("task-" + i)
                        .headers(headers)
                        .build();
                producer.basicPublish("", queue, properties, ("resize image " + i).getBytes(StandardCharsets.UTF_8));
            }
            producer.waitForConfirmsOrDie(10_000);

            CountDownLatch done = new CountDownLatch(TASK_COUNT);
            Map<String, String> processedBy = new ConcurrentHashMap<>();
            Set<String> redeliveries = ConcurrentHashMap.newKeySet();
            List<Channel> workerChannels = new ArrayList<>();
            for (String name : List.of("worker-a", "worker-b")) {
                Channel channel = connection.createChannel();
                channel.basicQos(1);
                channel.basicConsume(queue, false, name, new Worker(channel, name, processedBy, redeliveries, done));
                workerChannels.add(channel);
            }

            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(processedBy).hasSize(TASK_COUNT);
            for (int i = 0; i < TASK_COUNT; i++) {
                assertThat(processedBy).containsKey("task-" + i + ":resize image " + i);
            }
            assertThat(processedBy.values()).contains("worker-a", "worker-b");
            assertThat(redeliveries).isEmpty();

            for (Channel channel : workerChannels) {
                channel.close();
            }
            AMQP.Queue.DeclareOk drained = producer.queueDeclarePassive(queue);
            assertThat(drained.getMessageCount()).isZero();
            assertThat(producer.queueDelete(queue).getMessageCount()).isZero();
        }
    }

    @Test
    void rejectedTasksAreRequeuedOrDiscarded() throws Exception {
        try (Connection connection = factory.newConnection("work-queue-retry")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare().getQueue();
            assertThat(queue).startsWith("amq.gen-");

            channel.basicPublish("", queue, MessageProperties.TEXT_PLAIN, bytes("flaky task"));
            channel.basicPublish("", queue, MessageProperties.TEXT_PLAIN, bytes("poison task"));

            GetResponse flaky = awaitMessage(channel, queue);
            assertThat(text(flaky.getBody())).isEqualTo("flaky task");
            assertThat(flaky.getEnvelope().isRedeliver()).isFalse();
            assertThat(flaky.getProps().getContentType()).isEqualTo("text/plain");
            channel.basicNack(flaky.getEnvelope().getDeliveryTag(), false, true);

            GetResponse retried = awaitMessage(channel, queue);
            assertThat(text(retried.getBody())).isEqualTo("flaky task");
            assertThat(retried.getEnvelope().isRedeliver()).isTrue();
            channel.basicAck(retried.getEnvelope().getDeliveryTag(), false);

            GetResponse poison = awaitMessage(channel, queue);
            assertThat(text(poison.getBody())).isEqualTo("poison task");
            channel.basicReject(poison.getEnvelope().getDeliveryTag(), false);

            assertThat(channel.basicGet(queue, false)).isNull();
            assertThat(channel.messageCount(queue)).isZero();
        }
    }

    /// A worker that records which task it handled and acknowledges it once done.
    private static final class Worker extends DefaultConsumer {

        private final String name;

        private final Map<String, String> processedBy;

        private final Set<String> redeliveries;

        private final CountDownLatch done;

        Worker(Channel channel, String name, Map<String, String> processedBy, Set<String> redeliveries,
                CountDownLatch done) {
            super(channel);
            this.name = name;
            this.processedBy = processedBy;
            this.redeliveries = redeliveries;
            this.done = done;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                byte[] body) throws IOException {
            String task = properties.getMessageId() + ":" + text(body);
            if (envelope.isRedeliver()) {
                redeliveries.add(task);
            }
            Object index = properties.getHeaders().get("task-index");
            if (properties.getDeliveryMode() == 2 && properties.getMessageId().equals("task-" + index)) {
                processedBy.put(task, name);
            }
            getChannel().basicAck(envelope.getDeliveryTag(), false);
            done.countDown();
        }
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
