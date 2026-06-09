/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_amqp.spring_rabbitmq_client;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.rabbitmq.client.amqp.ByteCapacity;
import com.rabbitmq.client.amqp.Connection;
import com.rabbitmq.client.amqp.Connection.ConnectionInfo;
import com.rabbitmq.client.amqp.Consumer;
import com.rabbitmq.client.amqp.ConsumerBuilder;
import com.rabbitmq.client.amqp.Management;
import com.rabbitmq.client.amqp.Management.BindingSpecification;
import com.rabbitmq.client.amqp.Management.ClassicQueueSpecification;
import com.rabbitmq.client.amqp.Management.ClassicQueueVersion;
import com.rabbitmq.client.amqp.Management.DelayedRetryType;
import com.rabbitmq.client.amqp.Management.ExchangeSpecification;
import com.rabbitmq.client.amqp.Management.OverflowStrategy;
import com.rabbitmq.client.amqp.Management.PurgeStatus;
import com.rabbitmq.client.amqp.Management.QueueInfo;
import com.rabbitmq.client.amqp.Management.QueueLeaderLocator;
import com.rabbitmq.client.amqp.Management.QueueSpecification;
import com.rabbitmq.client.amqp.Management.QueueType;
import com.rabbitmq.client.amqp.Management.QuorumQueueDeadLetterStrategy;
import com.rabbitmq.client.amqp.Management.QuorumQueueSpecification;
import com.rabbitmq.client.amqp.Management.StreamSpecification;
import com.rabbitmq.client.amqp.Management.UnbindSpecification;
import com.rabbitmq.client.amqp.Message;
import com.rabbitmq.client.amqp.Publisher;
import com.rabbitmq.client.amqp.PublisherBuilder;
import com.rabbitmq.client.amqp.Requester;
import com.rabbitmq.client.amqp.RequesterBuilder;
import com.rabbitmq.client.amqp.Resource;
import com.rabbitmq.client.amqp.ResponderBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAcknowledgment;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbitmq.client.AmqpConnectionFactory;
import org.springframework.amqp.rabbitmq.client.RabbitAmqpAdmin;
import org.springframework.amqp.rabbitmq.client.RabbitAmqpTemplate;
import org.springframework.amqp.rabbitmq.client.RabbitAmqpUtils;
import org.springframework.amqp.rabbitmq.client.listener.RabbitAmqpListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Spring_rabbitmq_clientTest {
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    @Test
    void toAmqpMessageMapsSpringMessageBodyPropertiesAndSupportedHeaderTypes() {
        org.springframework.amqp.core.Message springMessage = springMessage(PAYLOAD);
        MessageProperties properties = springMessage.getMessageProperties();
        UUID id = UUID.randomUUID();
        properties.setHeader("string", "value");
        properties.setHeader("long", 9L);
        properties.setHeader("integer", 8);
        properties.setHeader("short", (short) 7);
        properties.setHeader("byte", (byte) 6);
        properties.setHeader("double", 1.25D);
        properties.setHeader("float", 2.5F);
        properties.setHeader("char", 'c');
        properties.setHeader("uuid", id);
        properties.setHeader("binary", new byte[] {1, 2, 3});
        properties.setHeader("boolean", true);
        properties.setHeader("unsupported", List.of("not mapped"));
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setContentEncoding("utf-8");
        properties.setMessageId("message-1");
        properties.setCorrelationId("correlation-1");
        properties.setPriority(4);
        properties.setUserId("guest");
        properties.setReplyTo("reply.queue");
        properties.setTimestamp(new java.util.Date(1_000L));
        properties.setExpiration("5000");

        FakeMessage amqpMessage = new FakeMessage();
        RabbitAmqpUtils.toAmqpMessage(springMessage, amqpMessage);

        assertThat(amqpMessage.body()).isEqualTo(PAYLOAD);
        assertThat(amqpMessage.contentType()).isEqualTo(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        assertThat(amqpMessage.contentEncoding()).isEqualTo("utf-8");
        assertThat(amqpMessage.messageIdAsString()).isEqualTo("message-1");
        assertThat(amqpMessage.correlationIdAsString()).isEqualTo("correlation-1");
        assertThat(amqpMessage.priority()).isEqualTo((byte) 4);
        assertThat(new String(amqpMessage.userId(), StandardCharsets.UTF_8)).isEqualTo("guest");
        assertThat(amqpMessage.to()).isEqualTo("reply.queue");
        assertThat(amqpMessage.creationTime()).isEqualTo(1_000L);
        assertThat(amqpMessage.absoluteExpiryTime()).isEqualTo(6_000L);
        assertThat(amqpMessage.properties).containsEntry("string", "value")
                .containsEntry("long", 9L)
                .containsEntry("integer", 8)
                .containsEntry("short", (short) 7)
                .containsEntry("byte", (byte) 6)
                .containsEntry("double", 1.25D)
                .containsEntry("float", 2.5F)
                .containsEntry("char", 'c')
                .containsEntry("uuid", id)
                .containsEntry("boolean", true)
                .doesNotContainKey("unsupported");
        assertThat((byte[]) amqpMessage.properties.get("binary")).containsExactly((byte) 1, (byte) 2, (byte) 3);
    }

    @Test
    void fromAmqpMessageMapsRabbitPropertiesAndAcknowledgmentOperations() {
        FakeMessage source = new FakeMessage()
                .messageId("message-2")
                .correlationId("correlation-2")
                .userId("user".getBytes(StandardCharsets.UTF_8))
                .contentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .contentEncoding("utf-8")
                .replyTo("reply.address")
                .creationTime(10_000L)
                .absoluteExpiryTime(16_000L)
                .body(PAYLOAD)
                .property("application", "orders")
                .property("attempt", 3);
        RecordingConsumerContext context = new RecordingConsumerContext();

        org.springframework.amqp.core.Message mapped = RabbitAmqpUtils.fromAmqpMessage(source, context);

        assertThat(mapped.getBody()).isEqualTo(PAYLOAD);
        MessageProperties properties = mapped.getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo("message-2");
        assertThat(properties.getCorrelationId()).isEqualTo("correlation-2");
        assertThat(properties.getUserId()).isEqualTo("user");
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        assertThat(properties.getContentEncoding()).isEqualTo("utf-8");
        assertThat(properties.getReplyTo()).isEqualTo("reply.address");
        assertThat(properties.getTimestamp()).hasTime(10_000L);
        assertThat(properties.getExpiration()).isEqualTo("6000");
        assertThat(properties.getHeaders()).containsEntry("application", "orders").containsEntry("attempt", 3);

        AmqpAcknowledgment acknowledgment = properties.getAmqpAcknowledgment();
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.ACCEPT);
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.REJECT);
        acknowledgment.acknowledge(AmqpAcknowledgment.Status.REQUEUE);

        assertThat(context.accepts).isEqualTo(1);
        assertThat(context.discards).isEqualTo(1);
        assertThat(context.requeues).isEqualTo(1);
    }

    @Test
    void templateConvertAndSendPublishesConvertedMessageToConfiguredAddress() throws Exception {
        FakeConnection connection = new FakeConnection();
        RabbitAmqpTemplate template = new RabbitAmqpTemplate(connectionFactory(connection));
        template.setPublishTimeout(Duration.ofMillis(250));
        template.setExchange("orders.exchange");
        template.setRoutingKey("created");

        CompletableFuture<Boolean> result = template.convertAndSend("orders.exchange", "created", "created-event", message -> {
            message.getMessageProperties().setHeader("tenant", "test");
            message.getMessageProperties().setMessageId("event-1");
            message.getMessageProperties().setPriority(0);
            return message;
        });

        assertThat(result.get(1, TimeUnit.SECONDS)).isTrue();
        assertThat(connection.publisherBuilder.publishTimeout).isEqualTo(Duration.ofMillis(250));
        assertThat(connection.publisher.published).hasSize(1);
        FakeMessage published = connection.publisher.published.get(0);
        assertThat(published.addressExchange).isEqualTo("orders.exchange");
        assertThat(published.addressKey).isEqualTo("created");
        assertThat(published.messageIdAsString()).isEqualTo("event-1");
        assertThat(new String(published.body(), StandardCharsets.UTF_8)).isEqualTo("created-event");
        assertThat(published.property("tenant")).isEqualTo("test");

        template.destroy();
        assertThat(connection.publisher.closed).isTrue();
    }

    @Test
    void templateReceiveAndConvertConsumesOneDeliveryAndClosesConsumer() throws Exception {
        FakeMessage delivery = textMessage("incoming");
        RecordingConsumerContext deliveryContext = new RecordingConsumerContext();
        FakeConnection connection = new FakeConnection();
        connection.nextDelivery = delivery;
        connection.nextDeliveryContext = deliveryContext;
        RabbitAmqpTemplate template = new RabbitAmqpTemplate(connectionFactory(connection));
        template.setCompletionTimeout(Duration.ofMillis(250));
        template.setReceiveQueue("orders.queue");

        Object converted = template.receiveAndConvert().get(1, TimeUnit.SECONDS);

        assertThat(converted).isEqualTo("incoming");
        assertThat(deliveryContext.accepts).isEqualTo(1);
        assertThat(connection.consumerBuilder.queue).isEqualTo("orders.queue");
        assertThat(connection.consumerBuilder.initialCredits).isEqualTo(1);
        assertThat(connection.consumerBuilder.priority).isEqualTo(10);
        assertThat(connection.createdConsumers).hasSize(1);
        assertThat(connection.createdConsumers.get(0).closed).isTrue();
    }

    @Test
    void templateConvertSendAndReceiveUsesRequesterCorrelationAndConvertsReply() throws Exception {
        FakeConnection connection = new FakeConnection();
        connection.requestReply = textMessage("accepted");
        RabbitAmqpTemplate template = new RabbitAmqpTemplate(connectionFactory(connection));
        template.setPublishTimeout(Duration.ofMillis(250));
        template.setCompletionTimeout(Duration.ofMillis(250));
        template.setQueue("requests");
        template.setReplyToQueue("replies");
        org.springframework.amqp.core.Message request = springMessage("approve".getBytes(StandardCharsets.UTF_8));
        request.getMessageProperties().setMessageId("request-1");

        String reply = template.<String>convertSendAndReceive(request).get(1, TimeUnit.SECONDS);

        assertThat(reply).isEqualTo("accepted");
        assertThat(connection.requesterBuilder.requestTimeout).isEqualTo(Duration.ofMillis(250));
        assertThat(connection.requesterBuilder.replyToQueue).isEqualTo("replies");
        assertThat(connection.requesterBuilder.correlationIdSupplier.get()).isEqualTo("request-1");
        assertThat(connection.requester.published).hasSize(1);
        assertThat(connection.requester.published.get(0).addressQueue).isEqualTo("requests");
        assertThat(connection.requester.closed).isTrue();
    }

    @Test
    void listenerContainerBuildsConsumersDeliversSpringMessageAndAutoSettles() throws Exception {
        FakeMessage delivery = textMessage("container-message");
        RecordingConsumerContext deliveryContext = new RecordingConsumerContext();
        FakeConnection connection = new FakeConnection();
        connection.nextDelivery = delivery;
        connection.nextDeliveryContext = deliveryContext;
        RabbitAmqpListenerContainer container = new RabbitAmqpListenerContainer(connectionFactory(connection));
        AtomicReference<org.springframework.amqp.core.Message> received = new AtomicReference<>();
        AtomicReference<AcknowledgeMode> acknowledgeMode = new AtomicReference<>();
        MessageListener listener = new MessageListener() {
            @Override
            public void onMessage(org.springframework.amqp.core.Message message) {
                received.set(message);
            }

            @Override
            public void containerAckMode(AcknowledgeMode mode) {
                acknowledgeMode.set(mode);
            }
        };
        container.setQueueNames("container.queue");
        container.setConsumersPerQueue(2);
        container.setInitialCredits(12);
        container.setPriority(5);
        container.setGracefulShutdownPeriod(Duration.ofMillis(250));
        container.setupMessageListener(listener);

        container.afterPropertiesSet();
        container.start();

        assertThat(container.isRunning()).isTrue();
        assertThat(acknowledgeMode.get()).isEqualTo(AcknowledgeMode.AUTO);
        assertThat(received.get()).isNotNull();
        assertThat(new String(received.get().getBody(), StandardCharsets.UTF_8)).isEqualTo("container-message");
        assertThat(deliveryContext.accepts).isEqualTo(1);
        assertThat(connection.consumerBuilder.queue).isEqualTo("container.queue");
        assertThat(connection.consumerBuilder.initialCredits).isEqualTo(12);
        assertThat(connection.consumerBuilder.priority).isEqualTo(5);
        assertThat(connection.createdConsumers).hasSize(2);

        CountDownLatch stopped = new CountDownLatch(1);
        container.stop(stopped::countDown);
        assertThat(stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(connection.createdConsumers).allSatisfy(consumer -> assertThat(consumer.closed).isTrue());
    }

    @Test
    void listenerContainerPausesAndResumesConsumersForSelectedQueue() throws Exception {
        FakeConnection connection = new FakeConnection();
        RabbitAmqpListenerContainer container = new RabbitAmqpListenerContainer(connectionFactory(connection));
        container.setQueueNames("billing.high", "billing.low");
        container.setConsumersPerQueue(2);
        container.setGracefulShutdownPeriod(Duration.ofMillis(250));
        container.setupMessageListener(message -> assertThat(message).isNotNull());

        container.afterPropertiesSet();
        container.start();

        assertThat(container.isRunning()).isTrue();
        assertThat(connection.createdConsumers).hasSize(4);
        List<FakeConsumer> highPriorityConsumers = connection.createdConsumers.stream()
                .filter(consumer -> "billing.high".equals(consumer.queue))
                .toList();
        List<FakeConsumer> lowPriorityConsumers = connection.createdConsumers.stream()
                .filter(consumer -> "billing.low".equals(consumer.queue))
                .toList();
        assertThat(highPriorityConsumers).hasSize(2);
        assertThat(lowPriorityConsumers).hasSize(2);

        container.pause("billing.high");

        assertThat(highPriorityConsumers).allSatisfy(consumer -> assertThat(consumer.paused).isTrue());
        assertThat(lowPriorityConsumers).allSatisfy(consumer -> assertThat(consumer.paused).isFalse());

        container.resume("billing.high");

        assertThat(connection.createdConsumers).allSatisfy(consumer -> assertThat(consumer.paused).isFalse());

        container.pause();

        assertThat(connection.createdConsumers).allSatisfy(consumer -> assertThat(consumer.paused).isTrue());

        container.resume();

        assertThat(connection.createdConsumers).allSatisfy(consumer -> assertThat(consumer.paused).isFalse());

        CountDownLatch stopped = new CountDownLatch(1);
        container.stop(stopped::countDown);
        assertThat(stopped.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(connection.createdConsumers).allSatisfy(consumer -> assertThat(consumer.closed).isTrue());
    }

    @Test
    void adminDeclaresBindingsAndReportsQueueStateThroughManagementApi() {
        FakeConnection connection = new FakeConnection();
        RabbitAmqpAdmin admin = new RabbitAmqpAdmin(connectionFactory(connection));
        Queue queue = new Queue("orders.queue", false, true, true, Map.of("x-message-ttl", 5_000));
        DirectExchange exchange = new DirectExchange("orders.exchange", false, true,
                Map.of("alternate-exchange", "orders.unrouted"));
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("orders.created");

        String actualName = admin.declareQueue(queue);
        admin.declareExchange(exchange);
        admin.declareBinding(binding);
        int purged = admin.purgeQueue(queue.getName());
        Properties queueProperties = admin.getQueueProperties(queue.getName());
        admin.removeBinding(binding);
        admin.deleteQueue(queue.getName(), true, true);
        admin.deleteExchange(exchange.getName());

        assertThat(actualName).isEqualTo("orders.queue");
        assertThat(queue.getActualName()).isEqualTo("orders.queue");
        assertThat(purged).isEqualTo(4);
        assertThat(queueProperties)
                .containsEntry(RabbitAdmin.QUEUE_NAME, "orders.queue")
                .containsEntry(RabbitAdmin.QUEUE_MESSAGE_COUNT, 0L)
                .containsEntry(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0)
                .containsEntry(RabbitAmqpAdmin.QUEUE_TYPE, "classic");
        assertThat(connection.management.closed).isTrue();
        assertThat(connection.management.queueSpecification.name).isEqualTo("orders.queue");
        assertThat(connection.management.queueSpecification.autoDelete).isTrue();
        assertThat(connection.management.queueSpecification.exclusive).isTrue();
        assertThat(connection.management.queueSpecification.arguments).containsEntry("x-message-ttl", 5_000);
        assertThat(connection.management.exchangeSpecification.name).isEqualTo("orders.exchange");
        assertThat(connection.management.exchangeSpecification.type).isEqualTo("direct");
        assertThat(connection.management.exchangeSpecification.autoDelete).isTrue();
        assertThat(connection.management.exchangeSpecification.declared).isTrue();
        assertThat(connection.management.exchangeSpecification.arguments)
                .containsEntry("alternate-exchange", "orders.unrouted");
        assertThat(connection.management.bindingSpecification.sourceExchange).isEqualTo("orders.exchange");
        assertThat(connection.management.bindingSpecification.destinationQueue).isEqualTo("orders.queue");
        assertThat(connection.management.bindingSpecification.key).isEqualTo("orders.created");
        assertThat(connection.management.bindingSpecification.bound).isTrue();
        FakeBindingSpecification unbindSpecification = connection.management.unbindSpecification;
        assertThat(unbindSpecification.destinationQueue).isEqualTo("orders.queue");
        assertThat(unbindSpecification.unbound).isTrue();
        assertThat(connection.management.deletedQueues).containsExactly("orders.queue");
        assertThat(connection.management.deletedExchanges).containsExactly("orders.exchange");
    }

    @Test
    void templateRequiresDefaultQueueForReceiveOperations() {
        RabbitAmqpTemplate template = new RabbitAmqpTemplate(connectionFactory(new FakeConnection()));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(template::receive)
                .withMessageContaining("No 'queue' specified");
    }

    private static org.springframework.amqp.core.Message springMessage(byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
        properties.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
        properties.setPriority(0);
        return new org.springframework.amqp.core.Message(body, properties);
    }

    private static FakeMessage textMessage(String body) {
        return new FakeMessage()
                .contentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .contentEncoding(StandardCharsets.UTF_8.name())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private static AmqpConnectionFactory connectionFactory(Connection connection) {
        return () -> connection;
    }

    private static final class FakeConnection implements Connection {
        private final FakeManagement management = new FakeManagement();
        private final FakePublisher publisher = new FakePublisher();
        private final FakePublisherBuilder publisherBuilder = new FakePublisherBuilder(this.publisher);
        private final FakeConsumer consumer = new FakeConsumer();
        private final FakeConsumerBuilder consumerBuilder = new FakeConsumerBuilder(this.consumer);
        private final FakeRequester requester = new FakeRequester();
        private final FakeRequesterBuilder requesterBuilder = new FakeRequesterBuilder(this.requester);
        private final List<FakeConsumer> createdConsumers = new ArrayList<>();
        private FakeMessage nextDelivery;
        private RecordingConsumerContext nextDeliveryContext;
        private boolean nextDeliveryDelivered;
        private FakeMessage requestReply;
        private boolean closed;

        private FakeConnection() {
            this.consumerBuilder.owner = this;
            this.requester.owner = this;
        }

        @Override
        public Management management() {
            return this.management;
        }

        @Override
        public PublisherBuilder publisherBuilder() {
            return this.publisherBuilder;
        }

        @Override
        public ConsumerBuilder consumerBuilder() {
            return this.consumerBuilder;
        }

        @Override
        public RequesterBuilder requesterBuilder() {
            return this.requesterBuilder;
        }

        @Override
        public ResponderBuilder responderBuilder() {
            throw new UnsupportedOperationException("Responder is not used by these tests");
        }

        @Override
        public ConnectionInfo connectionInfo() {
            return new FakeConnectionInfo();
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FakeConnectionInfo implements ConnectionInfo {

        @Override
        public String brokerVersion() {
            return "test-broker-version";
        }

        @Override
        public String brokerProductName() {
            return "RabbitMQ";
        }

        @Override
        public String brokerNode() {
            return "rabbit@test";
        }

        @Override
        public String host() {
            return "localhost";
        }

        @Override
        public int port() {
            return 5672;
        }

        @Override
        public String name() {
            return "test-connection";
        }
    }

    private static final class FakeManagement implements Management {
        private final FakeQueueSpecification queueSpecification = new FakeQueueSpecification();
        private final FakeExchangeSpecification exchangeSpecification = new FakeExchangeSpecification();
        private final FakeBindingSpecification bindingSpecification = new FakeBindingSpecification();
        private final FakeUnbindSpecification unbindSpecification = new FakeUnbindSpecification();
        private final List<String> deletedQueues = new ArrayList<>();
        private final List<String> deletedExchanges = new ArrayList<>();
        private boolean closed;

        @Override
        public QueueSpecification queue() {
            this.queueSpecification.name = null;
            return this.queueSpecification;
        }

        @Override
        public QueueSpecification queue(String name) {
            this.queueSpecification.name = name;
            return this.queueSpecification;
        }

        @Override
        public QueueInfo queueInfo(String name) {
            return new FakeQueueInfo(name, QueueType.CLASSIC, 0L, 0);
        }

        @Override
        public void queueDelete(String name) {
            this.deletedQueues.add(name);
        }

        @Override
        public PurgeStatus queuePurge(String name) {
            return () -> 4L;
        }

        @Override
        public ExchangeSpecification exchange() {
            this.exchangeSpecification.name = null;
            return this.exchangeSpecification;
        }

        @Override
        public ExchangeSpecification exchange(String name) {
            this.exchangeSpecification.name = name;
            return this.exchangeSpecification;
        }

        @Override
        public void exchangeDelete(String name) {
            this.deletedExchanges.add(name);
        }

        @Override
        public BindingSpecification binding() {
            return this.bindingSpecification;
        }

        @Override
        public UnbindSpecification unbind() {
            return this.unbindSpecification;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FakeQueueSpecification implements QueueSpecification, ClassicQueueSpecification,
            QuorumQueueSpecification, StreamSpecification {
        private String name;
        private boolean exclusive;
        private boolean autoDelete;
        private QueueType type = QueueType.CLASSIC;
        private final Map<String, Object> arguments = new LinkedHashMap<>();

        @Override
        public QueueSpecification name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public QueueSpecification exclusive(boolean exclusive) {
            this.exclusive = exclusive;
            return this;
        }

        @Override
        public QueueSpecification autoDelete(boolean autoDelete) {
            this.autoDelete = autoDelete;
            return this;
        }

        @Override
        public QueueSpecification type(QueueType type) {
            this.type = type;
            return this;
        }

        @Override
        public QueueSpecification deadLetterExchange(String exchange) {
            this.arguments.put("x-dead-letter-exchange", exchange);
            return this;
        }

        @Override
        public QueueSpecification deadLetterRoutingKey(String key) {
            this.arguments.put("x-dead-letter-routing-key", key);
            return this;
        }

        @Override
        public QueueSpecification overflowStrategy(String strategy) {
            this.arguments.put("x-overflow", strategy);
            return this;
        }

        @Override
        public QueueSpecification overflowStrategy(OverflowStrategy strategy) {
            return overflowStrategy(strategy.strategy());
        }

        @Override
        public QueueSpecification expires(Duration expires) {
            this.arguments.put("x-expires", expires);
            return this;
        }

        @Override
        public QueueSpecification maxLength(long maxLength) {
            this.arguments.put("x-max-length", maxLength);
            return this;
        }

        @Override
        public QueueSpecification maxLengthBytes(ByteCapacity maxLengthBytes) {
            this.arguments.put("x-max-length-bytes", maxLengthBytes);
            return this;
        }

        @Override
        public QueueSpecification singleActiveConsumer(boolean singleActiveConsumer) {
            this.arguments.put("x-single-active-consumer", singleActiveConsumer);
            return this;
        }

        @Override
        public QueueSpecification messageTtl(Duration ttl) {
            this.arguments.put("x-message-ttl", ttl);
            return this;
        }

        @Override
        public QueueSpecification leaderLocator(QueueLeaderLocator leaderLocator) {
            this.arguments.put("x-queue-leader-locator", leaderLocator.locator());
            return this;
        }

        @Override
        public QuorumQueueSpecification quorum() {
            this.type = QueueType.QUORUM;
            return this;
        }

        @Override
        public ClassicQueueSpecification classic() {
            this.type = QueueType.CLASSIC;
            return this;
        }

        @Override
        public StreamSpecification stream() {
            this.type = QueueType.STREAM;
            return this;
        }

        @Override
        public QueueSpecification argument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        @Override
        public QueueSpecification arguments(Map<String, Object> arguments) {
            this.arguments.clear();
            if (arguments != null) {
                this.arguments.putAll(arguments);
            }
            return this;
        }

        @Override
        public QueueInfo declare() {
            return new FakeQueueInfo(this.name, this.type, 0L, 0);
        }

        @Override
        public ClassicQueueSpecification maxPriority(int maxPriority) {
            this.arguments.put("x-max-priority", maxPriority);
            return this;
        }

        @Override
        public ClassicQueueSpecification version(ClassicQueueVersion version) {
            this.arguments.put("x-queue-version", version);
            return this;
        }

        @Override
        public QueueSpecification queue() {
            return this;
        }

        @Override
        public QuorumQueueSpecification deadLetterStrategy(String strategy) {
            this.arguments.put("x-dead-letter-strategy", strategy);
            return this;
        }

        @Override
        public QuorumQueueSpecification deadLetterStrategy(QuorumQueueDeadLetterStrategy strategy) {
            return deadLetterStrategy(strategy.strategy());
        }

        @Override
        public QuorumQueueSpecification deliveryLimit(int deliveryLimit) {
            this.arguments.put("x-delivery-limit", deliveryLimit);
            return this;
        }

        @Override
        public FakeQueueSpecification initialMemberCount(int count) {
            this.arguments.put("x-initial-member-count", count);
            return this;
        }

        @Override
        public QuorumQueueSpecification delayedRetryType(DelayedRetryType type) {
            this.arguments.put("x-delayed-retry-type", type.type());
            return this;
        }

        @Override
        public QuorumQueueSpecification delayedRetryMin(Duration min) {
            this.arguments.put("x-delayed-retry-min", min);
            return this;
        }

        @Override
        public QuorumQueueSpecification delayedRetryMax(Duration max) {
            this.arguments.put("x-delayed-retry-max", max);
            return this;
        }

        @Override
        public StreamSpecification maxAge(Duration maxAge) {
            this.arguments.put("x-max-age", maxAge);
            return this;
        }

        @Override
        public StreamSpecification maxSegmentSizeBytes(ByteCapacity maxSegmentSize) {
            this.arguments.put("x-max-segment-size-bytes", maxSegmentSize);
            return this;
        }
    }

    private static final class FakeQueueInfo implements QueueInfo {
        private final String name;
        private final QueueType type;
        private final long messageCount;
        private final int consumerCount;

        private FakeQueueInfo(String name, QueueType type, long messageCount, int consumerCount) {
            this.name = name;
            this.type = type;
            this.messageCount = messageCount;
            this.consumerCount = consumerCount;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public boolean durable() {
            return false;
        }

        @Override
        public boolean autoDelete() {
            return true;
        }

        @Override
        public boolean exclusive() {
            return true;
        }

        @Override
        public QueueType type() {
            return this.type;
        }

        @Override
        public Map<String, Object> arguments() {
            return Map.of();
        }

        @Override
        public String leader() {
            return "node-1";
        }

        @Override
        public List<String> members() {
            return List.of("node-1");
        }

        @Override
        public long messageCount() {
            return this.messageCount;
        }

        @Override
        public int consumerCount() {
            return this.consumerCount;
        }
    }

    private static final class FakeExchangeSpecification implements ExchangeSpecification {
        private String name;
        private boolean autoDelete;
        private String type;
        private final Map<String, Object> arguments = new LinkedHashMap<>();
        private boolean declared;

        @Override
        public ExchangeSpecification name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public ExchangeSpecification autoDelete(boolean autoDelete) {
            this.autoDelete = autoDelete;
            return this;
        }

        @Override
        public ExchangeSpecification type(Management.ExchangeType type) {
            this.type = type.name().toLowerCase();
            return this;
        }

        @Override
        public ExchangeSpecification type(String type) {
            this.type = type;
            return this;
        }

        @Override
        public ExchangeSpecification argument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        @Override
        public ExchangeSpecification arguments(Map<String, Object> arguments) {
            this.arguments.clear();
            if (arguments != null) {
                this.arguments.putAll(arguments);
            }
            return this;
        }

        @Override
        public void declare() {
            this.declared = true;
        }
    }

    private static class FakeBindingSpecification implements BindingSpecification, UnbindSpecification {
        private String sourceExchange;
        private String destinationQueue;
        private String destinationExchange;
        private String key;
        private final Map<String, Object> arguments = new LinkedHashMap<>();
        private boolean bound;
        private boolean unbound;

        @Override
        public FakeBindingSpecification sourceExchange(String sourceExchange) {
            this.sourceExchange = sourceExchange;
            return this;
        }

        @Override
        public FakeBindingSpecification destinationQueue(String queue) {
            this.destinationQueue = queue;
            return this;
        }

        @Override
        public FakeBindingSpecification destinationExchange(String exchange) {
            this.destinationExchange = exchange;
            return this;
        }

        @Override
        public FakeBindingSpecification key(String key) {
            this.key = key;
            return this;
        }

        @Override
        public FakeBindingSpecification argument(String key, Object value) {
            this.arguments.put(key, value);
            return this;
        }

        @Override
        public FakeBindingSpecification arguments(Map<String, Object> arguments) {
            this.arguments.clear();
            if (arguments != null) {
                this.arguments.putAll(arguments);
            }
            return this;
        }

        @Override
        public void bind() {
            this.bound = true;
        }

        @Override
        public void unbind() {
            this.unbound = true;
        }
    }

    private static final class FakeUnbindSpecification extends FakeBindingSpecification {
    }

    private static final class FakePublisherBuilder implements PublisherBuilder {
        private final FakePublisher publisher;
        private Resource.StateListener[] listeners;
        private Duration publishTimeout;
        private String exchange;
        private String key;
        private String queue;

        private FakePublisherBuilder(FakePublisher publisher) {
            this.publisher = publisher;
        }

        @Override
        public PublisherBuilder listeners(Resource.StateListener... listeners) {
            this.listeners = listeners == null ? null : Arrays.copyOf(listeners, listeners.length);
            return this;
        }

        @Override
        public PublisherBuilder publishTimeout(Duration timeout) {
            this.publishTimeout = timeout;
            return this;
        }

        @Override
        public Publisher build() {
            return this.publisher;
        }

        @Override
        public PublisherBuilder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        @Override
        public PublisherBuilder key(String key) {
            this.key = key;
            return this;
        }

        @Override
        public PublisherBuilder queue(String queue) {
            this.queue = queue;
            return this;
        }
    }

    private static final class FakePublisher implements Publisher {
        private final List<FakeMessage> published = new ArrayList<>();
        private Publisher.Status status = Publisher.Status.ACCEPTED;
        private boolean closed;

        @Override
        public Message message() {
            return new FakeMessage();
        }

        @Override
        public Message message(byte[] body) {
            return new FakeMessage().body(body);
        }

        @Override
        public void publish(Message message, Publisher.Callback callback) {
            FakeMessage fakeMessage = (FakeMessage) message;
            this.published.add(fakeMessage);
            callback.handle(new FakePublisherContext(fakeMessage, this.status, null));
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FakePublisherContext implements Publisher.Context {
        private final Message message;
        private final Publisher.Status status;
        private final Throwable failureCause;

        private FakePublisherContext(Message message, Publisher.Status status, Throwable failureCause) {
            this.message = message;
            this.status = status;
            this.failureCause = failureCause;
        }

        @Override
        public Message message() {
            return this.message;
        }

        @Override
        public Publisher.Status status() {
            return this.status;
        }

        @Override
        public Throwable failureCause() {
            return this.failureCause;
        }
    }

    private static final class FakeConsumerBuilder implements ConsumerBuilder {
        private final FakeConsumer consumer;
        private String queue;
        private Consumer.MessageHandler messageHandler;
        private int initialCredits;
        private int priority;
        private Resource.StateListener[] listeners;
        private FakeConnection owner;

        private FakeConsumerBuilder(FakeConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public ConsumerBuilder queue(String queue) {
            this.queue = queue;
            return this;
        }

        @Override
        public ConsumerBuilder messageHandler(Consumer.MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
            return this;
        }

        @Override
        public ConsumerBuilder initialCredits(int initialCredits) {
            this.initialCredits = initialCredits;
            return this;
        }

        @Override
        public ConsumerBuilder preSettled() {
            return this;
        }

        @Override
        public ConsumerBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public ConsumerBuilder listeners(Resource.StateListener... listeners) {
            this.listeners = listeners == null ? null : Arrays.copyOf(listeners, listeners.length);
            return this;
        }

        @Override
        public ConsumerBuilder.StreamOptions stream() {
            throw new UnsupportedOperationException("Streams are not used by these tests");
        }

        @Override
        public ConsumerBuilder subscriptionListener(ConsumerBuilder.SubscriptionListener listener) {
            return this;
        }

        @Override
        public Consumer build() {
            FakeConsumer builtConsumer = this.owner == null ? this.consumer : new FakeConsumer(this.queue);
            if (this.owner != null) {
                this.owner.createdConsumers.add(builtConsumer);
            }
            if (this.messageHandler != null && this.owner != null && this.owner.nextDelivery != null
                    && !this.owner.nextDeliveryDelivered) {
                this.owner.nextDeliveryDelivered = true;
                this.messageHandler.handle(this.owner.nextDeliveryContext, this.owner.nextDelivery);
            }
            return builtConsumer;
        }
    }

    private static final class FakeConsumer implements Consumer {
        private final String queue;
        private boolean paused;
        private boolean closed;

        private FakeConsumer() {
            this(null);
        }

        private FakeConsumer(String queue) {
            this.queue = queue;
        }

        @Override
        public void pause() {
            this.paused = true;
        }

        @Override
        public long unsettledMessageCount() {
            return 0;
        }

        @Override
        public void unpause() {
            this.paused = false;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FakeRequesterBuilder implements RequesterBuilder {
        private final FakeRequester requester;
        private String replyToQueue;
        private Supplier<Object> correlationIdSupplier;
        private Duration requestTimeout;

        private FakeRequesterBuilder(FakeRequester requester) {
            this.requester = requester;
        }

        @Override
        public RequesterBuilder.RequesterAddressBuilder requestAddress() {
            throw new UnsupportedOperationException("Request address builder is not used by these tests");
        }

        @Override
        public RequesterBuilder replyToQueue(String queue) {
            this.replyToQueue = queue;
            return this;
        }

        @Override
        public RequesterBuilder correlationIdSupplier(Supplier<Object> supplier) {
            this.correlationIdSupplier = supplier;
            return this;
        }

        @Override
        public RequesterBuilder requestPostProcessor(BiFunction<Message, Object, Message> postProcessor) {
            return this;
        }

        @Override
        public RequesterBuilder correlationIdExtractor(Function<Message, Object> extractor) {
            return this;
        }

        @Override
        public RequesterBuilder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        @Override
        public Requester build() {
            return this.requester;
        }
    }

    private static final class FakeRequester implements Requester {
        private final List<FakeMessage> published = new ArrayList<>();
        private FakeConnection owner;
        private boolean closed;

        @Override
        public Message message() {
            return new FakeMessage();
        }

        @Override
        public Message message(byte[] body) {
            return new FakeMessage().body(body);
        }

        @Override
        public CompletableFuture<Message> publish(Message message) {
            this.published.add((FakeMessage) message);
            return CompletableFuture.completedFuture(this.owner.requestReply);
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static class RecordingConsumerContext implements Consumer.Context {
        private int accepts;
        private int discards;
        private int requeues;

        @Override
        public void accept() {
            this.accepts++;
        }

        @Override
        public void discard() {
            this.discards++;
        }

        @Override
        public void discard(Map<String, Object> annotations) {
            this.discard();
        }

        @Override
        public void requeue() {
            this.requeues++;
        }

        @Override
        public void requeue(Map<String, Object> annotations) {
            this.requeue();
        }

        @Override
        public Consumer.BatchContext batch(int size) {
            return new RecordingBatchConsumerContext(size);
        }
    }

    private static final class RecordingBatchConsumerContext extends RecordingConsumerContext
            implements Consumer.BatchContext {
        private final int size;
        private final List<Consumer.Context> contexts = new ArrayList<>();

        private RecordingBatchConsumerContext(int size) {
            this.size = size;
        }

        @Override
        public void add(Consumer.Context context) {
            this.contexts.add(context);
        }

        @Override
        public int size() {
            return this.size;
        }
    }

    private static final class FakeMessage implements Message {
        private Object messageId;
        private Object correlationId;
        private byte[] userId;
        private String to;
        private String subject;
        private String replyTo;
        private String contentType;
        private String contentEncoding;
        private long absoluteExpiryTime;
        private long creationTime;
        private String groupId;
        private int groupSequence;
        private String replyToGroupId;
        private byte[] body = new byte[0];
        private boolean durable;
        private byte priority;
        private Duration ttl;
        private String addressExchange;
        private String addressKey;
        private String addressQueue;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final Map<String, Object> annotations = new LinkedHashMap<>();

        @Override
        public Object messageId() {
            return this.messageId;
        }

        @Override
        public String messageIdAsString() {
            return this.messageId == null ? null : this.messageId.toString();
        }

        @Override
        public long messageIdAsLong() {
            return ((Number) this.messageId).longValue();
        }

        @Override
        public byte[] messageIdAsBinary() {
            return (byte[]) this.messageId;
        }

        @Override
        public UUID messageIdAsUuid() {
            return (UUID) this.messageId;
        }

        @Override
        public Object correlationId() {
            return this.correlationId;
        }

        @Override
        public String correlationIdAsString() {
            return this.correlationId == null ? null : this.correlationId.toString();
        }

        @Override
        public long correlationIdAsLong() {
            return ((Number) this.correlationId).longValue();
        }

        @Override
        public byte[] correlationIdAsBinary() {
            return (byte[]) this.correlationId;
        }

        @Override
        public UUID correlationIdAsUuid() {
            return (UUID) this.correlationId;
        }

        @Override
        public byte[] userId() {
            return this.userId;
        }

        @Override
        public String to() {
            return this.to;
        }

        @Override
        public String subject() {
            return this.subject;
        }

        @Override
        public String replyTo() {
            return this.replyTo;
        }

        @Override
        public FakeMessage messageId(Object messageId) {
            this.messageId = messageId;
            return this;
        }

        @Override
        public FakeMessage messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        @Override
        public FakeMessage messageId(long messageId) {
            this.messageId = messageId;
            return this;
        }

        @Override
        public FakeMessage messageId(byte[] messageId) {
            this.messageId = messageId;
            return this;
        }

        @Override
        public FakeMessage messageId(UUID messageId) {
            this.messageId = messageId;
            return this;
        }

        @Override
        public FakeMessage correlationId(Object correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Override
        public FakeMessage correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Override
        public FakeMessage correlationId(long correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Override
        public FakeMessage correlationId(byte[] correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Override
        public FakeMessage correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        @Override
        public FakeMessage userId(byte[] userId) {
            this.userId = userId;
            return this;
        }

        @Override
        public FakeMessage to(String to) {
            this.to = to;
            return this;
        }

        @Override
        public FakeMessage subject(String subject) {
            this.subject = subject;
            return this;
        }

        @Override
        public FakeMessage replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        @Override
        public FakeMessage contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        @Override
        public FakeMessage contentEncoding(String contentEncoding) {
            this.contentEncoding = contentEncoding;
            return this;
        }

        @Override
        public FakeMessage absoluteExpiryTime(long absoluteExpiryTime) {
            this.absoluteExpiryTime = absoluteExpiryTime;
            return this;
        }

        @Override
        public FakeMessage creationTime(long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        @Override
        public FakeMessage groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        @Override
        public FakeMessage groupSequence(int groupSequence) {
            this.groupSequence = groupSequence;
            return this;
        }

        @Override
        public FakeMessage replyToGroupId(String replyToGroupId) {
            this.replyToGroupId = replyToGroupId;
            return this;
        }

        @Override
        public String contentType() {
            return this.contentType;
        }

        @Override
        public String contentEncoding() {
            return this.contentEncoding;
        }

        @Override
        public long absoluteExpiryTime() {
            return this.absoluteExpiryTime;
        }

        @Override
        public long creationTime() {
            return this.creationTime;
        }

        @Override
        public String groupId() {
            return this.groupId;
        }

        @Override
        public int groupSequence() {
            return this.groupSequence;
        }

        @Override
        public String replyToGroupId() {
            return this.replyToGroupId;
        }

        @Override
        public Object property(String key) {
            return this.properties.get(key);
        }

        @Override
        public FakeMessage property(String key, boolean value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, byte value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, short value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, int value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, long value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyUnsigned(String key, byte value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyUnsigned(String key, short value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyUnsigned(String key, int value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyUnsigned(String key, long value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, float value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, double value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyDecimal32(String key, BigDecimal value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyDecimal64(String key, BigDecimal value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyDecimal128(String key, BigDecimal value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, char value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertyTimestamp(String key, long value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, UUID value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, byte[] value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage property(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public FakeMessage propertySymbol(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        @Override
        public boolean hasProperty(String key) {
            return this.properties.containsKey(key);
        }

        @Override
        public boolean hasProperties() {
            return !this.properties.isEmpty();
        }

        @Override
        public Object removeProperty(String key) {
            return this.properties.remove(key);
        }

        @Override
        public FakeMessage forEachProperty(BiConsumer<String, Object> action) {
            this.properties.forEach(action);
            return this;
        }

        @Override
        public FakeMessage body(byte[] body) {
            this.body = body;
            return this;
        }

        @Override
        public byte[] body() {
            return this.body;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I, O> O body(Message.Converter<I, O> converter) {
            return converter.convert((I) this.body);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I, O> O body(Message.SectionsConverter<I, O> converter) {
            return converter.convert(List.of((I) this.body));
        }

        @Override
        public FakeMessage durable(boolean durable) {
            this.durable = durable;
            return this;
        }

        @Override
        public boolean durable() {
            return this.durable;
        }

        @Override
        public long deliveryCount() {
            return 0;
        }

        @Override
        public FakeMessage priority(byte priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public byte priority() {
            return this.priority;
        }

        @Override
        public FakeMessage ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        @Override
        public Duration ttl() {
            return this.ttl;
        }

        @Override
        public boolean firstAcquirer() {
            return false;
        }

        @Override
        public Object annotation(String key) {
            return this.annotations.get(key);
        }

        @Override
        public FakeMessage annotation(String key, Object value) {
            this.annotations.put(key, value);
            return this;
        }

        @Override
        public boolean hasAnnotation(String key) {
            return this.annotations.containsKey(key);
        }

        @Override
        public boolean hasAnnotations() {
            return !this.annotations.isEmpty();
        }

        @Override
        public Object removeAnnotation(String key) {
            return this.annotations.remove(key);
        }

        @Override
        public FakeMessage forEachAnnotation(BiConsumer<String, Object> action) {
            this.annotations.forEach(action);
            return this;
        }

        @Override
        public MessageAddressBuilder toAddress() {
            return new FakeAddressBuilder(this, false);
        }

        @Override
        public MessageAddressBuilder replyToAddress() {
            return new FakeAddressBuilder(this, true);
        }
    }

    private static final class FakeAddressBuilder implements Message.MessageAddressBuilder {
        private final FakeMessage message;
        private final boolean replyTo;

        private FakeAddressBuilder(FakeMessage message, boolean replyTo) {
            this.message = message;
            this.replyTo = replyTo;
        }

        @Override
        public Message.MessageAddressBuilder exchange(String exchange) {
            this.message.addressExchange = exchange;
            return this;
        }

        @Override
        public Message.MessageAddressBuilder key(String key) {
            this.message.addressKey = key;
            return this;
        }

        @Override
        public Message.MessageAddressBuilder queue(String queue) {
            if (this.replyTo) {
                this.message.replyTo = queue;
            } else {
                this.message.addressQueue = queue;
            }
            return this;
        }

        @Override
        public Message message() {
            return this.message;
        }
    }
}
