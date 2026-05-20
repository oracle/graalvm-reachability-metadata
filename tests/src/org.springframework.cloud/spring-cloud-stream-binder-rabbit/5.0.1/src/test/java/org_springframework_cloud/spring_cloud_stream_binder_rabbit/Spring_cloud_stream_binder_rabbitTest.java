/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.function.context.config.MessageConverterHelper;
import org.springframework.cloud.stream.binder.rabbit.BatchCapableRejectAndDontRequeueRecoverer;
import org.springframework.cloud.stream.binder.rabbit.RabbitExpressionEvaluatingInterceptor;
import org.springframework.cloud.stream.binder.rabbit.RabbitMessageChannelBinder;
import org.springframework.cloud.stream.binder.rabbit.aot.RabbitBinderRuntimeHints;
import org.springframework.cloud.stream.binder.rabbit.config.DefaultMessageConverterHelper;
import org.springframework.cloud.stream.binder.rabbit.config.ExtendedBindingHandlerMappingsProviderConfiguration;
import org.springframework.cloud.stream.binder.rabbit.config.MessageConverterHelperConfiguration;
import org.springframework.cloud.stream.binder.rabbit.config.RabbitBinderConfiguration;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitCommonProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitConsumerProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitExtendedBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties.ProducerType;
import org.springframework.cloud.stream.binder.rabbit.provisioning.RabbitExchangeQueueProvisioner;
import org.springframework.cloud.stream.config.BindingHandlerAdvise.MappingsProvider;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class Spring_cloud_stream_binder_rabbitTest {

    @Test
    void batchCapableRecovererRejectsSingleAndBatchMessagesWithoutRequeue() {
        org.springframework.amqp.core.Message first = amqpMessage("first");
        org.springframework.amqp.core.Message second = amqpMessage("second");
        IllegalStateException cause = new IllegalStateException("poison payload");
        BatchCapableRejectAndDontRequeueRecoverer recoverer =
                new BatchCapableRejectAndDontRequeueRecoverer(() -> "retry attempts exhausted");

        assertThatExceptionOfType(ListenerExecutionFailedException.class)
                .isThrownBy(() -> recoverer.recover(first, cause))
                .satisfies(ex -> {
                    assertThat(ex).hasMessage("retry attempts exhausted");
                    assertThat(ex.getFailedMessage()).isSameAs(first);
                    assertThat(ex.getFailedMessages()).containsExactly(first);
                    assertThat(ex.getCause()).isInstanceOf(AmqpRejectAndDontRequeueException.class);
                    assertThat(ex.getCause().getCause()).isSameAs(cause);
                });

        assertThatExceptionOfType(ListenerExecutionFailedException.class)
                .isThrownBy(() -> recoverer.recover(List.of(first, second), cause))
                .satisfies(ex -> {
                    assertThat(ex).hasMessage("retry attempts exhausted");
                    assertThat(ex.getFailedMessages()).containsExactly(first, second);
                    assertThat(ex.getCause()).isInstanceOf(AmqpRejectAndDontRequeueException.class);
                    assertThat(ex.getCause().getCause()).isSameAs(cause);
                });

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new BatchCapableRejectAndDontRequeueRecoverer(null))
                .withMessageContaining("messageSupplier");
    }

    @Test
    void expressionInterceptorAddsRabbitRoutingAndDelayHeaders() {
        RabbitExpressionEvaluatingInterceptor interceptor = new RabbitExpressionEvaluatingInterceptor(
                RabbitExpressionEvaluatingInterceptor.PARSER.parseExpression(
                        "payload['tenant'] + '.' + headers['eventType']"),
                RabbitExpressionEvaluatingInterceptor.PARSER.parseExpression("headers['attempt'] * 125"),
                new StandardEvaluationContext());
        Message<Map<String, String>> message = MessageBuilder
                .withPayload(Map.of("tenant", "tenant-a"))
                .setHeader("eventType", "orders.created")
                .setHeader("attempt", 2)
                .build();

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result.getPayload()).isSameAs(message.getPayload());
        assertThat(result.getHeaders().get(RabbitExpressionEvaluatingInterceptor.ROUTING_KEY_HEADER))
                .isEqualTo("tenant-a.orders.created");
        assertThat(result.getHeaders().get(RabbitExpressionEvaluatingInterceptor.DELAY_HEADER)).isEqualTo(250);
        assertThat(message.getHeaders()).doesNotContainKey(RabbitExpressionEvaluatingInterceptor.ROUTING_KEY_HEADER);

        RabbitExpressionEvaluatingInterceptor routingOnly = new RabbitExpressionEvaluatingInterceptor(
                RabbitExpressionEvaluatingInterceptor.PARSER.parseExpression("'fixed.key'"), null,
                new StandardEvaluationContext());
        assertThat(routingOnly.preSend(message, null).getHeaders())
                .containsEntry(RabbitExpressionEvaluatingInterceptor.ROUTING_KEY_HEADER, "fixed.key")
                .doesNotContainKey(RabbitExpressionEvaluatingInterceptor.DELAY_HEADER);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RabbitExpressionEvaluatingInterceptor(null, null,
                        new StandardEvaluationContext()))
                .withMessageContaining("At least one expression");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new RabbitExpressionEvaluatingInterceptor(
                        RabbitExpressionEvaluatingInterceptor.PARSER.parseExpression("'key'"), null, null))
                .withMessageContaining("evaluationContext");
    }

    @Test
    void defaultMessageConverterHelperPrunesFailedBatchedHeaderOnlyOnFirstDeliveryAttempt() {
        DefaultMessageConverterHelper helper = new DefaultMessageConverterHelper();
        ArrayList<Map<String, Object>> batchedHeaders = new ArrayList<>();
        batchedHeaders.add(new LinkedHashMap<>(Map.of("sequence", 1)));
        batchedHeaders.add(new LinkedHashMap<>(Map.of("sequence", 2)));
        Message<List<String>> firstDelivery = MessageBuilder
                .withPayload(List.of("first", "second"))
                .setHeader("deliveryAttempt", new AtomicInteger(1))
                .setHeader("amqp_batchedHeaders", batchedHeaders)
                .build();

        assertThat(helper.shouldFailIfCantConvert(firstDelivery)).isFalse();
        helper.postProcessBatchMessageOnFailure(firstDelivery, 0);

        assertThat(batchedHeaders).hasSize(1);
        assertThat(batchedHeaders.get(0)).containsEntry("sequence", 2);

        ArrayList<Map<String, Object>> retryHeaders = new ArrayList<>();
        retryHeaders.add(new LinkedHashMap<>(Map.of("sequence", 1)));
        retryHeaders.add(new LinkedHashMap<>(Map.of("sequence", 2)));
        Message<List<String>> retryDelivery = MessageBuilder
                .withPayload(List.of("first", "second"))
                .setHeader("deliveryAttempt", new AtomicInteger(2))
                .setHeader("amqp_batchedHeaders", retryHeaders)
                .build();

        helper.postProcessBatchMessageOnFailure(retryDelivery, 0);

        assertThat(retryHeaders).hasSize(2);
    }

    @Test
    void configurationObjectsExposeBinderBeansAndDefaultPropertyMappings() {
        RabbitBinderConfiguration binderConfiguration = new RabbitBinderConfiguration();
        MessageConverterHelper converterHelper =
                new MessageConverterHelperConfiguration().rabbitMessageConverterHelper();
        MappingsProvider mappingsProvider = new ExtendedBindingHandlerMappingsProviderConfiguration()
                .rabbitExtendedPropertiesDefaultMappingsProvider();

        assertThat(binderConfiguration.binderName()).isEqualTo("rabbit");
        assertThat(converterHelper).isInstanceOf(DefaultMessageConverterHelper.class);
        assertThat(mappingsProvider.getDefaultMappings()).containsEntry(
                ConfigurationPropertyName.of("spring.cloud.stream.rabbit.bindings"),
                ConfigurationPropertyName.of("spring.cloud.stream.rabbit.default"));
    }

    @Test
    void rabbitBinderRuntimeHintsRegisterExtendedBindingPropertyTypes() {
        RuntimeHints hints = new RuntimeHints();

        new RabbitBinderRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertRegisteredBindingPropertyHint(hints, RabbitCommonProperties.class);
        assertRegisteredBindingPropertyHint(hints, RabbitConsumerProperties.class);
        assertRegisteredBindingPropertyHint(hints, RabbitProducerProperties.class);
        assertRegisteredBindingPropertyHint(hints, RabbitExtendedBindingProperties.class);
        assertRegisteredBindingPropertyHint(hints, RabbitBindingProperties.class);
    }

    @Test
    void messageChannelBinderExposesConfiguredExtendedPropertiesWithoutBrokerConnection() throws Exception {
        FailingConnectionFactory connectionFactory = new FailingConnectionFactory();
        RabbitExchangeQueueProvisioner provisioner = new RabbitExchangeQueueProvisioner(connectionFactory);
        RabbitMessageChannelBinder binder = new RabbitMessageChannelBinder(
                connectionFactory, new RabbitProperties(), provisioner);
        RabbitConsumerProperties consumer = new RabbitConsumerProperties();
        consumer.setQueueNameGroupOnly(true);
        RabbitProducerProperties producer = new RabbitProducerProperties();
        producer.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
        producer.setProducerType(ProducerType.STREAM_SYNC);
        RabbitBindingProperties binding = new RabbitBindingProperties();
        binding.setConsumer(consumer);
        binding.setProducer(producer);
        RabbitExtendedBindingProperties extendedProperties = new RabbitExtendedBindingProperties();
        extendedProperties.setBindings(Map.of("orders-in", binding));

        binder.setAdminAddresses(new String[] {"http://127.0.0.1:15672" });
        binder.setNodes(new String[] {"rabbit@localhost" });
        binder.setExtendedBindingProperties(extendedProperties);

        assertThat(binder.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(binder.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.rabbit.default");
        assertThat(binder.getExtendedPropertiesEntryClass()).isEqualTo(RabbitBindingProperties.class);
        assertThat(binder.getExtendedConsumerProperties("orders-in")).isSameAs(consumer);
        assertThat(binder.getExtendedProducerProperties("orders-in")).isSameAs(producer);
        assertThat(binder.getBinderIdentity()).startsWith("rabbit-");

        binder.destroy();
    }

    private static org.springframework.amqp.core.Message amqpMessage(String body) {
        return new org.springframework.amqp.core.Message(body.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRegisteredBindingPropertyHint(RuntimeHints hints, Class<?> type) {
        TypeHint typeHint = hints.reflection().getTypeHint(type);
        assertThat(typeHint).as("runtime hint for %s", type.getName()).isNotNull();
        assertThat(typeHint.getMemberCategories())
                .contains(
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INTROSPECT_PUBLIC_METHODS);
    }

    private static final class FailingConnectionFactory implements ConnectionFactory {

        @Override
        public Connection createConnection() {
            throw new AmqpConnectException(new ConnectException("No broker is required for binder metadata tests"));
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
}
