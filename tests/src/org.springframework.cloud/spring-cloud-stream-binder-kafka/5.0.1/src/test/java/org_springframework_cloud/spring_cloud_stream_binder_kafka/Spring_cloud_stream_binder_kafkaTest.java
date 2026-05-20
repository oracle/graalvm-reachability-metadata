/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.KafkaExpressionEvaluatingInterceptor;
import org.springframework.cloud.stream.binder.kafka.KafkaNullConverter;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper;
import org.springframework.cloud.stream.binder.kafka.common.TopicInformation;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StandardHeaders;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StartOffset;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties.CompressionType;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaTopicProperties;
import org.springframework.cloud.stream.binder.kafka.provisioning.KafkaTopicProvisioner;
import org.springframework.cloud.stream.binder.kafka.utils.BindingUtils;
import org.springframework.cloud.stream.binder.kafka.utils.DlqDestinationResolver;
import org.springframework.cloud.stream.binder.kafka.utils.DlqPartitionFunction;
import org.springframework.cloud.stream.binder.kafka.utils.KafkaTopicUtils;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer.ControlFlag;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

public class Spring_cloud_stream_binder_kafkaTest {

    @Test
    void propertyObjectsExposeDefaultsAndMutableNestedConfiguration() {
        KafkaBindingProperties binding = new KafkaBindingProperties();
        KafkaConsumerProperties consumer = binding.getConsumer();
        KafkaProducerProperties producer = binding.getProducer();

        assertThat(consumer.isAutoRebalanceEnabled()).isTrue();
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.none);
        assertThat(consumer.getIdleEventInterval()).isEqualTo(30_000L);
        assertThat(consumer.getPollTimeout()).isEqualTo(5_000L);
        assertThat(consumer.isTxCommitRecovered()).isTrue();
        assertThat(consumer.getConfiguration()).isEmpty();
        assertThat(consumer.getTopic()).isNotNull();
        assertThat(consumer.getDlqProducerProperties()).isNotNull();

        assertThat(producer.getBufferSize()).isEqualTo(16_384);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.none);
        assertThat(producer.getConfiguration()).isEmpty();
        assertThat(producer.getTopic()).isNotNull();
        assertThat(producer.getTheMessageKeyExpression()).isNull();

        KafkaTopicProperties topic = new KafkaTopicProperties();
        topic.setReplicationFactor((short) 3);
        topic.setReplicasAssignments(Map.of(0, List.of(1, 2), 1, List.of(2, 3)));
        topic.setProperties(Map.of("retention.ms", "60000", "cleanup.policy", "compact"));

        KafkaProducerProperties dlqProducer = new KafkaProducerProperties();
        dlqProducer.setCompressionType(CompressionType.gzip);
        dlqProducer.setTopic(topic);

        consumer.setAckMode(AckMode.MANUAL);
        consumer.setStartOffset(StartOffset.earliest);
        consumer.setResetOffsets(true);
        consumer.setEnableDlq(true);
        consumer.setAutoCommitOnError(Boolean.FALSE);
        consumer.setAutoRebalanceEnabled(false);
        consumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25"));
        consumer.setDlqName("orders.errors");
        consumer.setDlqPartitions(2);
        consumer.setTrustedPackages(new String[] { "com.acme.events", "java.util" });
        consumer.setDlqProducerProperties(dlqProducer);
        consumer.setStandardHeaders(StandardHeaders.both);
        consumer.setConverterBeanName("customConverter");
        consumer.setIdleEventInterval(125L);
        consumer.setDestinationIsPattern(true);
        consumer.setTopic(topic);
        consumer.setPollTimeout(250L);
        consumer.setTransactionManager("kafkaTransactionManager");
        consumer.setTxCommitRecovered(false);
        consumer.setCommonErrorHandlerBeanName("commonErrorHandler");
        consumer.setReactiveAutoCommit(true);
        consumer.setReactiveAtMostOnce(true);

        assertThat(consumer.getAckMode()).isEqualTo(AckMode.MANUAL);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(StartOffset.earliest.getReferencePoint()).isEqualTo(-2L);
        assertThat(StartOffset.latest.getReferencePoint()).isEqualTo(-1L);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getAutoCommitOnError()).isFalse();
        assertThat(consumer.isAutoRebalanceEnabled()).isFalse();
        assertThat(consumer.getConfiguration()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25");
        assertThat(consumer.getDlqName()).isEqualTo("orders.errors");
        assertThat(consumer.getDlqPartitions()).isEqualTo(2);
        assertThat(consumer.getTrustedPackages()).containsExactly("com.acme.events", "java.util");
        assertThat(consumer.getDlqProducerProperties()).isSameAs(dlqProducer);
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.getConverterBeanName()).isEqualTo("customConverter");
        assertThat(consumer.getIdleEventInterval()).isEqualTo(125L);
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
        assertThat(consumer.getPollTimeout()).isEqualTo(250L);
        assertThat(consumer.getTransactionManager()).isEqualTo("kafkaTransactionManager");
        assertThat(consumer.isTxCommitRecovered()).isFalse();
        assertThat(consumer.getCommonErrorHandlerBeanName()).isEqualTo("commonErrorHandler");
        assertThat(consumer.isReactiveAutoCommit()).isTrue();
        assertThat(consumer.isReactiveAtMostOnce()).isTrue();

        Expression keyExpression = new SpelExpressionParser().parseExpression("headers['eventKey']");
        Expression sendTimeoutExpression = new SpelExpressionParser().parseExpression("1000 + 250");
        producer.setBufferSize(32_768);
        producer.setCompressionType(CompressionType.zstd);
        producer.setSync(true);
        producer.setSendTimeoutExpression(sendTimeoutExpression);
        producer.setBatchTimeout(75);
        producer.setMessageKeyExpression(keyExpression);
        producer.setHeaderPatterns(new String[] { "trace*", "eventType" });
        producer.setConfiguration(Map.of(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1048576"));
        producer.setTopic(topic);
        producer.setUseTopicHeader(true);
        producer.setRecordMetadataChannel("metadataChannel");
        producer.setTransactionManager("txManager");
        producer.setCloseTimeout(10);
        producer.setAllowNonTransactional(true);

        assertThat(producer.getBufferSize()).isEqualTo(32_768);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.zstd);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getSendTimeoutExpression().getValue()).isEqualTo(1_250);
        assertThat(producer.getBatchTimeout()).isEqualTo(75);
        assertThat(producer.getMessageKeyExpression()).isSameAs(keyExpression);
        assertThat(producer.getTheMessageKeyExpression()).isEqualTo("headers['eventKey']");
        assertThat(producer.getHeaderPatterns()).containsExactly("trace*", "eventType");
        assertThat(producer.getConfiguration()).containsEntry(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1048576");
        assertThat(producer.getTopic()).isSameAs(topic);
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadataChannel");
        assertThat(producer.getTransactionManager()).isEqualTo("txManager");
        assertThat(producer.getCloseTimeout()).isEqualTo(10);
        assertThat(producer.isAllowNonTransactional()).isTrue();
    }

    @Test
    void binderMapsExternalConfigurationToKafkaProperties() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.ack-mode", "MANUAL_IMMEDIATE");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.start-offset", "earliest");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.reset-offsets", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.enable-dlq", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-name", "orders.dlq");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-partitions", "3");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.trusted-packages[0]", "com.acme.events");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.standard-headers", "both");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.replication-factor", "2");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.properties.retention.ms", "120000");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.compression-type", "lz4");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.sync", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.batch-timeout", "50");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[0]", "trace*");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[1]", "eventType");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.use-topic-header", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.record-metadata-channel", "metadata");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.topic.properties.cleanup.policy", "compact");
        source.put("spring.cloud.stream.kafka.binder.brokers[0]", "broker-one");
        source.put("spring.cloud.stream.kafka.binder.brokers[1]", "broker-two:9192");
        source.put("spring.cloud.stream.kafka.binder.default-broker-port", "19092");
        source.put("spring.cloud.stream.kafka.binder.required-acks", "all");
        source.put("spring.cloud.stream.kafka.binder.min-partition-count", "4");
        source.put("spring.cloud.stream.kafka.binder.replication-factor", "3");
        source.put("spring.cloud.stream.kafka.binder.auto-create-topics", "false");
        source.put("spring.cloud.stream.kafka.binder.auto-add-partitions", "true");
        source.put("spring.cloud.stream.kafka.binder.auto-alter-topics", "true");
        source.put("spring.cloud.stream.kafka.binder.headers[0]", "tenant");
        source.put("spring.cloud.stream.kafka.binder.headers[1]", "traceId");
        source.put("spring.cloud.stream.kafka.binder.jaas.login-module", "com.example.LoginModule");
        source.put("spring.cloud.stream.kafka.binder.jaas.control-flag", "sufficient");
        source.put("spring.cloud.stream.kafka.binder.jaas.options.username", "alice");
        source.put("spring.cloud.stream.kafka.binder.health-timeout", "7");
        source.put("spring.cloud.stream.kafka.binder.metrics.default-offset-lag-metrics-enabled", "false");
        source.put("spring.cloud.stream.kafka.binder.metrics.offset-lag-metrics-interval", "2s");
        source.put("spring.cloud.stream.kafka.binder.transaction.transaction-id-prefix", "tx-");
        source.put("spring.cloud.stream.kafka.binder.header-mapper-bean-name", "customHeaderMapper");
        source.put("spring.cloud.stream.kafka.binder.authorization-exception-retry-interval", "5s");
        source.put("spring.cloud.stream.kafka.binder.certificate-store-directory", "/tmp/certs");
        source.put("spring.cloud.stream.kafka.binder.enable-observation", "true");
        source.put("spring.cloud.stream.kafka.binder.health-indicator-consumer-group", "health-group");

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        KafkaBindingProperties binding = binder.bind(
                "spring.cloud.stream.kafka.bindings.orders-in",
                Bindable.of(KafkaBindingProperties.class)).get();
        KafkaBinderConfigurationProperties binderProperties = newBinderProperties();
        binder.bind("spring.cloud.stream.kafka.binder", Bindable.ofInstance(binderProperties));

        KafkaConsumerProperties consumer = binding.getConsumer();
        assertThat(consumer.getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getDlqName()).isEqualTo("orders.dlq");
        assertThat(consumer.getDlqPartitions()).isEqualTo(3);
        assertThat(consumer.getTrustedPackages()).containsExactly("com.acme.events");
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.getTopic().getReplicationFactor()).isEqualTo((short) 2);
        assertThat(consumer.getTopic().getProperties()).containsEntry("retention.ms", "120000");

        KafkaProducerProperties producer = binding.getProducer();
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.lz4);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getBatchTimeout()).isEqualTo(50);
        assertThat(producer.getHeaderPatterns()).containsExactly("trace*", "eventType");
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadata");
        assertThat(producer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");

        assertThat(binderProperties.getBrokers()).containsExactly("broker-one", "broker-two:9192");
        assertThat(binderProperties.getKafkaConnectionString()).isEqualTo("broker-one:19092,broker-two:9192");
        assertThat(binderProperties.getRequiredAcks()).isEqualTo("all");
        assertThat(binderProperties.getMinPartitionCount()).isEqualTo(4);
        assertThat(binderProperties.getReplicationFactor()).isEqualTo((short) 3);
        assertThat(binderProperties.isAutoCreateTopics()).isFalse();
        assertThat(binderProperties.isAutoAddPartitions()).isTrue();
        assertThat(binderProperties.isAutoAlterTopics()).isTrue();
        assertThat(binderProperties.getHeaders()).containsExactly("tenant", "traceId");
        assertThat(binderProperties.getJaas().getLoginModule()).isEqualTo("com.example.LoginModule");
        assertThat(binderProperties.getJaas().getControlFlag()).isEqualTo(ControlFlag.SUFFICIENT);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("username", "alice");
        assertThat(binderProperties.getHealthTimeout()).isEqualTo(7);
        assertThat(binderProperties.getMetrics().isDefaultOffsetLagMetricsEnabled()).isFalse();
        assertThat(binderProperties.getMetrics().getOffsetLagMetricsInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(binderProperties.getTransaction().getTransactionIdPrefix()).isEqualTo("tx-");
        assertThat(binderProperties.getHeaderMapperBeanName()).isEqualTo("customHeaderMapper");
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(binderProperties.getCertificateStoreDirectory()).isEqualTo("/tmp/certs");
        assertThat(binderProperties.isEnableObservation()).isTrue();
        assertThat(binderProperties.getHealthIndicatorConsumerGroup()).isEqualTo("health-group");
    }

    @Test
    void binderConfigurationMergesBootBinderAndBindingLevelClientProperties() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("boot:9092"));
        kafkaProperties.getConsumer().setAutoOffsetReset("latest");
        kafkaProperties.getProducer().setClientId("boot-producer");

        KafkaBinderConfigurationProperties binderProperties = new KafkaBinderConfigurationProperties(
                kafkaProperties, noConnectionDetails());
        binderProperties.setBrokers("binder-one", "binder-two:19092");
        binderProperties.setDefaultBrokerPort("29092");
        binderProperties.setRequiredAcks("all");
        binderProperties.setConfiguration(Map.of(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.GROUP_ID_CONFIG, "ignored-group",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy",
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "1234",
                "schema.registry.url", "http://registry",
                "unknown.client.property", "ignored"));
        binderProperties.setConsumerProperties(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"));
        binderProperties.setProducerProperties(Map.of(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "2048"));

        Map<String, Object> mergedConsumer = binderProperties.mergedConsumerConfiguration();
        assertThat(mergedConsumer).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                "binder-one:29092,binder-two:19092");
        assertThat(mergedConsumer).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        assertThat(mergedConsumer).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");
        assertThat(mergedConsumer).containsEntry("schema.registry.url", "http://registry");
        assertThat(mergedConsumer).doesNotContainKeys(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                ConsumerConfig.GROUP_ID_CONFIG,
                "unknown.client.property");

        Map<String, Object> mergedProducer = binderProperties.mergedProducerConfiguration();
        assertThat(mergedProducer).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                "binder-one:29092,binder-two:19092");
        assertThat(mergedProducer).containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        assertThat(mergedProducer).containsEntry(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "2048");
        assertThat(mergedProducer).containsEntry("schema.registry.url", "http://registry");
        assertThat(mergedProducer).doesNotContainKey("unknown.client.property");

        Map<String, Object> adminProperties = new LinkedHashMap<>();
        KafkaTopicProvisioner.normalalizeBootPropsWithBinder(adminProperties, kafkaProperties, binderProperties);
        assertThat(adminProperties).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                "binder-one:29092,binder-two:19092");
        assertThat(adminProperties).containsEntry(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "1234");
        assertThat(adminProperties).doesNotContainKey("unknown.client.property");
    }

    @Test
    void extendedBindingPropertiesReturnsConfiguredBindingEntries() {
        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        consumer.setEnableDlq(true);
        consumer.setStartOffset(StartOffset.latest);
        KafkaProducerProperties producer = new KafkaProducerProperties();
        producer.setCompressionType(CompressionType.snappy);
        producer.setUseTopicHeader(true);
        KafkaBindingProperties binding = new KafkaBindingProperties();
        binding.setConsumer(consumer);
        binding.setProducer(producer);

        KafkaExtendedBindingProperties extended = new KafkaExtendedBindingProperties();
        extended.setBindings(Map.of("orders", binding));

        assertThat(extended.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.kafka.default");
        assertThat(extended.getExtendedPropertiesEntryClass()).isEqualTo(KafkaBindingProperties.class);
        assertThat(extended.getBindings()).containsEntry("orders", binding);
        assertThat(extended.getExtendedConsumerProperties("orders")).isSameAs(consumer);
        assertThat(extended.getExtendedProducerProperties("orders")).isSameAs(producer);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> extended.getBindings().put("other", new KafkaBindingProperties()));
    }

    @Test
    void bindingUtilsCreatesConsumerAndProducerClientConfigurations() {
        KafkaBinderConfigurationProperties binderProperties = newBinderProperties();
        binderProperties.setBrokers("config-broker");
        binderProperties.setDefaultBrokerPort("19092");
        binderProperties.setRequiredAcks("all");
        binderProperties.getKafkaProperties().getConsumer().setKeyDeserializer(ByteArrayDeserializer.class);
        binderProperties.getKafkaProperties().getConsumer().setValueDeserializer(ByteArrayDeserializer.class);
        binderProperties.getKafkaProperties().getProducer().setKeySerializer(ByteArraySerializer.class);
        binderProperties.getKafkaProperties().getProducer().setValueSerializer(ByteArraySerializer.class);
        binderProperties.setConfiguration(Map.of(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"));

        KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
        kafkaConsumer.setStartOffset(StartOffset.earliest);
        kafkaConsumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5"));
        ExtendedConsumerProperties<KafkaConsumerProperties> consumer = new ExtendedConsumerProperties<>(kafkaConsumer);

        Map<String, Object> consumerConfigs = BindingUtils.createConsumerConfigs(
                true, "orders-group", consumer, binderProperties);

        assertThat(consumerConfigs).containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class);
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class);
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders-group");
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5");
        assertThat(consumerConfigs).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                "config-broker:19092");

        KafkaProducerProperties kafkaProducer = new KafkaProducerProperties();
        kafkaProducer.setBufferSize(4_096);
        kafkaProducer.setBatchTimeout(25);
        kafkaProducer.setCompressionType(CompressionType.lz4);
        kafkaProducer.setConfiguration(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "binding-producer"));
        ExtendedProducerProperties<KafkaProducerProperties> producer = new ExtendedProducerProperties<>(kafkaProducer);

        Map<String, Object> producerConfigs = BindingUtils.createProducerConfigs(producer, binderProperties);

        assertThat(producerConfigs).containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        assertThat(producerConfigs).containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        assertThat(producerConfigs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
        assertThat(producerConfigs).containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, "4096");
        assertThat(producerConfigs).containsEntry(ProducerConfig.LINGER_MS_CONFIG, "25");
        assertThat(producerConfigs).containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        assertThat(producerConfigs).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "binding-producer");
        assertThat(producerConfigs).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                "config-broker:19092");

        kafkaProducer.setConfiguration(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "other:9092"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> BindingUtils.createProducerConfigs(producer, binderProperties))
                .withMessageContaining("bootstrap.servers cannot be overridden");
    }

    @Test
    void kafkaNullConverterHandlesTombstonePayloadsForAnyContentType() {
        KafkaNullConverter converter = new KafkaNullConverter();
        Message<KafkaNull> tombstoneMessage = MessageBuilder.withPayload(KafkaNull.INSTANCE)
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON)
                .build();

        Object convertedPayload = converter.fromMessage(tombstoneMessage, KafkaNull.class);
        Message<?> outboundMessage = converter.toMessage(KafkaNull.INSTANCE, tombstoneMessage.getHeaders());

        assertThat(convertedPayload).isSameAs(KafkaNull.INSTANCE);
        assertThat(outboundMessage).isNotNull();
        assertThat(outboundMessage.getPayload()).isSameAs(KafkaNull.INSTANCE);
        assertThat(outboundMessage.getHeaders()).containsEntry("contentType", MimeTypeUtils.APPLICATION_JSON);
        assertThat(converter.fromMessage(MessageBuilder.withPayload("value").build(), KafkaNull.class)).isNull();
    }

    @Test
    void expressionEvaluatingInterceptorAddsMessageKeyHeaderFromConfiguredExpression() {
        KafkaExpressionEvaluatingInterceptor interceptor = new KafkaExpressionEvaluatingInterceptor(
                new SpelExpressionParser().parseExpression("headers['tenant'] + ':' + payload"),
                new StandardEvaluationContext());
        Message<String> message = MessageBuilder.withPayload("order-42")
                .setHeader("tenant", "acme")
                .build();

        Message<?> intercepted = interceptor.preSend(message, null);

        assertThat(intercepted.getPayload()).isEqualTo("order-42");
        assertThat(intercepted.getHeaders()).containsEntry("tenant", "acme");
        assertThat(intercepted.getHeaders()).containsEntry(
                KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER, "acme:order-42");
        assertThat(message.getHeaders()).doesNotContainKey(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER);
    }

    @Test
    void headerMapperRoundTripsApplicationHeadersAndManagesNeverPatterns() {
        BinderHeaderMapper mapper = new BinderHeaderMapper("tenant", "eventType", "contentType");
        mapper.setEncodeStrings(false);
        mapper.addTrustedPackages("java.util", "java.lang");
        mapper.addToStringClasses("java.time.*");
        Message<String> message = MessageBuilder.withPayload("created")
                .setHeader("tenant", "acme")
                .setHeader("eventType", "OrderCreated")
                .setHeader("ignored", "not mapped")
                .build();

        Headers kafkaHeaders = new RecordHeaders();
        mapper.fromHeaders(message.getHeaders(), kafkaHeaders);

        assertThat(lastHeader(kafkaHeaders, "tenant")).isNotNull();
        assertThat(lastHeader(kafkaHeaders, "eventType")).isNotNull();
        assertThat(lastHeader(kafkaHeaders, "ignored")).isNull();
        assertThat(lastHeader(kafkaHeaders, BinderHeaderMapper.JSON_TYPES)).isNotNull();

        Map<String, Object> mappedHeaders = new LinkedHashMap<>();
        mapper.toHeaders(kafkaHeaders, mappedHeaders);

        assertThat(mappedHeaders).containsEntry("tenant", "acme");
        assertThat(mappedHeaders).containsEntry("eventType", "OrderCreated");
        assertThat(mappedHeaders).doesNotContainKey("ignored");

        String[] patterns = BinderHeaderMapper.addNeverHeaderPatterns(List.of("tenant"));
        assertThat(patterns).contains("tenant", "!id", "!timestamp");
        BinderHeaderMapper.removeNeverHeaders(kafkaHeaders);
        assertThat(lastHeader(kafkaHeaders, "id")).isNull();
        assertThat(lastHeader(kafkaHeaders, "timestamp")).isNull();
    }

    @Test
    void topicValidationDlqUtilitiesAndTopicInformationUseKafkaTypes() {
        KafkaTopicUtils.validateTopicName("orders.created-01_compacted");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KafkaTopicUtils.validateTopicName("orders created"))
                .withMessageContaining("Topic name can only have ASCII alphanumerics");

        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 4, 12L, "key", "payload");
        assertThat(DlqPartitionFunction.ORIGINAL_PARTITION.apply("orders.dlq", record, new RuntimeException("boom")))
                .isEqualTo(4);
        assertThat(DlqPartitionFunction.PARTITION_ZERO.apply("orders.dlq", record, new RuntimeException("boom")))
                .isZero();
        assertThat(DlqPartitionFunction.determineFallbackFunction(null, new CapturingLog()))
                .isSameAs(DlqPartitionFunction.ORIGINAL_PARTITION);
        assertThat(DlqPartitionFunction.determineFallbackFunction(1, new CapturingLog()))
                .isSameAs(DlqPartitionFunction.PARTITION_ZERO);
        CapturingLog log = new CapturingLog();
        assertThat(DlqPartitionFunction.determineFallbackFunction(3, log))
                .isSameAs(DlqPartitionFunction.ORIGINAL_PARTITION);
        assertThat(log.errorMessage()).contains("dlqPartitions");

        DlqDestinationResolver resolver = (consumerRecord, exception) -> consumerRecord.topic() + "." + exception.getClass()
                .getSimpleName();
        assertThat(resolver.apply(record, new IllegalStateException("failed")))
                .isEqualTo("orders.IllegalStateException");

        List<PartitionInfo> partitions = List.of(
                new PartitionInfo("orders", 0, null, null, null),
                new PartitionInfo("orders", 1, null, null, null));
        TopicInformation information = new TopicInformation("group-a", partitions, false);

        assertThat(information.consumerGroup()).isEqualTo("group-a");
        assertThat(information.partitionInfos()).hasSize(2);
        assertThat(information.isTopicPattern()).isFalse();
        assertThat(information.isConsumerTopic()).isTrue();
        assertThat(information).isEqualTo(new TopicInformation("group-a", partitions, false));
        assertThat(information.toString()).contains("group-a", "orders");
    }

    @Test
    void provisionerReturnsDestinationsWithoutConnectingWhenTopicAutoCreationIsDisabled() {
        KafkaBinderConfigurationProperties binderProperties = newBinderProperties();
        binderProperties.setAutoCreateTopics(false);
        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(
                binderProperties, new KafkaProperties(), configs -> configs.put("client.id", "provisioner-test"));
        provisioner.afterPropertiesSet();

        KafkaProducerProperties kafkaProducer = new KafkaProducerProperties();
        kafkaProducer.getTopic().setProperties(Map.of("retention.ms", "60000"));
        ExtendedProducerProperties<KafkaProducerProperties> producer = new ExtendedProducerProperties<>(kafkaProducer);
        producer.setPartitionCount(3);

        ProducerDestination producerDestination = provisioner.provisionProducerDestination("orders", producer);

        assertThat(producerDestination.getName()).isEqualTo("orders");
        assertThat(producerDestination.getNameForPartition(1)).isEqualTo("orders");
        assertThat(producerDestination.toString()).contains("KafkaProducerDestination", "orders");
        assertThat(provisioner.getAdminClientProperties()).containsEntry("client.id", "provisioner-test");

        KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
        ExtendedConsumerProperties<KafkaConsumerProperties> consumer = new ExtendedConsumerProperties<>(kafkaConsumer);
        consumer.setInstanceCount(2);
        consumer.setConcurrency(2);

        ConsumerDestination consumerDestination = provisioner.provisionConsumerDestination("orders", "workers", consumer);

        assertThat(consumerDestination.getName()).isEqualTo("orders");
        assertThat(consumerDestination.toString()).contains("KafkaConsumerDestination", "orders");

        kafkaConsumer.setDestinationIsPattern(true);
        consumer.setMultiplex(true);
        ConsumerDestination multiplexed = provisioner.provisionConsumerDestination("orders.*,payments.*", "workers", consumer);
        assertThat(multiplexed.getName()).isEqualTo("orders.*,payments.*");

        kafkaConsumer.setEnableDlq(true);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> provisioner.provisionConsumerDestination("orders.*", "workers", consumer))
                .withMessageContaining("enableDLQ is not allowed");
    }

    private static KafkaBinderConfigurationProperties newBinderProperties() {
        return new KafkaBinderConfigurationProperties(new KafkaProperties(), noConnectionDetails());
    }

    private static ObjectProvider<KafkaConnectionDetails> noConnectionDetails() {
        return new ObjectProvider<>() {

            @Override
            public KafkaConnectionDetails getObject(Object... args) {
                return null;
            }

            @Override
            public KafkaConnectionDetails getIfAvailable() {
                return null;
            }

            @Override
            public KafkaConnectionDetails getIfUnique() {
                return null;
            }

            @Override
            public Stream<KafkaConnectionDetails> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<KafkaConnectionDetails> orderedStream() {
                return Stream.empty();
            }
        };
    }

    private static Header lastHeader(Headers headers, String name) {
        return headers.lastHeader(name);
    }

    private static final class CapturingLog implements Log {

        private String errorMessage;

        private String errorMessage() {
            return this.errorMessage;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isFatalEnabled() {
            return false;
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void trace(Object message) {
        }

        @Override
        public void trace(Object message, Throwable throwable) {
        }

        @Override
        public void debug(Object message) {
        }

        @Override
        public void debug(Object message, Throwable throwable) {
        }

        @Override
        public void info(Object message) {
        }

        @Override
        public void info(Object message, Throwable throwable) {
        }

        @Override
        public void warn(Object message) {
        }

        @Override
        public void warn(Object message, Throwable throwable) {
        }

        @Override
        public void error(Object message) {
            this.errorMessage = String.valueOf(message);
        }

        @Override
        public void error(Object message, Throwable throwable) {
            error(message);
        }

        @Override
        public void fatal(Object message) {
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
        }
    }
}
