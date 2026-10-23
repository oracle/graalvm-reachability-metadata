/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.impl.nio.NioParams;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs connections over the non-blocking NIO transport, configured from an
/// externalised properties map and from an AMQP URI, including broker
/// `connection.blocked` / `connection.unblocked` notifications.
///
/// An application typically keeps its broker settings in a configuration file
/// under a `rabbitmq.` prefix, or in a single `amqp://` URI; this scenario feeds
/// both into `ConnectionFactory`, switches to NIO with small buffers and a shared
/// IO thread pool, and moves large, frame-split messages through a real broker.
public class NioTransportTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static String containerId;

    private static int port;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5672", IMAGE);
        String mapping = docker("port", containerId, "5672/tcp").lines().findFirst().orElseThrow();
        port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void propertiesConfiguredNioConnectionMovesFrameSplitMessages() throws Exception {
        Map<String, String> settings = new HashMap<>();
        settings.put("rabbitmq.host", "127.0.0.1");
        settings.put("rabbitmq.port", String.valueOf(port));
        settings.put("rabbitmq.username", "guest");
        settings.put("rabbitmq.password", "guest");
        settings.put("rabbitmq.virtual.host", "/");
        settings.put("rabbitmq.connection.heartbeat", "15");
        settings.put("rabbitmq.connection.frame.max", "8192");
        settings.put("rabbitmq.connection.timeout", "10000");
        settings.put("rabbitmq.connection.recovery.enabled", "false");
        settings.put("rabbitmq.client.properties.application", "inventory-sync");
        settings.put("rabbitmq.use.nio", "true");
        settings.put("rabbitmq.nio.nb.io.threads", "2");
        settings.put("rabbitmq.nio.read.byte.buffer.size", "4096");
        settings.put("rabbitmq.nio.write.byte.buffer.size", "4096");
        settings.put("rabbitmq.nio.write.queue.capacity", "64");
        settings.put("unrelated.setting", "ignored");

        ConnectionFactory factory = new ConnectionFactory().load(settings);
        assertThat(factory.getHost()).isEqualTo("127.0.0.1");
        assertThat(factory.getPort()).isEqualTo(port);
        assertThat(factory.getRequestedHeartbeat()).isEqualTo(15);
        assertThat(factory.getRequestedFrameMax()).isEqualTo(8192);
        assertThat(factory.isAutomaticRecoveryEnabled()).isFalse();
        assertThat(factory.getClientProperties()).containsEntry("application", "inventory-sync");
        NioParams nioParams = factory.getNioParams();
        assertThat(nioParams.getNbIoThreads()).isEqualTo(2);
        assertThat(nioParams.getReadByteBufferSize()).isEqualTo(4096);
        assertThat(nioParams.getWriteByteBufferSize()).isEqualTo(4096);
        assertThat(nioParams.getWriteQueueCapacity()).isEqualTo(64);

        try (Connection connection = factory.newConnection("inventory-sync-nio")) {
            assertThat(connection.getHeartbeat()).isEqualTo(15);
            assertThat(connection.getFrameMax()).isEqualTo(8192);
            assertThat(connection.getClientProvidedName()).isEqualTo("inventory-sync-nio");
            assertThat(connection.getServerProperties()).containsKeys("product", "version", "capabilities");
            assertThat(connection.getServerProperties().get("product").toString()).isEqualTo("RabbitMQ");

            String clientProperties = awaitBrokerConnection("inventory-sync-nio");
            assertThat(clientProperties).contains("inventory-sync");

            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare("inventory.snapshots", false, true, true, null).getQueue();
            channel.confirmSelect();

            Random random = new Random(42);
            List<byte[]> payloads = new ArrayList<>();
            for (int size : new int[] {10, 8192, 100_000, 300_000}) {
                byte[] payload = new byte[size];
                random.nextBytes(payload);
                payloads.add(payload);
                AMQP.BasicProperties properties = MessageProperties.MINIMAL_BASIC.builder()
                        .contentType("application/octet-stream")
                        .messageId("snapshot-" + size)
                        .build();
                channel.basicPublish("", queue, properties, payload);
            }
            channel.waitForConfirmsOrDie(10_000);

            BlockingQueue<Delivery> received = new LinkedBlockingQueue<>();
            channel.basicConsume(queue, true, new Collector(channel, received));
            for (byte[] expected : payloads) {
                Delivery delivery = received.poll(10, TimeUnit.SECONDS);
                assertThat(delivery).isNotNull();
                assertThat(delivery.messageId()).isEqualTo("snapshot-" + expected.length);
                assertThat(delivery.contentType()).isEqualTo("application/octet-stream");
                assertThat(Arrays.equals(delivery.body(), expected)).isTrue();
            }
        }
    }

    @Test
    void uriConfiguredConnectionsShareOneNioThread() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri("amqp://guest:guest@127.0.0.1:" + port + "/%2F?heartbeat=5&channel_max=4"
                + "&connection_timeout=10000");
        assertThat(factory.getVirtualHost()).isEqualTo("/");
        assertThat(factory.getRequestedHeartbeat()).isEqualTo(5);
        assertThat(factory.getRequestedChannelMax()).isEqualTo(4);
        assertThat(factory.getConnectionTimeout()).isEqualTo(10_000);
        factory.setAutomaticRecoveryEnabled(false);

        List<String> ioThreads = new CopyOnWriteArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "orders-nio-" + counter.incrementAndGet());
            ioThreads.add(thread.getName());
            return thread;
        };
        factory.useNio();
        factory.setNioParams(new NioParams().setNbIoThreads(1).setThreadFactory(threadFactory));

        try (Connection publisher = factory.newConnection("orders-publisher");
                Connection consumer = factory.newConnection("orders-consumer")) {
            assertThat(publisher.getChannelMax()).isEqualTo(4);
            assertThat(publisher.getHeartbeat()).isEqualTo(5);
            List<Channel> channels = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Channel channel = publisher.createChannel();
                assertThat(channel).isNotNull();
                assertThat(channel.getChannelNumber()).isBetween(1, 4);
                channels.add(channel);
            }
            assertThat(publisher.createChannel()).isNull();

            Channel consumerChannel = consumer.createChannel();
            String queue = consumerChannel.queueDeclare().getQueue();
            int perChannel = 25;
            CountDownLatch delivered = new CountDownLatch(perChannel * channels.size());
            List<String> bodies = new CopyOnWriteArrayList<>();
            consumerChannel.basicConsume(queue, true, new DefaultConsumer(consumerChannel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                        byte[] body) {
                    bodies.add(new String(body, StandardCharsets.UTF_8));
                    delivered.countDown();
                }
            });

            for (Channel channel : channels) {
                channel.confirmSelect();
                for (int i = 0; i < perChannel; i++) {
                    channel.basicPublish("", queue, null,
                            ("order " + channel.getChannelNumber() + "-" + i).getBytes(StandardCharsets.UTF_8));
                }
            }
            for (Channel channel : channels) {
                channel.waitForConfirmsOrDie(10_000);
            }

            assertThat(delivered.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(bodies).hasSize(100).doesNotHaveDuplicates();
            for (Channel channel : channels) {
                assertThat(bodies).contains("order " + channel.getChannelNumber() + "-0",
                        "order " + channel.getChannelNumber() + "-" + (perChannel - 1));
            }
            assertThat(ioThreads).containsExactly("orders-nio-1");
        }
    }

    @Test
    void nioConnectionReportsBrokerResourceAlarms() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri("amqp://guest:guest@127.0.0.1:" + port);
        factory.setAutomaticRecoveryEnabled(false);
        factory.useNio();

        try (Connection connection = factory.newConnection("alarm-aware-app")) {
            BlockingQueue<String> events = new LinkedBlockingQueue<>();
            connection.addBlockedListener(new BlockedListener() {
                @Override
                public void handleBlocked(String reason) {
                    events.add("blocked: " + reason);
                }

                @Override
                public void handleUnblocked() {
                    events.add("unblocked");
                }
            });
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare().getQueue();

            docker("exec", containerId, "rabbitmqctl", "set_vm_memory_high_watermark", "0");
            try {
                channel.basicPublish("", queue, null, "held back".getBytes(StandardCharsets.UTF_8));
                String blocked = events.poll(30, TimeUnit.SECONDS);
                assertThat(blocked).isNotNull().startsWith("blocked: ").contains("memory");
            } finally {
                docker("exec", containerId, "rabbitmqctl", "set_vm_memory_high_watermark", "0.4");
            }
            assertThat(events.poll(30, TimeUnit.SECONDS)).isEqualTo("unblocked");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (channel.messageCount(queue) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(channel.messageCount(queue)).isEqualTo(1);
            assertThat(new String(channel.basicGet(queue, true).getBody(), StandardCharsets.UTF_8))
                    .isEqualTo("held back");
        }
    }

    /// A message as the consumer observed it.
    private record Delivery(String messageId, String contentType, byte[] body) {
    }

    /// A consumer that hands every delivery to the test thread.
    private static final class Collector extends DefaultConsumer {

        private final BlockingQueue<Delivery> received;

        Collector(Channel channel, BlockingQueue<Delivery> received) {
            super(channel);
            this.received = received;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                byte[] body) {
            received.add(new Delivery(properties.getMessageId(), properties.getContentType(), body));
        }
    }

    private static String awaitBrokerConnection(String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            String listing = docker("exec", containerId, "rabbitmqctl", "list_connections", "-q",
                    "client_properties");
            for (String line : listing.lines().toList()) {
                if (line.contains("\"" + name + "\"")) {
                    return line;
                }
            }
            Thread.sleep(250);
        }
        throw new TimeoutException("The broker did not list connection " + name);
    }

    private static void awaitBroker() throws Exception {
        ConnectionFactory probe = new ConnectionFactory();
        probe.setHost("127.0.0.1");
        probe.setPort(port);
        probe.setAutomaticRecoveryEnabled(false);
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = probe.newConnection("startup-probe")) {
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
}
