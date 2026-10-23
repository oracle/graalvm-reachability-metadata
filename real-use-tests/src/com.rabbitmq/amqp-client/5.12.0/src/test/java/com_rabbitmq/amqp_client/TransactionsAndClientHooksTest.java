/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq.amqp_client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.AddressResolver;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.CredentialsProvider;
import com.rabbitmq.client.impl.ForgivingExceptionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises AMQP channel transactions and the client-side hooks an application
/// installs on a `ConnectionFactory`: an `ExceptionHandler` for failing consumers,
/// a `TrafficListener`, a `CredentialsProvider`, and an `AddressResolver` that
/// fails over past an unreachable broker address.
///
/// The scenario is an order hand-off service that moves orders from an inbox to
/// an outbox atomically, against a real broker.
public class TransactionsAndClientHooksTest {

    private static final String IMAGE = "rabbitmq:4.4";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static String containerId;

    private static int brokerPort;

    private static ConnectionFactory factory;

    @BeforeAll
    static void startBroker() throws Exception {
        containerId = docker("run", "-d", "--rm", "-p", "127.0.0.1::5672", IMAGE);
        String mapping = docker("port", containerId, "5672/tcp").lines().findFirst().orElseThrow();
        brokerPort = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        factory = newFactory();
        awaitBroker();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (containerId != null) {
            docker("stop", containerId);
        }
    }

    @Test
    void transactionsMakePublishesAndAcksAtomic() throws Exception {
        try (Connection connection = factory.newConnection("order-handoff")) {
            Channel observer = connection.createChannel();
            Channel tx = connection.createChannel();
            String inbox = observer.queueDeclare("orders.inbox", true, false, false, null).getQueue();
            String outbox = observer.queueDeclare("orders.outbox", true, false, false, null).getQueue();
            observer.queuePurge(inbox);
            observer.queuePurge(outbox);

            assertThat(tx.txSelect()).isInstanceOf(AMQP.Tx.SelectOk.class);
            for (int order = 1; order <= 3; order++) {
                tx.basicPublish("", inbox, MessageProperties.PERSISTENT_TEXT_PLAIN, bytes("order-" + order));
            }
            assertThat(observer.messageCount(inbox)).isZero();
            assertThat(tx.txCommit()).isInstanceOf(AMQP.Tx.CommitOk.class);
            assertThat(observer.messageCount(inbox)).isEqualTo(3);

            tx.basicPublish("", inbox, MessageProperties.PERSISTENT_TEXT_PLAIN, bytes("order-abandoned"));
            assertThat(tx.txRollback()).isInstanceOf(AMQP.Tx.RollbackOk.class);
            assertThat(observer.messageCount(inbox)).isEqualTo(3);

            // A failed hand-off: the outbox publish and the inbox ack are rolled back together.
            GetResponse first = tx.basicGet(inbox, false);
            assertThat(text(first.getBody())).isEqualTo("order-1");
            tx.basicPublish("", outbox, MessageProperties.PERSISTENT_TEXT_PLAIN, bytes("shipped:order-1"));
            tx.basicAck(first.getEnvelope().getDeliveryTag(), false);
            tx.txRollback();
            assertThat(observer.messageCount(outbox)).isZero();
            tx.basicRecover(true);
            tx.txCommit();

            // Successful hand-offs: every order moves to the outbox in one commit each.
            List<String> handedOff = new ArrayList<>();
            GetResponse response;
            while ((response = tx.basicGet(inbox, false)) != null) {
                String order = text(response.getBody());
                handedOff.add(order);
                tx.basicPublish("", outbox, MessageProperties.PERSISTENT_TEXT_PLAIN, bytes("shipped:" + order));
                tx.basicAck(response.getEnvelope().getDeliveryTag(), false);
                tx.txCommit();
            }
            assertThat(handedOff).containsExactlyInAnyOrder("order-1", "order-2", "order-3");
            assertThat(observer.messageCount(inbox)).isZero();
            assertThat(observer.messageCount(outbox)).isEqualTo(3);

            List<String> shipped = new ArrayList<>();
            while ((response = observer.basicGet(outbox, true)) != null) {
                shipped.add(text(response.getBody()));
            }
            assertThat(shipped).containsExactlyInAnyOrder("shipped:order-1", "shipped:order-2", "shipped:order-3");

            observer.queueDelete(inbox);
            observer.queueDelete(outbox);
        }
    }

    @Test
    void exceptionHandlersDecideWhetherAFailingConsumerKillsItsChannel() throws Exception {
        RecordingExceptionHandler forgiving = new RecordingExceptionHandler();
        ConnectionFactory forgivingFactory = newFactory();
        forgivingFactory.setExceptionHandler(forgiving);

        try (Connection connection = forgivingFactory.newConnection("forgiving-consumer")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare("", false, true, true, null).getQueue();
            BlockingQueue<String> processed = new LinkedBlockingQueue<>();
            String tag = channel.basicConsume(queue, true, new FragileConsumer(channel, processed));

            channel.basicPublish("", queue, null, bytes("poison"));
            channel.basicPublish("", queue, null, bytes("healthy"));

            assertThat(processed.poll(10, TimeUnit.SECONDS)).isEqualTo("healthy");
            assertThat(forgiving.failures).hasSize(1);
            ConsumerFailure failure = forgiving.failures.get(0);
            assertThat(failure.consumerTag()).isEqualTo(tag);
            assertThat(failure.methodName()).isEqualTo("handleDelivery");
            assertThat(failure.cause()).isInstanceOf(IllegalStateException.class).hasMessage("cannot parse poison");
            assertThat(channel.isOpen()).isTrue();
        }

        try (Connection connection = factory.newConnection("strict-consumer")) {
            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare("", false, true, true, null).getQueue();
            CountDownLatch closed = new CountDownLatch(1);
            List<ShutdownSignalException> causes = new CopyOnWriteArrayList<>();
            channel.addShutdownListener(cause -> {
                causes.add(cause);
                closed.countDown();
            });
            channel.basicConsume(queue, true, new FragileConsumer(channel, new LinkedBlockingQueue<>()));

            channel.basicPublish("", queue, null, bytes("poison"));

            assertThat(closed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(channel.isOpen()).isFalse();
            assertThat(causes.get(0).isInitiatedByApplication()).isTrue();
            assertThat(connection.isOpen()).isTrue();
        }
    }

    @Test
    void resolverFailsOverAndHooksObserveTheSession() throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        CountingCredentials credentials = new CountingCredentials("guest", "guest");
        RecordingTrafficListener traffic = new RecordingTrafficListener();
        ConnectionFactory hooked = new ConnectionFactory();
        hooked.setConnectionTimeout(5_000);
        hooked.setAutomaticRecoveryEnabled(false);
        hooked.setCredentialsProvider(credentials);
        hooked.setTrafficListener(traffic);
        RegistryResolver resolver = new RegistryResolver(List.of(
                new Address("127.0.0.1", deadPort),
                new Address("127.0.0.1", brokerPort)));

        ExecutorService consumerPool = Executors.newFixedThreadPool(2);
        try (Connection connection = hooked.newConnection(consumerPool, resolver, "registry-client")) {
            assertThat(resolver.lookups.get()).isEqualTo(1);
            assertThat(connection.getPort()).isEqualTo(brokerPort);
            assertThat(connection.getClientProvidedName()).isEqualTo("registry-client");
            assertThat(credentials.usernameReads.get()).isPositive();
            assertThat(credentials.passwordReads.get()).isPositive();

            Channel channel = connection.createChannel();
            String queue = channel.queueDeclare("", false, true, true, null).getQueue();
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("text/plain")
                    .headers(Map.of("tenant", "acme"))
                    .build();
            channel.basicPublish("", queue, properties, bytes("traced"));
            GetResponse response = awaitMessage(channel, queue);
            assertThat(text(response.getBody())).isEqualTo("traced");
            assertThat(response.getProps().getHeaders().get("tenant").toString()).isEqualTo("acme");
        } finally {
            consumerPool.shutdown();
        }

        assertThat(traffic.written).contains("queue.declare", "basic.publish", "basic.get");
        assertThat(traffic.read).contains("queue.declare-ok", "basic.get-ok");
        assertThat(traffic.publishedBodies).containsExactly("traced");
        assertThat(traffic.receivedBodies).containsExactly("traced");
    }

    /// A consumer that rejects one malformed order by throwing from its callback.
    private static final class FragileConsumer extends DefaultConsumer {

        private final BlockingQueue<String> processed;

        FragileConsumer(Channel channel, BlockingQueue<String> processed) {
            super(channel);
            this.processed = processed;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                byte[] body) {
            String order = text(body);
            if (order.equals("poison")) {
                throw new IllegalStateException("cannot parse " + order);
            }
            processed.add(order);
        }
    }

    private record ConsumerFailure(String consumerTag, String methodName, Throwable cause) {
    }

    /// Logs consumer failures for the application instead of closing the channel.
    private static final class RecordingExceptionHandler extends ForgivingExceptionHandler {

        private final List<ConsumerFailure> failures = new CopyOnWriteArrayList<>();

        @Override
        public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer,
                String consumerTag, String methodName) {
            failures.add(new ConsumerFailure(consumerTag, methodName, exception));
        }
    }

    /// Supplies credentials from an application-owned store and counts how often they are read.
    private static final class CountingCredentials implements CredentialsProvider {

        private final String username;

        private final String password;

        private final AtomicInteger usernameReads = new AtomicInteger();

        private final AtomicInteger passwordReads = new AtomicInteger();

        CountingCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String getUsername() {
            usernameReads.incrementAndGet();
            return username;
        }

        @Override
        public String getPassword() {
            passwordReads.incrementAndGet();
            return password;
        }
    }

    /// Resolves broker addresses from a service registry, listing a stale node first.
    private static final class RegistryResolver implements AddressResolver {

        private final List<Address> addresses;

        private final AtomicInteger lookups = new AtomicInteger();

        RegistryResolver(List<Address> addresses) {
            this.addresses = addresses;
        }

        @Override
        public List<Address> getAddresses() {
            lookups.incrementAndGet();
            return addresses;
        }
    }

    /// Records the AMQP methods and message bodies crossing the wire.
    private static final class RecordingTrafficListener implements TrafficListener {

        private final List<String> written = new CopyOnWriteArrayList<>();

        private final List<String> read = new CopyOnWriteArrayList<>();

        private final List<String> publishedBodies = new CopyOnWriteArrayList<>();

        private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

        @Override
        public void write(Command command) {
            String method = command.getMethod().protocolMethodName();
            written.add(method);
            if (method.equals("basic.publish")) {
                publishedBodies.add(text(command.getContentBody()));
            }
        }

        @Override
        public void read(Command command) {
            String method = command.getMethod().protocolMethodName();
            read.add(method);
            if (method.equals("basic.get-ok")) {
                receivedBodies.add(text(command.getContentBody()));
            }
        }
    }

    private static ConnectionFactory newFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1");
        connectionFactory.setPort(brokerPort);
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");
        connectionFactory.setConnectionTimeout(10_000);
        connectionFactory.setAutomaticRecoveryEnabled(false);
        return connectionFactory;
    }

    private static GetResponse awaitMessage(Channel channel, String queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            GetResponse response = channel.basicGet(queue, true);
            if (response != null) {
                return response;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("No message arrived on " + queue);
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
