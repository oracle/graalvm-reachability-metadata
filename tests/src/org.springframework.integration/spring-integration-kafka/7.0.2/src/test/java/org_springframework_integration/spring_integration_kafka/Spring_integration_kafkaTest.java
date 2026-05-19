/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.AttributeAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.AcknowledgmentCallback;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.kafka.channel.PollableKafkaChannel;
import org.springframework.integration.kafka.channel.PublishSubscribeKafkaChannel;
import org.springframework.integration.kafka.channel.SubscribableKafkaChannel;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.inbound.KafkaErrorSendingMessageRecoverer;
import org.springframework.integration.kafka.inbound.KafkaInboundGateway;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.inbound.KafkaMessageSource;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.integration.kafka.support.KafkaIntegrationHeaders;
import org.springframework.integration.kafka.support.KafkaSendFailureException;
import org.springframework.integration.kafka.support.RawRecordHeaderErrorMessageStrategy;
import org.springframework.integration.support.ErrorMessageUtils;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerProperties;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.TopicPartitionOffset;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_integration_kafkaTest {

    private static final String TOPIC = "spring.integration.kafka.orders";

    @Test
    void producerMessageHandlerDslSendsRecordsAndPublishesSuccessMessages() throws Exception {
        TrackingProducerFactory producerFactory = new TrackingProducerFactory();
        QueueChannel successChannel = new QueueChannel();

        KafkaProducerMessageHandler<String, String> handler = Kafka
                .<String, String>outboundChannelAdapter(producerFactory)
                .topic(TOPIC)
                .messageKey("order-1")
                .partitionId(0)
                .sync(true)
                .sendTimeout(500)
                .sendSuccessChannel(successChannel)
                .getObject();
        handler.setTimestampExpression(new SpelExpressionParser().parseExpression("123456789L"));
        handler.setBeanFactory(integrationBeanFactory());
        handler.afterPropertiesSet();
        handler.start();

        handler.handleMessage(MessageBuilder.withPayload("created").build());

        MockProducer<String, String> producer = producerFactory.lastProducer();
        assertThat(producer.history()).hasSize(1);
        ProducerRecord<String, String> record = producer.history().get(0);
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(record.value()).isEqualTo("created");
        assertThat(record.partition()).isEqualTo(0);
        assertThat(record.timestamp()).isEqualTo(123456789L);

        Message<?> successMessage = successChannel.receive(1_000);
        assertThat(successMessage).isNotNull();
        assertThat(successMessage.getPayload()).isNotNull();
        assertThat(handler.getComponentType()).isEqualTo("kafka:outbound-channel-adapter");
        assertThat(handler.isRunning()).isTrue();

        handler.stop();
    }

    @Test
    void outboundAdapterPublishesSendFuturesWithCorrelationToken() throws Exception {
        TrackingProducerFactory producerFactory = new TrackingProducerFactory();
        QueueChannel futuresChannel = new QueueChannel();

        KafkaProducerMessageHandler<String, String> handler = Kafka
                .<String, String>outboundChannelAdapter(producerFactory)
                .topic(TOPIC)
                .messageKey("order-7")
                .futuresChannel(futuresChannel)
                .sendFailureChannel(new QueueChannel())
                .getObject();
        handler.setBeanFactory(integrationBeanFactory());
        handler.afterPropertiesSet();
        handler.start();

        String futureToken = "future-token-1";
        handler.handleMessage(MessageBuilder.withPayload("future-send")
                .setHeader(KafkaIntegrationHeaders.FUTURE_TOKEN, futureToken)
                .build());

        Message<?> futureMessage = futuresChannel.receive(1_000);
        assertThat(futureMessage).isNotNull();
        assertThat(futureMessage.getHeaders()).containsEntry(KafkaIntegrationHeaders.FUTURE_TOKEN, futureToken);
        assertThat(futureMessage.getPayload()).isInstanceOf(CompletableFuture.class);

        CompletableFuture<?> sendFuture = (CompletableFuture<?>) futureMessage.getPayload();
        assertThat(sendFuture.get(1, TimeUnit.SECONDS)).isInstanceOfSatisfying(SendResult.class, (sendResult) -> {
            ProducerRecord<?, ?> producerRecord = ((SendResult<?, ?>) sendResult).getProducerRecord();
            assertThat(producerRecord.topic()).isEqualTo(TOPIC);
            assertThat(producerRecord.key()).isEqualTo("order-7");
            assertThat(producerRecord.value()).isEqualTo("future-send");
            assertThat(producerRecord.headers().lastHeader(KafkaIntegrationHeaders.FUTURE_TOKEN)).isNull();
        });

        handler.stop();
    }

    @Test
    void pollableChannelUsesKafkaTemplateForSendsAndMessageSourceForReceives() {
        TrackingProducerFactory producerFactory = new TrackingProducerFactory();
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        TrackingConsumerFactory consumerFactory = new TrackingConsumerFactory();
        ConsumerProperties consumerProperties = topicConsumerProperties();
        KafkaMessageSource<String, String> source = new KafkaMessageSource<>(consumerFactory, consumerProperties);
        source.setRawMessageHeader(true);
        source.setPayloadType(String.class);
        PollableKafkaChannel channel = new PollableKafkaChannel(kafkaTemplate, source);
        channel.addInterceptor(new MarkingInterceptor());

        assertThat(channel.send(MessageBuilder.withPayload("submitted")
                .setHeader(KafkaHeaders.TOPIC, TOPIC)
                .setHeader(KafkaHeaders.KEY, "order-2")
                .setHeader(KafkaHeaders.PARTITION, 0)
                .build(), 1_000)).isTrue();
        assertThat(producerFactory.lastProducer().history()).hasSize(1);
        assertThat(producerFactory.lastProducer().history().get(0).value()).isEqualTo("submitted");

        try {
            TopicPartition topicPartition = new TopicPartition(TOPIC, 0);
            consumerFactory.consumer().updateBeginningOffsets(Map.of(topicPartition, 0L));
            consumerFactory.consumer().updateEndOffsets(Map.of(topicPartition, 1L));
            source.start();
            consumerFactory.consumer().schedulePollTask(() -> {
                consumerFactory.consumer().rebalance(List.of(topicPartition));
                consumerFactory.consumer().addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "order-2", "accepted"));
            });

            Message<?> received = channel.receive(1_000);

            assertThat(received).isNotNull();
            assertThat(received.getPayload()).isEqualTo("accepted");
            assertThat(received.getHeaders()).containsEntry("intercepted", true);
            assertThat(received.getHeaders()).containsEntry(KafkaHeaders.RECEIVED_TOPIC, TOPIC);
            assertThat(received.getHeaders().get(KafkaHeaders.RAW_DATA)).isInstanceOf(ConsumerRecord.class);
            assertThat(source.getComponentType()).isEqualTo("kafka:message-source");
            assertThat(source.getAssignedPartitions()).containsExactly(topicPartition);
        } finally {
            source.destroy();
            kafkaTemplate.destroy();
        }
    }

    @Test
    void messageSourceAcknowledgmentCallbackDefersOutOfOrderCommits() {
        TrackingConsumerFactory consumerFactory = new TrackingConsumerFactory();
        TopicPartition topicPartition = new TopicPartition(TOPIC, 0);
        KafkaMessageSource<String, String> source = new KafkaMessageSource<>(consumerFactory,
                assignedPartitionConsumerProperties());
        source.setPayloadType(String.class);

        try {
            consumerFactory.consumer().updateBeginningOffsets(Map.of(topicPartition, 0L));
            consumerFactory.consumer().updateEndOffsets(Map.of(topicPartition, 2L));
            source.start();
            consumerFactory.consumer().schedulePollTask(() -> {
                consumerFactory.consumer().addRecord(new ConsumerRecord<>(TOPIC, 0, 0L, "order-5", "packed"));
                consumerFactory.consumer().addRecord(new ConsumerRecord<>(TOPIC, 0, 1L, "order-6", "shipped"));
            });

            Message<?> first = source.receive();
            Message<?> second = source.receive();
            AcknowledgmentCallback firstAcknowledgment = acknowledgmentCallback(first);
            AcknowledgmentCallback secondAcknowledgment = acknowledgmentCallback(second);
            firstAcknowledgment.noAutoAck();
            secondAcknowledgment.noAutoAck();

            assertThat(first.getPayload()).isEqualTo("packed");
            assertThat(second.getPayload()).isEqualTo("shipped");
            assertThat(first.getHeaders()).containsEntry(KafkaMessageSource.REMAINING_RECORDS, 1);
            assertThat(second.getHeaders()).containsEntry(KafkaMessageSource.REMAINING_RECORDS, 0);

            secondAcknowledgment.acknowledge(AcknowledgmentCallback.Status.ACCEPT);

            assertThat(secondAcknowledgment.isAcknowledged()).isTrue();
            assertThat(consumerFactory.consumer().committed(Set.of(topicPartition))).doesNotContainKey(topicPartition);

            firstAcknowledgment.acknowledge(AcknowledgmentCallback.Status.ACCEPT);

            OffsetAndMetadata committedOffset = consumerFactory.consumer().committed(Set.of(topicPartition))
                    .get(topicPartition);
            assertThat(firstAcknowledgment.isAcknowledged()).isTrue();
            assertThat(committedOffset).isNotNull();
            assertThat(committedOffset.offset()).isEqualTo(2L);
        } finally {
            source.destroy();
        }
    }

    @Test
    void inboundChannelAdapterDslBuildsConfigurableKafkaMessageSource() {
        TrackingConsumerFactory consumerFactory = new TrackingConsumerFactory();
        ConsumerProperties consumerProperties = assignedPartitionConsumerProperties();

        KafkaMessageSource<String, String> source = Kafka
                .inboundChannelAdapter(consumerFactory, consumerProperties, false)
                .messageConverter(new MessagingMessageConverter())
                .payloadType(String.class)
                .rawMessageHeader(true)
                .getObject();

        assertThat(source.getConsumerProperties()).isSameAs(consumerProperties);
        assertThat(source.isRunning()).isFalse();
        source.setCloseTimeout(Duration.ofMillis(100));
        source.start();
        assertThat(source.isRunning()).isTrue();
        source.pause();
        source.resume();
        source.stop();
        assertThat(source.isRunning()).isFalse();
        source.destroy();
    }

    @Test
    void messageDrivenAdapterAndInboundGatewayExposeLifecycleControlsWithoutStartingKafka() {
        TrackingConsumerFactory consumerFactory = new TrackingConsumerFactory();
        TrackingProducerFactory producerFactory = new TrackingProducerFactory();
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        ContainerProperties containerProperties = new ContainerProperties(TOPIC);
        containerProperties.setGroupId("integration-tests");
        containerProperties.setPollTimeout(10L);
        KafkaMessageListenerContainer<String, String> adapterContainer =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        KafkaMessageListenerContainer<String, String> gatewayContainer =
                new KafkaMessageListenerContainer<>(consumerFactory, new ContainerProperties(TOPIC));

        KafkaMessageDrivenChannelAdapter<String, String> adapter = Kafka
                .messageDrivenChannelAdapter(adapterContainer, KafkaMessageDrivenChannelAdapter.ListenerMode.record)
                .recordMessageConverter(new MessagingMessageConverter())
                .payloadType(String.class)
                .filterInRetry(true)
                .recordFilterStrategy(record -> false)
                .ackDiscarded(false)
                .getObject();
        adapter.setBindSourceRecord(true);

        KafkaInboundGateway<String, String, String> gateway = new KafkaInboundGateway<>(gatewayContainer,
                kafkaTemplate);
        gateway.setMessageConverter(new MessagingMessageConverter());
        gateway.setPayloadType(String.class);
        gateway.setBindSourceRecord(true);

        assertThat(adapter.getComponentType()).isEqualTo("kafka:message-driven-channel-adapter");
        assertThat(gateway.getComponentType()).isEqualTo("kafka:inbound-gateway");

        adapter.pause();
        gateway.pause();
        adapter.resume();
        gateway.resume();
        assertThat(adapter.beforeShutdown()).isEqualTo(adapter.getPhase());
        assertThat(gateway.beforeShutdown()).isEqualTo(gateway.getPhase());
        assertThat(adapter.afterShutdown()).isEqualTo(adapter.getPhase());
        assertThat(gateway.afterShutdown()).isEqualTo(gateway.getPhase());

        kafkaTemplate.destroy();
    }

    @Test
    void subscribableChannelVariantsManageSubscriptionsWithoutStartingContainers() {
        TrackingConsumerFactory consumerFactory = new TrackingConsumerFactory();
        TrackingProducerFactory producerFactory = new TrackingProducerFactory();
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(consumerFactory);
        containerFactory.getContainerProperties().setPollTimeout(10L);
        MessageHandler messageHandler = message -> assertThat(message).isNotNull();

        SubscribableKafkaChannel pointToPointChannel = Kafka.channel(kafkaTemplate, containerFactory, TOPIC)
                .getObject();
        pointToPointChannel.setBeanName("pointToPointKafkaChannel");
        pointToPointChannel.setBeanFactory(integrationBeanFactory());
        pointToPointChannel.setGroupId("point-to-point-group");
        pointToPointChannel.setPhase(17);
        pointToPointChannel.setAutoStartup(false);
        pointToPointChannel.setMessageConverter(new MessagingMessageConverter());
        pointToPointChannel.afterPropertiesSet();

        PublishSubscribeKafkaChannel publishSubscribeChannel = Kafka
                .publishSubscribeChannel(kafkaTemplate, containerFactory, TOPIC)
                .getObject();
        publishSubscribeChannel.setBeanName("publishSubscribeKafkaChannel");
        publishSubscribeChannel.setBeanFactory(integrationBeanFactory());
        publishSubscribeChannel.setGroupId("publish-subscribe-group");
        publishSubscribeChannel.afterPropertiesSet();

        assertThat(pointToPointChannel.subscribe(messageHandler)).isTrue();
        assertThat(pointToPointChannel.subscribe(messageHandler)).isFalse();
        assertThat(pointToPointChannel.unsubscribe(messageHandler)).isTrue();
        assertThat(pointToPointChannel.getPhase()).isEqualTo(17);
        assertThat(pointToPointChannel.isAutoStartup()).isFalse();
        assertThat(pointToPointChannel.isRunning()).isFalse();

        assertThat(publishSubscribeChannel.subscribe(messageHandler)).isTrue();
        assertThat(publishSubscribeChannel.unsubscribe(messageHandler)).isTrue();

        kafkaTemplate.destroy();
    }

    @Test
    void errorStrategiesAndRecoverersPreserveKafkaRecordContext() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 42L, "order-3", "failed");
        Message<String> inputMessage = MessageBuilder.withPayload("failed")
                .setHeader(KafkaHeaders.RAW_DATA, record)
                .build();
        AttributeAccessor attributes = ErrorMessageUtils.getAttributeAccessor(inputMessage, null);
        attributes.setAttribute(KafkaHeaders.RAW_DATA, record);

        ErrorMessage errorMessage = new RawRecordHeaderErrorMessageStrategy()
                .buildErrorMessage(new IllegalStateException("boom"), attributes);

        assertThat(errorMessage.getOriginalMessage()).isSameAs(inputMessage);
        assertThat(errorMessage.getHeaders()).containsEntry(KafkaHeaders.RAW_DATA, record)
                .containsEntry("sourceData", record);

        QueueChannel errors = new QueueChannel();
        KafkaErrorSendingMessageRecoverer recoverer = new KafkaErrorSendingMessageRecoverer(errors,
                new RawRecordHeaderErrorMessageStrategy());
        recoverer.accept(record, new IllegalArgumentException("not recovered"));

        Message<?> recovered = errors.receive(1_000);
        assertThat(recovered).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) recovered).getPayload()).isInstanceOfSatisfying(MessagingException.class,
                (exception) -> assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class));
        assertThat(recovered.getHeaders()).containsEntry(KafkaHeaders.RAW_DATA, record)
                .containsEntry("sourceData", record);
    }

    @Test
    void sendFailureExceptionExposesFailedProducerRecord() {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, 0, "order-4", "failed");
        Message<String> failedMessage = MessageBuilder.withPayload("failed").build();

        KafkaSendFailureException exception = new KafkaSendFailureException(failedMessage, record,
                new IllegalStateException("send failed"));

        assertThat(exception.getFailedMessage()).isSameAs(failedMessage);
        assertThat(exception.getRecord()).isSameAs(record);
        assertThat(exception.getCause()).hasMessage("send failed");
        assertThat(exception.toString()).contains(TOPIC);
    }

    private static AcknowledgmentCallback acknowledgmentCallback(Message<?> message) {
        assertThat(message).isNotNull();
        AcknowledgmentCallback acknowledgmentCallback = new IntegrationMessageHeaderAccessor(message)
                .getAcknowledgmentCallback();
        assertThat(acknowledgmentCallback).isNotNull();
        assertThat(acknowledgmentCallback.isAcknowledged()).isFalse();
        return acknowledgmentCallback;
    }

    private static ConsumerProperties assignedPartitionConsumerProperties() {
        ConsumerProperties consumerProperties = new ConsumerProperties(new TopicPartitionOffset(TOPIC, 0, 0L));
        configureConsumerProperties(consumerProperties);
        return consumerProperties;
    }

    private static ConsumerProperties topicConsumerProperties() {
        ConsumerProperties consumerProperties = new ConsumerProperties(TOPIC);
        configureConsumerProperties(consumerProperties);
        return consumerProperties;
    }

    private static void configureConsumerProperties(ConsumerProperties consumerProperties) {
        consumerProperties.setGroupId("integration-tests");
        consumerProperties.setClientId("spring-integration-kafka-test");
        consumerProperties.setPollTimeout(10L);
    }

    private static DefaultListableBeanFactory integrationBeanFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("integrationEvaluationContext", new StandardEvaluationContext());
        return beanFactory;
    }

    private static final class MarkingInterceptor implements ChannelInterceptor {

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            return message;
        }

        @Override
        public Message<?> postReceive(Message<?> message, MessageChannel channel) {
            return mark(message);
        }

        private Message<?> mark(Message<?> message) {
            return MessageBuilder.fromMessage(message)
                    .setHeader("intercepted", true)
                    .build();
        }
    }

    private static final class TrackingProducerFactory implements ProducerFactory<String, String> {

        private MockProducer<String, String> lastProducer;

        @Override
        public Producer<String, String> createProducer() {
            this.lastProducer = new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
            return this.lastProducer;
        }

        @Override
        public Map<String, Object> getConfigurationProperties() {
            return Collections.emptyMap();
        }

        private MockProducer<String, String> lastProducer() {
            assertThat(this.lastProducer).isNotNull();
            return this.lastProducer;
        }
    }

    private static final class TrackingConsumerFactory implements ConsumerFactory<String, String> {

        private final MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

        @Override
        public Consumer<String, String> createConsumer(String groupId, String clientIdPrefix, String clientIdSuffix,
                Properties properties) {
            return this.consumer;
        }

        @Override
        public boolean isAutoCommit() {
            return false;
        }

        @Override
        public Map<String, Object> getConfigurationProperties() {
            return Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        }

        private MockConsumer<String, String> consumer() {
            return this.consumer;
        }
    }
}
