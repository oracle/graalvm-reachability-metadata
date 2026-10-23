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
import com.rabbitmq.client.Method;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.RecoverableConnection;
import com.rabbitmq.client.RecoveryListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// Survives a broker-forced connection close through automatic connection and
/// topology recovery.
///
/// The scenario is an order-event listener that keeps an exclusive server-named
/// queue bound to a topic exchange; an operator closes every client connection
/// with `rabbitmqctl`, and the client reconnects, redeclares the queue under a
/// new name, rebinds it, restores confirm mode and the consumer, and keeps
/// delivering events.
public class ConnectionRecoveryTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final String EXCHANGE = "orders.events";

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
    void forcedCloseRecoversConnectionTopologyAndConsumer() throws Exception {
        ConnectionFactory factory = recoveringFactory();
        try (Connection connection = factory.newConnection("order-listener")) {
            assertThat(connection).isInstanceOf(RecoverableConnection.class);
            RecoveryEvents connectionEvents = new RecoveryEvents();
            ((Recoverable) connection).addRecoveryListener(connectionEvents);

            AtomicReference<ShutdownSignalException> shutdown = new AtomicReference<>();
            connection.addShutdownListener(shutdown::set);

            AutorecoveringConnection autorecovering = (AutorecoveringConnection) connection;
            List<String> renamedQueues = new CopyOnWriteArrayList<>();
            autorecovering.addQueueRecoveryListener((oldName, newName) -> renamedQueues.add(oldName + "->" + newName));
            List<String> recoveredConsumers = new CopyOnWriteArrayList<>();
            autorecovering.addConsumerRecoveryListener((oldTag, newTag) -> recoveredConsumers.add(newTag));

            Channel channel = connection.createChannel();
            RecoveryEvents channelEvents = new RecoveryEvents();
            ((Recoverable) channel).addRecoveryListener(channelEvents);
            channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, false, false, null);
            String queue = channel.queueDeclare().getQueue();
            channel.queueBind(queue, EXCHANGE, "order.*");
            channel.basicQos(5);
            channel.confirmSelect();

            OrderListener listener = new OrderListener(channel);
            String consumerTag = channel.basicConsume(queue, false, "order-listener-1", listener);
            assertThat(consumerTag).isEqualTo("order-listener-1");

            publish(channel, "order.created", "order 1001 created");
            assertThat(listener.next()).isEqualTo("order.created:order 1001 created");

            docker("exec", containerId, "rabbitmqctl", "close_all_connections", "planned maintenance");

            assertThat(connectionEvents.started.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(connectionEvents.recovered.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(channelEvents.recovered.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(connection.isOpen()).isTrue();
            assertThat(channel.isOpen()).isTrue();

            ShutdownSignalException cause = shutdown.get();
            assertThat(cause).isNotNull();
            assertThat(cause.isHardError()).isTrue();
            assertThat(cause.isInitiatedByApplication()).isFalse();
            Method reason = cause.getReason();
            assertThat(reason).isInstanceOf(AMQP.Connection.Close.class);
            AMQP.Connection.Close close = (AMQP.Connection.Close) reason;
            assertThat(close.getReplyCode()).isEqualTo(AMQP.CONNECTION_FORCED);
            assertThat(close.getReplyText()).contains("planned maintenance");

            assertThat(renamedQueues).hasSize(1);
            String recoveredQueue = renamedQueues.get(0).substring(renamedQueues.get(0).indexOf("->") + 2);
            assertThat(renamedQueues.get(0)).startsWith(queue + "->amq.gen-");
            assertThat(recoveredQueue).isNotEqualTo(queue);
            assertThat(recoveredConsumers).containsExactly(consumerTag);
            assertThat(autorecovering.getRecordedQueues()).containsKey(recoveredQueue);
            assertThat(autorecovering.getRecordedConsumers()).containsKey(consumerTag);

            publish(channel, "order.shipped", "order 1001 shipped");
            assertThat(listener.next()).isEqualTo("order.shipped:order 1001 shipped");
            publish(channel, "invoice.paid", "invoice 77 paid");
            publish(channel, "order.delivered", "order 1001 delivered");
            assertThat(listener.next()).isEqualTo("order.delivered:order 1001 delivered");

            AMQP.Queue.DeclareOk passive = channel.queueDeclarePassive(recoveredQueue);
            assertThat(passive.getConsumerCount()).isEqualTo(1);
            channel.exchangeDelete(EXCHANGE);
        }
    }

    @Test
    void applicationCloseDoesNotTriggerRecovery() throws Exception {
        ConnectionFactory factory = recoveringFactory();
        Connection connection = factory.newConnection("short-lived-client");
        RecoveryEvents events = new RecoveryEvents();
        ((Recoverable) connection).addRecoveryListener(events);
        AtomicReference<ShutdownSignalException> shutdown = new AtomicReference<>();
        connection.addShutdownListener(shutdown::set);

        Channel channel = connection.createChannel();
        String queue = channel.queueDeclare().getQueue();
        assertThat(channel.messageCount(queue)).isZero();
        connection.close(AMQP.REPLY_SUCCESS, "shutting down");

        assertThat(connection.isOpen()).isFalse();
        assertThat(shutdown.get()).isNotNull();
        assertThat(shutdown.get().isInitiatedByApplication()).isTrue();
        assertThat(connection.getCloseReason()).isSameAs(shutdown.get());
        AMQP.Connection.Close close = (AMQP.Connection.Close) shutdown.get().getReason();
        assertThat(close.getReplyText()).isEqualTo("shutting down");
        assertThat(events.started.await(2, TimeUnit.SECONDS)).isFalse();
        assertThat(connection.isOpen()).isFalse();
    }

    private static ConnectionFactory recoveringFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(port);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10_000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(500);
        return factory;
    }

    private static void publish(Channel channel, String routingKey, String text) throws Exception {
        channel.basicPublish(EXCHANGE, routingKey, MessageProperties.TEXT_PLAIN, text.getBytes(StandardCharsets.UTF_8));
        channel.waitForConfirmsOrDie(10_000);
    }

    /// Counts the start and end of a recovery for a connection or channel.
    private static final class RecoveryEvents implements RecoveryListener {

        private final CountDownLatch started = new CountDownLatch(1);

        private final CountDownLatch recovered = new CountDownLatch(1);

        @Override
        public void handleRecoveryStarted(Recoverable recoverable) {
            started.countDown();
        }

        @Override
        public void handleRecovery(Recoverable recoverable) {
            recovered.countDown();
        }
    }

    /// Collects order events and acknowledges each one once handled.
    private static final class OrderListener extends DefaultConsumer {

        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

        OrderListener(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                byte[] body) throws IOException {
            events.add(envelope.getRoutingKey() + ":" + new String(body, StandardCharsets.UTF_8));
            getChannel().basicAck(envelope.getDeliveryTag(), false);
        }

        String next() throws Exception {
            String event = events.poll(15, TimeUnit.SECONDS);
            if (event == null) {
                throw new TimeoutException("No order event arrived");
            }
            return event;
        }
    }

    private static void awaitBroker() throws Exception {
        ConnectionFactory probe = new ConnectionFactory();
        probe.setHost("127.0.0.1");
        probe.setPort(port);
        probe.setConnectionTimeout(10_000);
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
