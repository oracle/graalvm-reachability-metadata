/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_rabbit_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.DeclarableCustomizer;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.rabbit.admin.RabbitAdminException;
import org.springframework.cloud.stream.binder.rabbit.admin.RabbitBindingCleaner;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitCommonProperties.QuorumConfig;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitConsumerProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitConsumerProperties.ContainerType;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitExtendedBindingProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties.AlternateExchange;
import org.springframework.cloud.stream.binder.rabbit.properties.RabbitProducerProperties.ProducerType;
import org.springframework.cloud.stream.binder.rabbit.provisioning.RabbitExchangeQueueProvisioner;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class Spring_cloud_stream_binder_rabbit_coreTest {

    @Test
    void propertyObjectsExposeDefaultsAndMutableNestedConfiguration() {
        RabbitBindingProperties binding = new RabbitBindingProperties();
        RabbitConsumerProperties consumer = binding.getConsumer();
        RabbitProducerProperties producer = binding.getProducer();

        assertThat(consumer.getExchangeType()).isEqualTo("topic");
        assertThat(consumer.isDeclareExchange()).isTrue();
        assertThat(consumer.isExchangeDurable()).isTrue();
        assertThat(consumer.isBindQueue()).isTrue();
        assertThat(consumer.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.AUTO);
        assertThat(consumer.getMaxConcurrency()).isOne();
        assertThat(consumer.getPrefetch()).isOne();
        assertThat(consumer.getBatchSize()).isOne();
        assertThat(consumer.isDurableSubscription()).isTrue();
        assertThat(consumer.isRepublishToDlq()).isTrue();
        assertThat(consumer.getRepublishDeliveyMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(consumer.getHeaderPatterns()).containsExactly("*");
        assertThat(consumer.getRecoveryInterval()).isEqualTo(5_000L);
        assertThat(consumer.getFrameMaxHeadroom()).isEqualTo(20_000);
        assertThat(consumer.getContainerType()).isEqualTo(ContainerType.SIMPLE);
        assertThat(consumer.getAnonymousGroupPrefix()).isEqualTo("anonymous.");
        assertThat(consumer.getConsumerPriority()).isEqualTo(-1);
        assertThat(consumer.getQueueMaxPriority()).isEqualTo(-1);

        assertThat(producer.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(producer.getHeaderPatterns()).containsExactly("*");
        assertThat(producer.getBatchSize()).isEqualTo(100);
        assertThat(producer.getBatchBufferLimit()).isEqualTo(10_000);
        assertThat(producer.getBatchTimeout()).isEqualTo(5_000);
        assertThat(producer.getProducerType()).isEqualTo(ProducerType.AMQP);
        assertThat(producer.getAlternateExchange()).isNull();

        QuorumConfig quorum = new QuorumConfig();
        quorum.setEnabled(true);
        quorum.setInitialGroupSize(3);
        quorum.setDeliveryLimit(7);
        consumer.setQuorum(quorum);
        consumer.setDlqQuorum(quorum);
        consumer.setAutoBindDlq(true);
        consumer.setDeadLetterQueueName("custom.dlq");
        consumer.setDeadLetterExchange("custom.dlx");
        consumer.setDeadLetterRoutingKey("dead.route");
        consumer.setDlqDeadLetterExchange("retry.dlx");
        consumer.setDlqDeadLetterRoutingKey("retry.route");
        consumer.setQueueBindingArguments(Map.of("x-match", "all"));
        consumer.setDlqBindingArguments(Map.of("reason", "expired"));
        consumer.setSingleActiveConsumer(true);
        consumer.setDlqSingleActiveConsumer(true);
        consumer.setStreamStreamMessageConverterBeanName("streamConverter");
        consumer.setContainerType(ContainerType.DIRECT);
        consumer.setReceiveTimeout(25L);
        consumer.setSuperStream(true);
        consumer.setConsumerPriority(9);
        consumer.setQueueMaxPriority(12);

        assertThat(consumer.getQuorum()).isSameAs(quorum);
        assertThat(consumer.getDlqQuorum().getDeliveryLimit()).isEqualTo(7);
        assertThat(consumer.isAutoBindDlq()).isTrue();
        assertThat(consumer.getDeadLetterQueueName()).isEqualTo("custom.dlq");
        assertThat(consumer.getDeadLetterExchange()).isEqualTo("custom.dlx");
        assertThat(consumer.getDeadLetterRoutingKey()).isEqualTo("dead.route");
        assertThat(consumer.getDlqDeadLetterExchange()).isEqualTo("retry.dlx");
        assertThat(consumer.getDlqDeadLetterRoutingKey()).isEqualTo("retry.route");
        assertThat(consumer.getQueueBindingArguments()).containsEntry("x-match", "all");
        assertThat(consumer.getDlqBindingArguments()).containsEntry("reason", "expired");
        assertThat(consumer.isSingleActiveConsumer()).isTrue();
        assertThat(consumer.isDlqSingleActiveConsumer()).isTrue();
        assertThat(consumer.getStreamStreamMessageConverterBeanName()).isEqualTo("streamConverter");
        assertThat(consumer.getContainerType()).isEqualTo(ContainerType.DIRECT);
        assertThat(consumer.getReceiveTimeout()).isEqualTo(25L);
        assertThat(consumer.isSuperStream()).isTrue();
        assertThat(consumer.getConsumerPriority()).isEqualTo(9);
        assertThat(consumer.getQueueMaxPriority()).isEqualTo(12);

        producer.setCompress(true);
        producer.setBatchingEnabled(true);
        producer.setBatchSize(5);
        producer.setBatchBufferLimit(512);
        producer.setBatchTimeout(250);
        producer.setTransacted(true);
        producer.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
        producer.setHeaderPatterns(new String[] { "foo*", "bar" });
        producer.setRoutingKey("orders.created");
        producer.setDelayExpression(new SpelExpressionParser().parseExpression("100 + 23"));
        producer.setConfirmAckChannel("acks");
        producer.setBatchingStrategyBeanName("batcher");
        producer.setUseConfirmHeader(true);
        producer.setProducerType(ProducerType.STREAM_ASYNC);
        producer.setStreamMessageConverterBeanName("producerConverter");
        producer.setSuperStream(true);

        assertThat(producer.isCompress()).isTrue();
        assertThat(producer.isBatchingEnabled()).isTrue();
        assertThat(producer.getBatchSize()).isEqualTo(5);
        assertThat(producer.getBatchBufferLimit()).isEqualTo(512);
        assertThat(producer.getBatchTimeout()).isEqualTo(250);
        assertThat(producer.isTransacted()).isTrue();
        assertThat(producer.getDeliveryMode()).isEqualTo(MessageDeliveryMode.NON_PERSISTENT);
        assertThat(producer.getHeaderPatterns()).containsExactly("foo*", "bar");
        assertThat(producer.getDelayExpression().getValue()).isEqualTo(123);
        assertThat(producer.getConfirmAckChannel()).isEqualTo("acks");
        assertThat(producer.getBatchingStrategyBeanName()).isEqualTo("batcher");
        assertThat(producer.isUseConfirmHeader()).isTrue();
        assertThat(producer.getProducerType()).isEqualTo(ProducerType.STREAM_ASYNC);
        assertThat(producer.getStreamMessageConverterBeanName()).isEqualTo("producerConverter");
        assertThat(producer.isSuperStream()).isTrue();
        assertThat(producer.getRoutingKeyExpression().getValue()).isEqualTo("orders.created");

        Expression routingExpression = new SpelExpressionParser().parseExpression("'tenant.' + 'orders'");
        producer.setRoutingKeyExpression(routingExpression);
        assertThat(producer.getRoutingKeyExpression().getValue()).isEqualTo("tenant.orders");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> consumer.setAcknowledgeMode(null))
                .withMessageContaining("Acknowledge mode cannot be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> producer.setProducerType(null))
                .withMessageContaining("producerType");
    }

    @Test
    void binderMapsExternalConfigurationToRabbitProperties() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.exchange-type", "direct");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.binding-routing-key", "created,updated");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.binding-routing-key-delimiter", ",");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.auto-bind-dlq", "true");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.ttl", "1000");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.dlq-ttl", "2000");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.quorum.enabled", "true");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.quorum.initial-group-size", "5");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.quorum.delivery-limit", "10");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.container-type", "STREAM");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.enable-batching", "true");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.receive-timeout", "50");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.header-patterns[0]", "trace*");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.consumer.header-patterns[1]", "type");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.producer-type", "STREAM_SYNC");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.routing-key", "orders.created");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.compress", "true");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.alternate-exchange.name", "orders.ae");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.alternate-exchange.type", "fanout");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.alternate-exchange.binding.queue",
                "orders.unrouted");
        source.put("spring.cloud.stream.rabbit.bindings.orders-in.producer.alternate-exchange.binding.routing-key",
                "#");
        source.put("spring.cloud.stream.rabbit.binder.admin-addresses[0]", "http://localhost:15672");
        source.put("spring.cloud.stream.rabbit.binder.admin-addresses[1]", "https://localhost:15671");
        source.put("spring.cloud.stream.rabbit.binder.nodes[0]", "rabbit@one");
        source.put("spring.cloud.stream.rabbit.binder.nodes[1]", "rabbit@two");
        source.put("spring.cloud.stream.rabbit.binder.compression-level", "4");
        source.put("spring.cloud.stream.rabbit.binder.connection-name-prefix", "native-test");

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        RabbitBindingProperties binding = binder.bind(
                "spring.cloud.stream.rabbit.bindings.orders-in",
                Bindable.of(RabbitBindingProperties.class)).get();
        RabbitBinderConfigurationProperties binderProperties = binder.bind(
                "spring.cloud.stream.rabbit.binder",
                Bindable.of(RabbitBinderConfigurationProperties.class)).get();

        RabbitConsumerProperties consumer = binding.getConsumer();
        assertThat(consumer.getExchangeType()).isEqualTo("direct");
        assertThat(consumer.getBindingRoutingKey()).isEqualTo("created,updated");
        assertThat(consumer.getBindingRoutingKeyDelimiter()).isEqualTo(",");
        assertThat(consumer.isAutoBindDlq()).isTrue();
        assertThat(consumer.getTtl()).isEqualTo(1_000);
        assertThat(consumer.getDlqTtl()).isEqualTo(2_000);
        assertThat(consumer.getQuorum().isEnabled()).isTrue();
        assertThat(consumer.getQuorum().getInitialGroupSize()).isEqualTo(5);
        assertThat(consumer.getQuorum().getDeliveryLimit()).isEqualTo(10);
        assertThat(consumer.getContainerType()).isEqualTo(ContainerType.STREAM);
        assertThat(consumer.isEnableBatching()).isTrue();
        assertThat(consumer.getReceiveTimeout()).isEqualTo(50L);
        assertThat(consumer.getHeaderPatterns()).containsExactly("trace*", "type");

        RabbitProducerProperties producer = binding.getProducer();
        assertThat(producer.getProducerType()).isEqualTo(ProducerType.STREAM_SYNC);
        assertThat(producer.getRoutingKey()).isEqualTo("orders.created");
        assertThat(producer.isCompress()).isTrue();
        assertThat(producer.getAlternateExchange().getName()).isEqualTo("orders.ae");
        assertThat(producer.getAlternateExchange().getType()).isEqualTo("fanout");
        assertThat(producer.getAlternateExchange().getBinding().getQueue()).isEqualTo("orders.unrouted");
        assertThat(producer.getAlternateExchange().getBinding().getRoutingKey()).isEqualTo("#");

        assertThat(binderProperties.getAdminAddresses())
                .containsExactly("http://localhost:15672", "https://localhost:15671");
        assertThat(binderProperties.getNodes()).containsExactly("rabbit@one", "rabbit@two");
        assertThat(binderProperties.getCompressionLevel()).isEqualTo(4);
        assertThat(binderProperties.getConnectionNamePrefix()).isEqualTo("native-test");
    }

    @Test
    void extendedBindingPropertiesReturnsConfiguredBindingEntries() {
        RabbitConsumerProperties consumer = new RabbitConsumerProperties();
        consumer.setExchangeType("headers");
        consumer.setAutoBindDlq(true);
        RabbitProducerProperties producer = new RabbitProducerProperties();
        producer.setRoutingKey("outbound.key");
        producer.setProducerType(ProducerType.STREAM_SYNC);
        RabbitBindingProperties binding = new RabbitBindingProperties();
        binding.setConsumer(consumer);
        binding.setProducer(producer);

        RabbitExtendedBindingProperties extended = new RabbitExtendedBindingProperties();
        extended.setBindings(Map.of("orders", binding));

        assertThat(extended.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.rabbit.default");
        assertThat(extended.getExtendedPropertiesEntryClass()).isEqualTo(RabbitBindingProperties.class);
        assertThat(extended.getBindings()).containsEntry("orders", binding);
        assertThat(extended.getExtendedConsumerProperties("orders")).isSameAs(consumer);
        assertThat(extended.getExtendedProducerProperties("orders")).isSameAs(producer);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> extended.getBindings().put("other", new RabbitBindingProperties()));
    }

    @Test
    void provisionerBuildsProducerDeclarablesAndDestinationsWithoutBrokerConnection() {
        CapturingDeclarableCustomizer customizer = new CapturingDeclarableCustomizer();
        RabbitExchangeQueueProvisioner provisioner = new RabbitExchangeQueueProvisioner(
                new FailingConnectionFactory(), List.of(customizer));
        RabbitProducerProperties rabbit = new RabbitProducerProperties();
        rabbit.setPrefix("prod.");
        rabbit.setExchangeType("topic");
        rabbit.setBindingRoutingKey("created,updated");
        rabbit.setBindingRoutingKeyDelimiter(",");
        rabbit.setAutoBindDlq(true);
        rabbit.setDeadLetterRoutingKey("dead.orders");
        rabbit.setTtl(1_500);
        rabbit.setMaxLength(25);
        rabbit.setLazy(true);
        rabbit.setSingleActiveConsumer(true);
        QuorumConfig quorum = new QuorumConfig();
        quorum.setEnabled(true);
        quorum.setDeliveryLimit(4);
        quorum.setInitialGroupSize(3);
        rabbit.setQuorum(quorum);
        rabbit.setQueueBindingArguments(Map.of("binding-arg", "value"));
        rabbit.setDlqBindingArguments(Map.of("dlq-arg", "dead"));
        rabbit.setAlternateExchange(alternateExchange("orders.unrouted", "orders.unrouted.queue"));

        ExtendedProducerProperties<RabbitProducerProperties> producer = new ExtendedProducerProperties<>(rabbit);
        producer.setRequiredGroups("accounting");
        producer.setPartitionCount(2);
        producer.setPartitionKeyExpression(new SpelExpressionParser().parseExpression("payload.customerId"));

        ProducerDestination destination = provisioner.provisionProducerDestination("orders", producer);

        assertThat(destination.getName()).isEqualTo("prod.orders");
        assertThat(destination.getNameForPartition(1)).isEqualTo("prod.orders");
        assertThat(destination.toString()).contains("RabbitProducerDestination", "prod.orders");
        assertThat(RabbitExchangeQueueProvisioner.constructDLQName("orders.accounting"))
                .isEqualTo("orders.accounting.dlq");
        assertThat(RabbitExchangeQueueProvisioner.applyPrefix("prod.", "orders"))
                .isEqualTo("prod.orders");

        assertThat(customizer.exchanges()).anySatisfy(exchange -> {
            assertThat(exchange.getName()).isEqualTo("prod.orders");
            assertThat(exchange.getType()).isEqualTo("topic");
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.getArguments()).containsEntry("alternate-exchange", "orders.unrouted");
        });
        assertThat(customizer.exchanges()).anySatisfy(exchange -> {
            assertThat(exchange.getName()).isEqualTo("orders.unrouted");
            assertThat(exchange.getType()).isEqualTo("topic");
        });
        assertThat(customizer.queues()).anySatisfy(queue -> {
            assertThat(queue.getName()).isEqualTo("prod.orders.accounting-0");
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", "prod.DLX")
                    .containsEntry("x-dead-letter-routing-key", "dead.orders")
                    .containsEntry("x-message-ttl", 1_500)
                    .containsEntry("x-max-length", 25)
                    .containsEntry("x-queue-mode", "lazy")
                    .containsEntry("x-queue-type", "quorum")
                    .containsEntry("x-delivery-limit", 4)
                    .containsEntry("x-quorum-initial-group-size", 3)
                    .containsEntry("x-single-active-consumer", true);
        });
        assertThat(customizer.queues()).anySatisfy(queue ->
                assertThat(queue.getName()).isEqualTo("orders.unrouted.queue"));
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("prod.orders.accounting-0");
            assertThat(binding.getExchange()).isEqualTo("prod.orders");
            assertThat(binding.getRoutingKey()).isEqualTo("created-0");
        });
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("prod.orders.accounting.dlq");
            assertThat(binding.getExchange()).isEqualTo("prod.DLX");
            assertThat(binding.getRoutingKey()).isEqualTo("dead.orders");
        });

        provisioner.cleanAutoDeclareContext(destination, producer);
    }

    @Test
    void provisionerBuildsConsumerDeclarablesAndRejectsFanoutPartitioning() {
        CapturingDeclarableCustomizer customizer = new CapturingDeclarableCustomizer();
        RabbitExchangeQueueProvisioner provisioner = new RabbitExchangeQueueProvisioner(
                new FailingConnectionFactory(), List.of(customizer));
        RabbitConsumerProperties rabbit = new RabbitConsumerProperties();
        rabbit.setPrefix("in.");
        rabbit.setExchangeType("direct");
        rabbit.setBindingRoutingKey("orders.created");
        rabbit.setQueueNameGroupOnly(true);
        rabbit.setDurableSubscription(false);
        rabbit.setAutoBindDlq(true);
        rabbit.setContainerType(ContainerType.STREAM);
        rabbit.setQueueMaxPriority(8);
        rabbit.setOverflowBehavior("reject-publish");

        ExtendedConsumerProperties<RabbitConsumerProperties> consumer = new ExtendedConsumerProperties<>(rabbit);
        consumer.setPartitioned(true);
        consumer.setInstanceIndex(2);

        ConsumerDestination destination = provisioner.provisionConsumerDestination("orders", "workers", consumer);

        assertThat(destination.getName()).isEqualTo("in.workers-2");
        assertThat(destination.toString()).contains("RabbitConsumerDestination", "workers", "orders");
        assertThat(customizer.queues()).anySatisfy(queue -> {
            assertThat(queue.getName()).isEqualTo("in.workers-2");
            assertThat(queue.isDurable()).isFalse();
            assertThat(queue.isAutoDelete()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", "in.DLX")
                    .containsEntry("x-dead-letter-routing-key", "in.workers-2")
                    .containsEntry("x-queue-type", "stream")
                    .containsEntry("x-max-priority", 8)
                    .containsEntry("x-overflow", "reject-publish");
        });
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("in.workers-2");
            assertThat(binding.getExchange()).isEqualTo("in.orders");
            assertThat(binding.getRoutingKey()).isEqualTo("orders.created-2");
        });

        provisioner.cleanAutoDeclareContext(destination, consumer);

        RabbitConsumerProperties invalidRabbit = new RabbitConsumerProperties();
        invalidRabbit.setExchangeType("fanout");
        ExtendedConsumerProperties<RabbitConsumerProperties> invalidConsumer =
                new ExtendedConsumerProperties<>(invalidRabbit);
        invalidConsumer.setPartitioned(true);
        invalidConsumer.setInstanceIndex(0);

        assertThatExceptionOfType(ProvisioningException.class)
                .isThrownBy(() -> provisioner.provisionConsumerDestination("fanout.orders", "workers", invalidConsumer))
                .withMessageContaining("fanout exchange is not appropriate");
    }

    @Test
    void provisionerBuildsMultiplexedPartitionedConsumerDestinations() {
        CapturingDeclarableCustomizer customizer = new CapturingDeclarableCustomizer();
        RabbitExchangeQueueProvisioner provisioner = new RabbitExchangeQueueProvisioner(
                new FailingConnectionFactory(), List.of(customizer));
        RabbitConsumerProperties rabbit = new RabbitConsumerProperties();
        rabbit.setPrefix("multi.");

        ExtendedConsumerProperties<RabbitConsumerProperties> consumer = new ExtendedConsumerProperties<>(rabbit);
        consumer.setMultiplex(true);
        consumer.setPartitioned(true);
        consumer.setInstanceIndexList(List.of(0, 2));

        ConsumerDestination destination = provisioner.provisionConsumerDestination("orders,returns", "workers", consumer);

        assertThat(destination.getName()).isEqualTo(
                "multi.orders.workers-0,multi.orders.workers-2,"
                        + "multi.returns.workers-0,multi.returns.workers-2");
        assertThat(customizer.exchanges()).extracting(Exchange::getName)
                .containsExactly("multi.orders", "multi.orders", "multi.returns", "multi.returns");
        assertThat(customizer.queues()).extracting(Queue::getName)
                .containsExactly(
                        "multi.orders.workers-0",
                        "multi.orders.workers-2",
                        "multi.returns.workers-0",
                        "multi.returns.workers-2");
        assertThat(customizer.bindings()).hasSize(4);
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("multi.orders.workers-0");
            assertThat(binding.getExchange()).isEqualTo("multi.orders");
            assertThat(binding.getRoutingKey()).isEqualTo("orders-0");
        });
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("multi.orders.workers-2");
            assertThat(binding.getExchange()).isEqualTo("multi.orders");
            assertThat(binding.getRoutingKey()).isEqualTo("orders-2");
        });
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("multi.returns.workers-0");
            assertThat(binding.getExchange()).isEqualTo("multi.returns");
            assertThat(binding.getRoutingKey()).isEqualTo("returns-0");
        });
        assertThat(customizer.bindings()).anySatisfy(binding -> {
            assertThat(binding.getDestination()).isEqualTo("multi.returns.workers-2");
            assertThat(binding.getExchange()).isEqualTo("multi.returns");
            assertThat(binding.getRoutingKey()).isEqualTo("returns-2");
        });

        provisioner.cleanAutoDeclareContext(destination, consumer);
    }

    @Test
    void bindingCleanerDeletesOnlyQueuesAndExchangesForRequestedDestination() throws Exception {
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString("cleaner:secret".getBytes(StandardCharsets.UTF_8));
        List<StubResponse> responses = List.of(
                new StubResponse("GET", "/api/queues/%2F/", 200, """
                        [
                          {"name":"binder.orders.group-one","consumers":0},
                          {"name":"binder.orders.group-two","consumers":0},
                          {"name":"binder.other.group","consumers":0}
                        ]
                        """),
                new StubResponse("GET", "/api/exchanges/%2F/", 200, """
                        [
                          {"name":"binder.orders.exchange"},
                          {"name":"binder.other.exchange"}
                        ]
                        """),
                new StubResponse("GET", "/api/exchanges/%2F/binder.orders.exchange/bindings/source", 200, """
                        [
                          {"destination_type":"queue","destination":"binder.orders.group-one"},
                          {"destination_type":"queue","destination":"binder.orders.group-two"}
                        ]
                        """),
                new StubResponse("GET", "/api/exchanges/%2F/binder.orders.exchange/bindings/destination", 200, "[]"),
                new StubResponse("DELETE", "/api/queues/%2F/binder.orders.group-two", 204, ""),
                new StubResponse("DELETE", "/api/queues/%2F/binder.orders.group-one", 204, ""),
                new StubResponse("DELETE", "/api/exchanges/%2F/binder.orders.exchange", 204, ""));

        try (ManagementApiServer server = ManagementApiServer.start(responses, authorization)) {
            RabbitBindingCleaner cleaner = new RabbitBindingCleaner();

            Map<String, List<String>> deleted = cleaner.clean(server.baseUrl(), "cleaner", "secret", "/",
                    RabbitBindingCleaner.BINDER_PREFIX, "orders", false);

            assertThat(deleted.get("queues"))
                    .containsExactly("binder.orders.group-one", "binder.orders.group-two");
            assertThat(deleted.get("exchanges")).containsExactly("binder.orders.exchange");
            assertThat(server.requests()).extracting(RecordedRequest::method)
                    .containsExactly("GET", "GET", "GET", "GET", "DELETE", "DELETE", "DELETE");
            assertThat(server.requests()).extracting(RecordedRequest::path)
                    .containsExactly(
                            "/api/queues/%2F/",
                            "/api/exchanges/%2F/",
                            "/api/exchanges/%2F/binder.orders.exchange/bindings/source",
                            "/api/exchanges/%2F/binder.orders.exchange/bindings/destination",
                            "/api/queues/%2F/binder.orders.group-two",
                            "/api/queues/%2F/binder.orders.group-one",
                            "/api/exchanges/%2F/binder.orders.exchange");
            assertThat(server.requests()).allSatisfy(request ->
                    assertThat(request.authorization()).isEqualTo(authorization));
        }
    }

    @Test
    void alternateExchangeAndExceptionTypesRemainUsableThroughPublicApi() {
        AlternateExchange alternate = alternateExchange("fallback", "fallback.queue");
        alternate.setExists(true);
        alternate.setType("direct");
        alternate.getBinding().setRoutingKey("fallback.key");

        RabbitProducerProperties producer = new RabbitProducerProperties();
        producer.setAlternateExchange(alternate);

        assertThat(producer.getAlternateExchange().isExists()).isTrue();
        assertThat(producer.getAlternateExchange().getType()).isEqualTo("direct");
        assertThat(producer.getAlternateExchange().getBinding().getQueue()).isEqualTo("fallback.queue");
        assertThat(producer.getAlternateExchange().getBinding().getRoutingKey()).isEqualTo("fallback.key");

        RabbitAdminException messageOnly = new RabbitAdminException("management call failed");
        IllegalStateException cause = new IllegalStateException("denied");
        RabbitAdminException withCause = new RabbitAdminException("management call failed", cause);

        assertThat(messageOnly).hasMessage("management call failed");
        assertThat(withCause).hasMessage("management call failed").hasCause(cause);
    }

    private static AlternateExchange alternateExchange(String name, String queue) {
        AlternateExchange alternate = new AlternateExchange();
        alternate.setName(name);
        AlternateExchange.Binding binding = new AlternateExchange.Binding();
        binding.setQueue(queue);
        alternate.setBinding(binding);
        return alternate;
    }

    private static final class CapturingDeclarableCustomizer implements DeclarableCustomizer {

        private final List<Declarable> declarables = new ArrayList<>();

        @Override
        public Declarable apply(Declarable declarable) {
            this.declarables.add(declarable);
            return declarable;
        }

        private List<Exchange> exchanges() {
            List<Exchange> exchanges = new ArrayList<>();
            for (Declarable declarable : this.declarables) {
                if (declarable instanceof Exchange exchange) {
                    exchanges.add(exchange);
                }
            }
            return exchanges;
        }

        private List<Queue> queues() {
            List<Queue> queues = new ArrayList<>();
            for (Declarable declarable : this.declarables) {
                if (declarable instanceof Queue queue) {
                    queues.add(queue);
                }
            }
            return queues;
        }

        private List<Binding> bindings() {
            List<Binding> bindings = new ArrayList<>();
            for (Declarable declarable : this.declarables) {
                if (declarable instanceof Binding binding) {
                    bindings.add(binding);
                }
            }
            return bindings;
        }
    }

    private record StubResponse(String method, String path, int status, String body) {
    }

    private record RecordedRequest(String method, String path, String authorization) {
    }

    private static final class ManagementApiServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final ConcurrentLinkedQueue<StubResponse> responses;

        private final String expectedAuthorization;

        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        private final Future<?> serverTask;

        private ManagementApiServer(List<StubResponse> responses, String expectedAuthorization) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.serverSocket.setSoTimeout(5_000);
            this.responses = new ConcurrentLinkedQueue<>(responses);
            this.expectedAuthorization = expectedAuthorization;
            this.serverTask = this.executor.submit(this::serveRequests);
        }

        private static ManagementApiServer start(List<StubResponse> responses, String expectedAuthorization)
                throws IOException {
            return new ManagementApiServer(responses, expectedAuthorization);
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + this.serverSocket.getLocalPort();
        }

        private List<RecordedRequest> requests() {
            return List.copyOf(this.requests);
        }

        private void serveRequests() {
            while (!this.responses.isEmpty()) {
                StubResponse response = this.responses.remove();
                try (Socket socket = this.serverSocket.accept()) {
                    RecordedRequest request = readRequest(socket);
                    this.requests.add(request);
                    if (!response.method().equals(request.method()) || !response.path().equals(request.path())
                            || !this.expectedAuthorization.equals(request.authorization())) {
                        writeResponse(socket, 500, "Unexpected request: " + request);
                        continue;
                    }
                    writeResponse(socket, response.status(), response.body());
                }
                catch (SocketTimeoutException ex) {
                    throw new IllegalStateException("Timed out waiting for management API request", ex);
                }
                catch (IOException ex) {
                    if (this.serverSocket.isClosed()) {
                        break;
                    }
                    throw new IllegalStateException("Failed to serve management API request", ex);
                }
            }
        }

        private static RecordedRequest readRequest(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                throw new IOException("Missing HTTP request line");
            }
            String[] parts = requestLine.split(" ");
            String authorization = null;
            String header = reader.readLine();
            while (header != null && !header.isEmpty()) {
                int separator = header.indexOf(':');
                if (separator > 0 && "authorization".equalsIgnoreCase(header.substring(0, separator))) {
                    authorization = header.substring(separator + 1).trim();
                }
                header = reader.readLine();
            }
            return new RecordedRequest(parts[0], parts[1], authorization);
        }

        private static void writeResponse(Socket socket, int status, String body) throws IOException {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),
                    StandardCharsets.UTF_8));
            writer.write("HTTP/1.1 " + status + " " + reasonPhrase(status) + "\r\n");
            writer.write("Content-Type: application/json\r\n");
            writer.write("Content-Length: " + bodyBytes.length + "\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();
            socket.getOutputStream().write(bodyBytes);
            socket.getOutputStream().flush();
        }

        private static String reasonPhrase(int status) {
            return status == 204 ? "No Content" : "OK";
        }

        @Override
        public void close() throws Exception {
            this.serverSocket.close();
            this.executor.shutdown();
            if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.executor.shutdownNow();
            }
            this.serverTask.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class FailingConnectionFactory implements ConnectionFactory {

        @Override
        public Connection createConnection() {
            throw new AmqpConnectException(new ConnectException("No broker is required for provisioning tests"));
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
