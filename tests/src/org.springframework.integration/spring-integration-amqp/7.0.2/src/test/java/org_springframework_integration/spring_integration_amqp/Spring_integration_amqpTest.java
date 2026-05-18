/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_integration.spring_integration_amqp;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.AttributeAccessorSupport;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter;
import org.springframework.integration.amqp.outbound.AmqpOutboundEndpoint;
import org.springframework.integration.amqp.support.AmqpMessageHeaderErrorMessageStrategy;
import org.springframework.integration.amqp.support.DefaultAmqpHeaderMapper;
import org.springframework.integration.amqp.support.MappingUtils;
import org.springframework.integration.amqp.support.NackedAmqpMessageException;
import org.springframework.integration.amqp.support.ReturnedAmqpMessageException;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_integration_amqpTest {

    @Test
    void inboundHeaderMapperCopiesStandardConsumerAndUserHeaders() {
        MessageProperties properties = new MessageProperties();
        Date timestamp = new Date(1_700_000_000_000L);
        properties.setAppId("orders-app");
        properties.setClusterId("cluster-a");
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setContentLength(42L);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setCorrelationId("correlation-1");
        properties.setReceivedDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setDeliveryTag(123L);
        properties.setExpiration("60000");
        properties.setMessageCount(3);
        properties.setMessageId("message-1");
        properties.setPriority(7);
        properties.setReceivedDelayLong(25L);
        properties.setReceivedExchange("orders.exchange");
        properties.setReceivedRoutingKey("orders.created");
        properties.setRedelivered(true);
        properties.setReplyTo("reply.queue");
        properties.setTimestamp(timestamp);
        properties.setType("order-created");
        properties.setReceivedUserId("broker-user");
        properties.setRetryCount(2L);
        properties.setConsumerTag("consumer-tag");
        properties.setConsumerQueue("orders.queue");
        properties.setHeader("tenant", "acme");
        properties.setHeader("traceNumber", 99);

        DefaultAmqpHeaderMapper mapper = DefaultAmqpHeaderMapper.inboundMapper();
        Map<String, Object> headers = mapper.toHeadersFromRequest(properties);

        assertThat(headers)
                .containsEntry(AmqpHeaders.APP_ID, "orders-app")
                .containsEntry(AmqpHeaders.CLUSTER_ID, "cluster-a")
                .containsEntry(AmqpHeaders.CONTENT_ENCODING, StandardCharsets.UTF_8.name())
                .containsEntry(AmqpHeaders.CONTENT_LENGTH, 42L)
                .containsEntry(MessageHeaders.CONTENT_TYPE, MessageProperties.CONTENT_TYPE_JSON)
                .containsEntry(AmqpHeaders.CORRELATION_ID, "correlation-1")
                .containsEntry(AmqpHeaders.RECEIVED_DELIVERY_MODE, MessageDeliveryMode.PERSISTENT)
                .containsEntry(AmqpHeaders.DELIVERY_TAG, 123L)
                .containsEntry(AmqpHeaders.EXPIRATION, "60000")
                .containsEntry(AmqpHeaders.MESSAGE_COUNT, 3)
                .containsEntry(AmqpHeaders.MESSAGE_ID, "message-1")
                .containsEntry(IntegrationMessageHeaderAccessor.PRIORITY, 7)
                .containsEntry(AmqpHeaders.RECEIVED_DELAY, 25L)
                .containsEntry(AmqpHeaders.RECEIVED_EXCHANGE, "orders.exchange")
                .containsEntry(AmqpHeaders.RECEIVED_ROUTING_KEY, "orders.created")
                .containsEntry(AmqpHeaders.REDELIVERED, true)
                .containsEntry(AmqpHeaders.REPLY_TO, "reply.queue")
                .containsEntry(AmqpHeaders.TIMESTAMP, timestamp)
                .containsEntry(AmqpHeaders.TYPE, "order-created")
                .containsEntry(AmqpHeaders.RECEIVED_USER_ID, "broker-user")
                .containsEntry(AmqpHeaders.RETRY_COUNT, 2L)
                .containsEntry(AmqpHeaders.CONSUMER_TAG, "consumer-tag")
                .containsEntry(AmqpHeaders.CONSUMER_QUEUE, "orders.queue")
                .containsEntry("tenant", "acme")
                .containsEntry("traceNumber", 99);
    }

    @Test
    void outboundHeaderMapperPopulatesAmqpPropertiesAndFiltersUnsafeXHeaders() {
        Message<String> springMessage = MessageBuilder.withPayload("created")
                .setHeader(AmqpHeaders.APP_ID, "producer-app")
                .setHeader(AmqpHeaders.CLUSTER_ID, "cluster-b")
                .setHeader(AmqpHeaders.CONTENT_ENCODING, StandardCharsets.UTF_8.name())
                .setHeader(AmqpHeaders.CONTENT_LENGTH, 128L)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .setHeader(AmqpHeaders.CORRELATION_ID, "correlation-2")
                .setHeader(AmqpHeaders.DELAY, 250L)
                .setHeader(AmqpHeaders.DELIVERY_MODE, MessageDeliveryMode.PERSISTENT)
                .setHeader(AmqpHeaders.EXPIRATION, "120000")
                .setHeader(AmqpHeaders.MESSAGE_ID, "outbound-message")
                .setHeader(IntegrationMessageHeaderAccessor.PRIORITY, 4)
                .setHeader(AmqpHeaders.REPLY_TO, "amq.rabbitmq.reply-to")
                .setHeader(AmqpHeaders.TYPE, "order-command")
                .setHeader(AmqpHeaders.USER_ID, "publisher")
                .setHeader(AmqpHeaders.RETRY_COUNT, 5L)
                .setHeader("businessKey", "order-123")
                .setHeader("x-internal", "must-not-leak")
                .build();
        MessageProperties properties = new MessageProperties();

        DefaultAmqpHeaderMapper mapper = DefaultAmqpHeaderMapper.outboundMapper();
        mapper.fromHeadersToRequest(springMessage.getHeaders(), properties);

        assertThat(properties.getAppId()).isEqualTo("producer-app");
        assertThat(properties.getClusterId()).isEqualTo("cluster-b");
        assertThat(properties.getContentEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(properties.getContentLength()).isEqualTo(128L);
        assertThat(properties.getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON.toString());
        assertThat(properties.getCorrelationId()).isEqualTo("correlation-2");
        assertThat(properties.getDelayLong()).isEqualTo(250L);
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getExpiration()).isEqualTo("120000");
        assertThat(properties.getMessageId()).isEqualTo("outbound-message");
        assertThat(properties.getPriority()).isEqualTo(4);
        assertThat(properties.getReplyTo()).isEqualTo("amq.rabbitmq.reply-to");
        assertThat(properties.getType()).isEqualTo("order-command");
        assertThat(properties.getUserId()).isEqualTo("publisher");
        assertThat(properties.getRetryCount()).isEqualTo(5L);
        assertThat(properties.getHeaders())
                .containsEntry("businessKey", "order-123")
                .doesNotContainKey("x-internal");
    }

    @Test
    void mappingUtilsBuildsAmqpMessagesWithMappedHeadersAndDefaultDeliveryMode() {
        Message<String> springMessage = MessageBuilder.withPayload("plain text")
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN)
                .setHeader("custom", "custom-value")
                .build();

        org.springframework.amqp.core.Message amqpMessage = MappingUtils.mapMessage(
                springMessage,
                new SimpleMessageConverter(),
                DefaultAmqpHeaderMapper.outboundMapper(),
                MessageDeliveryMode.NON_PERSISTENT,
                false);

        assertThat(new String(amqpMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo("plain text");
        assertThat(amqpMessage.getMessageProperties().getContentType()).isEqualTo(MimeTypeUtils.TEXT_PLAIN.toString());
        assertThat(amqpMessage.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
        assertThat(amqpMessage.getMessageProperties().getHeaders()).containsEntry("custom", "custom-value");
    }

    @Test
    void outboundEndpointConvertsAndSendsThroughRabbitTemplateWithoutBroker() {
        RecordingRabbitTemplate rabbitTemplate = new RecordingRabbitTemplate();
        AmqpOutboundEndpoint endpoint = new AmqpOutboundEndpoint(rabbitTemplate);
        endpoint.setExchangeName("orders.exchange");
        endpoint.setRoutingKey("orders.created");
        endpoint.setDefaultDeliveryMode(MessageDeliveryMode.PERSISTENT);
        endpoint.setBeanFactory(new DefaultListableBeanFactory());
        endpoint.afterPropertiesSet();

        Message<String> message = MessageBuilder.withPayload("created")
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN)
                .setHeader("businessKey", "order-321")
                .build();
        endpoint.handleMessage(message);

        assertThat(endpoint.getComponentType()).isEqualTo("amqp:outbound-channel-adapter");
        assertThat(rabbitTemplate.exchange).isEqualTo("orders.exchange");
        assertThat(rabbitTemplate.routingKey).isEqualTo("orders.created");
        assertThat(rabbitTemplate.sentMessage).isNotNull();
        assertThat(rabbitTemplate.correlationData).isNull();
        assertThat(new String(rabbitTemplate.sentMessage.getBody(), StandardCharsets.UTF_8)).isEqualTo("created");
        assertThat(rabbitTemplate.sentMessage.getMessageProperties().getContentType())
                .isEqualTo(MimeTypeUtils.TEXT_PLAIN.toString());
        assertThat(rabbitTemplate.sentMessage.getMessageProperties().getDeliveryMode())
                .isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(rabbitTemplate.sentMessage.getMessageProperties().getHeaders())
                .containsEntry("businessKey", "order-321");
    }

    @Test
    void inboundChannelAdapterConvertsAmqpMessageAndSendsSpringMessage() throws Exception {
        RecordingMessageListenerContainer listenerContainer = new RecordingMessageListenerContainer();
        QueueChannel outputChannel = new QueueChannel(1);
        AmqpInboundChannelAdapter adapter = new AmqpInboundChannelAdapter(listenerContainer);
        adapter.setOutputChannel(outputChannel);
        adapter.setBindSourceMessage(true);
        adapter.setBeanFactory(new DefaultListableBeanFactory());
        adapter.afterPropertiesSet();

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setReceivedExchange("orders.exchange");
        properties.setReceivedRoutingKey("orders.created");
        properties.setHeader("tenant", "acme");
        org.springframework.amqp.core.Message amqpMessage = new org.springframework.amqp.core.Message(
                "hello from rabbit".getBytes(StandardCharsets.UTF_8), properties);

        ChannelAwareMessageListener channelAwareListener = (ChannelAwareMessageListener) listenerContainer.listener;
        channelAwareListener.onMessage(amqpMessage, null);

        Message<?> received = outputChannel.receive(1_000L);
        assertThat(received).isNotNull();
        assertThat(received.getPayload()).isEqualTo("hello from rabbit");
        assertThat(received.getHeaders())
                .containsEntry(AmqpHeaders.RECEIVED_EXCHANGE, "orders.exchange")
                .containsEntry(AmqpHeaders.RECEIVED_ROUTING_KEY, "orders.created")
                .containsEntry("tenant", "acme")
                .containsEntry(IntegrationMessageHeaderAccessor.SOURCE_DATA, amqpMessage);
        assertThat(listenerContainer.afterPropertiesSetCalled).isTrue();
        assertThat(adapter.getComponentType()).isEqualTo("amqp:inbound-channel-adapter");
        assertThat(adapter.beforeShutdown()).isZero();
        assertThat(adapter.afterShutdown()).isZero();
    }

    @Test
    void errorMessageStrategyAddsRawAmqpMessageAndOriginalInputMessage() {
        Message<String> inputMessage = MessageBuilder.withPayload("failed").build();
        org.springframework.amqp.core.Message rawMessage = new org.springframework.amqp.core.Message(
                "raw".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        TestAttributeAccessor attributes = new TestAttributeAccessor();
        attributes.setAttribute("inputMessage", inputMessage);
        attributes.setAttribute(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE, rawMessage);
        RuntimeException failure = new RuntimeException("boom");

        ErrorMessage errorMessage = new AmqpMessageHeaderErrorMessageStrategy().buildErrorMessage(failure, attributes);

        assertThat(errorMessage.getPayload()).isSameAs(failure);
        assertThat(errorMessage.getOriginalMessage()).isSameAs(inputMessage);
        assertThat(errorMessage.getHeaders())
                .containsEntry(AmqpMessageHeaderErrorMessageStrategy.AMQP_RAW_MESSAGE, rawMessage)
                .containsEntry(IntegrationMessageHeaderAccessor.SOURCE_DATA, rawMessage);
    }

    @Test
    void amqpSpecificExceptionsExposeBrokerDiagnostics() {
        Message<String> failedSpringMessage = MessageBuilder.withPayload("payload").build();
        org.springframework.amqp.core.Message returnedAmqpMessage = new org.springframework.amqp.core.Message(
                "returned".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        ReturnedAmqpMessageException returned = new ReturnedAmqpMessageException(
                failedSpringMessage, returnedAmqpMessage, 312, "NO_ROUTE", "orders.exchange", "missing.key");
        NackedAmqpMessageException nacked = new NackedAmqpMessageException(
                failedSpringMessage, "correlation-data", "broker nack");

        assertThat(returned.getFailedMessage()).isSameAs(failedSpringMessage);
        assertThat(returned.getAmqpMessage()).isSameAs(returnedAmqpMessage);
        assertThat(returned.getReplyCode()).isEqualTo(312);
        assertThat(returned.getReplyText()).isEqualTo("NO_ROUTE");
        assertThat(returned.getExchange()).isEqualTo("orders.exchange");
        assertThat(returned.getRoutingKey()).isEqualTo("missing.key");
        assertThat(returned.toString()).contains("NO_ROUTE", "orders.exchange", "missing.key");

        assertThat(nacked.getFailedMessage()).isSameAs(failedSpringMessage);
        assertThat(nacked.getCorrelationData()).isEqualTo("correlation-data");
        assertThat(nacked.getNackReason()).isEqualTo("broker nack");
        assertThat(nacked.toString()).contains("correlation-data", "broker nack");
    }

    private static final class RecordingRabbitTemplate extends RabbitTemplate {

        private String exchange;

        private String routingKey;

        private org.springframework.amqp.core.Message sentMessage;

        private CorrelationData correlationData;

        private RecordingRabbitTemplate() {
            super(new NoOpConnectionFactory());
            setMessageConverter(new SimpleMessageConverter());
        }

        @Override
        public void send(String exchange, String routingKey, org.springframework.amqp.core.Message message,
                CorrelationData correlationData) throws AmqpException {
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.sentMessage = message;
            this.correlationData = correlationData;
        }
    }

    private static final class NoOpConnectionFactory implements ConnectionFactory {

        @Override
        public Connection createConnection() throws AmqpException {
            throw new UnsupportedOperationException("No broker connection is required for these tests");
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public int getPort() {
            return 5672;
        }

        @Override
        public String getVirtualHost() {
            return "/";
        }

        @Override
        public String getUsername() {
            return "guest";
        }

        @Override
        public void addConnectionListener(ConnectionListener listener) {
        }

        @Override
        public boolean removeConnectionListener(ConnectionListener listener) {
            return false;
        }

        @Override
        public void clearConnectionListeners() {
        }
    }

    private static final class RecordingMessageListenerContainer implements MessageListenerContainer {

        private MessageListener listener;

        private boolean afterPropertiesSetCalled;

        private final AtomicBoolean running = new AtomicBoolean();

        @Override
        public void setupMessageListener(MessageListener listener) {
            this.listener = listener;
        }

        @Override
        public void setQueueNames(String... queueName) {
        }

        @Override
        public void setAutoStartup(boolean autoStartup) {
        }

        @Override
        public Object getMessageListener() {
            return this.listener;
        }

        @Override
        public void setListenerId(String listenerId) {
        }

        @Override
        public void afterPropertiesSet() {
            this.afterPropertiesSetCalled = true;
        }

        @Override
        public void start() {
            this.running.set(true);
        }

        @Override
        public void stop() {
            this.running.set(false);
        }

        @Override
        public boolean isRunning() {
            return this.running.get();
        }
    }

    private static final class TestAttributeAccessor extends AttributeAccessorSupport {
    }
}
