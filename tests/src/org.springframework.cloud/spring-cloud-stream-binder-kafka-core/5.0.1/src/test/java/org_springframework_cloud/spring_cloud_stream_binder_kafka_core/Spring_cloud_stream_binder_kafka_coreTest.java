/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.cloud.stream.binder.BinderException;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.HeaderMode;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper.NonTrustedHeaderType;
import org.springframework.cloud.stream.binder.kafka.common.KafkaBinderEnvironmentPostProcessor;
import org.springframework.cloud.stream.binder.kafka.common.TopicInformation;
import org.springframework.cloud.stream.binder.kafka.properties.JaasLoginModuleConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties.CombinedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StandardHeaders;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StartOffset;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties.CompressionType;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaTopicProperties;
import org.springframework.cloud.stream.binder.kafka.provisioning.KafkaTopicProvisioner;
import org.springframework.cloud.stream.binder.kafka.support.ConsumerConfigCustomizer;
import org.springframework.cloud.stream.binder.kafka.support.ProducerConfigCustomizer;
import org.springframework.cloud.stream.binder.kafka.utils.BindingUtils;
import org.springframework.cloud.stream.binder.kafka.utils.DlqDestinationResolver;
import org.springframework.cloud.stream.binder.kafka.utils.DlqPartitionFunction;
import org.springframework.cloud.stream.binder.kafka.utils.KafkaTopicUtils;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer.ControlFlag;
import org.springframework.kafka.support.KafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

public class Spring_cloud_stream_binder_kafka_coreTest {

    private static final Log TEST_LOG = LogFactory.getLog(Spring_cloud_stream_binder_kafka_coreTest.class);

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    @Test
    void kafkaBinderEnvironmentPostProcessorAddsDefaultKafkaClientConfigurationWithoutOverridingUserProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("applicationProperties", Map.of(
                "spring.kafka.producer.keySerializer", "com.example.CustomSerializer")));

        new KafkaBinderEnvironmentPostProcessor().postProcessEnvironment(environment, null);
        new KafkaBinderEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.kafka.producer.keySerializer"))
                .isEqualTo("com.example.CustomSerializer");
        assertThat(environment.getProperty("spring.kafka.producer.valueSerializer"))
                .isEqualTo(ByteArraySerializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.keyDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.valueDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("logging.level.org.I0Itec.zkclient")).isEqualTo("ERROR");
        assertThat(environment.getProperty("logging.level.kafka.server.KafkaConfig")).isEqualTo("ERROR");
        assertThat(environment.getProperty("logging.level.kafka.admin.AdminClient.AdminConfig")).isEqualTo("ERROR");
        assertThat(environment.getPropertySources())
                .filteredOn(propertySource -> propertySource.getName().equals("kafkaBinderDefaultProperties"))
                .hasSize(1);
    }

    @Test
    void kafkaBindingPropertiesExposeDefaultsAndMutableNestedConfiguration() {
        KafkaBindingProperties binding = new KafkaBindingProperties();
        KafkaConsumerProperties consumer = binding.getConsumer();
        KafkaProducerProperties producer = binding.getProducer();

        assertThat(consumer.isAutoRebalanceEnabled()).isTrue();
        assertThat(consumer.getAckMode()).isNull();
        assertThat(consumer.getStartOffset()).isNull();
        assertThat(consumer.isEnableDlq()).isFalse();
        assertThat(consumer.getDlqProducerProperties()).isNotNull();
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.none);
        assertThat(consumer.getIdleEventInterval()).isEqualTo(30_000L);
        assertThat(consumer.getPollTimeout()).isPositive();
        assertThat(consumer.isTxCommitRecovered()).isTrue();
        assertThat(consumer.isReactiveAutoCommit()).isFalse();
        assertThat(consumer.isReactiveAtMostOnce()).isFalse();

        assertThat(producer.getBufferSize()).isEqualTo(16_384);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.none);
        assertThat(producer.isSync()).isFalse();
        assertThat(producer.getBatchTimeout()).isZero();
        assertThat(producer.getHeaderPatterns()).isNull();
        assertThat(producer.isUseTopicHeader()).isFalse();
        assertThat(producer.getTopic()).isNotNull();
        assertThat(producer.isAllowNonTransactional()).isFalse();

        KafkaProducerProperties dlqProducer = new KafkaProducerProperties();
        dlqProducer.setBufferSize(8_192);
        dlqProducer.setCompressionType(CompressionType.zstd);
        dlqProducer.setBatchTimeout(25);
        dlqProducer.setSync(true);
        dlqProducer.setHeaderPatterns(new String[] { "trace*", "type" });
        dlqProducer.setConfiguration(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "dlq-producer"));

        consumer.setAckMode(AckMode.MANUAL);
        consumer.setAutoCommitOnError(false);
        consumer.setAutoRebalanceEnabled(false);
        consumer.setCommonErrorHandlerBeanName("errors");
        consumer.setConverterBeanName("recordConverter");
        consumer.setDestinationIsPattern(true);
        consumer.setDlqName("orders.dlq");
        consumer.setDlqPartitions(3);
        consumer.setDlqProducerProperties(dlqProducer);
        consumer.setEnableDlq(true);
        consumer.setIdleEventInterval(123L);
        consumer.setPollTimeout(456L);
        consumer.setReactiveAtMostOnce(true);
        consumer.setReactiveAutoCommit(true);
        consumer.setResetOffsets(true);
        consumer.setStandardHeaders(StandardHeaders.both);
        consumer.setStartOffset(StartOffset.earliest);
        consumer.setTopic(topicProperties());
        consumer.setTransactionManager("consumerTx");
        consumer.setTrustedPackages(new String[] { "org.example" });
        consumer.setTxCommitRecovered(false);
        consumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"));

        assertThat(consumer.getAckMode()).isEqualTo(AckMode.MANUAL);
        assertThat(consumer.getAutoCommitOnError()).isFalse();
        assertThat(consumer.isAutoRebalanceEnabled()).isFalse();
        assertThat(consumer.getCommonErrorHandlerBeanName()).isEqualTo("errors");
        assertThat(consumer.getConverterBeanName()).isEqualTo("recordConverter");
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getDlqName()).isEqualTo("orders.dlq");
        assertThat(consumer.getDlqPartitions()).isEqualTo(3);
        assertThat(consumer.getDlqProducerProperties()).isSameAs(dlqProducer);
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getIdleEventInterval()).isEqualTo(123L);
        assertThat(consumer.getPollTimeout()).isEqualTo(456L);
        assertThat(consumer.isReactiveAtMostOnce()).isTrue();
        assertThat(consumer.isReactiveAutoCommit()).isTrue();
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(consumer.getStartOffset().getReferencePoint()).isEqualTo(-2L);
        assertThat(StartOffset.latest.getReferencePoint()).isEqualTo(-1L);
        assertThat(consumer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
        assertThat(consumer.getTransactionManager()).isEqualTo("consumerTx");
        assertThat(consumer.getTrustedPackages()).containsExactly("org.example");
        assertThat(consumer.isTxCommitRecovered()).isFalse();
        assertThat(consumer.getConfiguration()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");

        Expression messageKey = PARSER.parseExpression("'key-' + payload");
        Expression sendTimeout = PARSER.parseExpression("1000 + 250");
        KafkaTopicProperties producerTopic = topicProperties();
        producer.setAllowNonTransactional(true);
        producer.setBatchTimeout(50);
        producer.setBufferSize(32_768);
        producer.setCloseTimeout(9);
        producer.setCompressionType(CompressionType.gzip);
        producer.setConfiguration(Map.of(ProducerConfig.RETRIES_CONFIG, "2"));
        producer.setHeaderPatterns(new String[] { "foo*", "bar" });
        producer.setMessageKeyExpression(messageKey);
        producer.setRecordMetadataChannel("metadataOut");
        producer.setSendTimeoutExpression(sendTimeout);
        producer.setSync(true);
        producer.setTopic(producerTopic);
        producer.setTransactionManager("producerTx");
        producer.setUseTopicHeader(true);

        assertThat(producer.isAllowNonTransactional()).isTrue();
        assertThat(producer.getBatchTimeout()).isEqualTo(50);
        assertThat(producer.getBufferSize()).isEqualTo(32_768);
        assertThat(producer.getCloseTimeout()).isEqualTo(9);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.gzip);
        assertThat(producer.getConfiguration()).containsEntry(ProducerConfig.RETRIES_CONFIG, "2");
        assertThat(producer.getHeaderPatterns()).containsExactly("foo*", "bar");
        assertThat(producer.getMessageKeyExpression()).isSameAs(messageKey);
        assertThat(producer.getTheMessageKeyExpression()).isEqualTo("'key-' + payload");
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadataOut");
        assertThat(producer.getSendTimeoutExpression().getValue()).isEqualTo(1_250);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getTopic()).isSameAs(producerTopic);
        assertThat(producer.getTransactionManager()).isEqualTo("producerTx");
        assertThat(producer.isUseTopicHeader()).isTrue();

        KafkaConsumerProperties replacementConsumer = new KafkaConsumerProperties();
        KafkaProducerProperties replacementProducer = new KafkaProducerProperties();
        binding.setConsumer(replacementConsumer);
        binding.setProducer(replacementProducer);
        assertThat(binding.getConsumer()).isSameAs(replacementConsumer);
        assertThat(binding.getProducer()).isSameAs(replacementProducer);
    }

    @Test
    void binderMapsExternalConfigurationToKafkaProperties() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.ack-mode", "MANUAL_IMMEDIATE");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.auto-rebalance-enabled", "false");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.start-offset", "latest");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.reset-offsets", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.enable-dlq", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-name", "orders.errors");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-partitions", "2");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-producer-properties.compression-type", "lz4");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.trusted-packages[0]", "org.example.events");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.standard-headers", "both");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.converter-bean-name", "converter");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.idle-event-interval", "5000");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.destination-is-pattern", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.configuration[max.poll.records]", "25");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.replication-factor", "2");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.properties[cleanup.policy]", "delete");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.buffer-size", "4096");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.compression-type", "snappy");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.sync", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.batch-timeout", "42");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[0]", "trace*");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[1]", "type");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.configuration[retries]", "3");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.use-topic-header", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.record-metadata-channel", "records");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.close-timeout", "7");
        source.put("spring.cloud.stream.kafka.binder.brokers[0]", "broker-one:9094");
        source.put("spring.cloud.stream.kafka.binder.brokers[1]", "broker-two");
        source.put("spring.cloud.stream.kafka.binder.default-broker-port", "19092");
        source.put("spring.cloud.stream.kafka.binder.headers[0]", "x-trace-id");
        source.put("spring.cloud.stream.kafka.binder.required-acks", "all");
        source.put("spring.cloud.stream.kafka.binder.replication-factor", "2");
        source.put("spring.cloud.stream.kafka.binder.min-partition-count", "4");
        source.put("spring.cloud.stream.kafka.binder.health-timeout", "5");
        source.put("spring.cloud.stream.kafka.binder.auto-create-topics", "false");
        source.put("spring.cloud.stream.kafka.binder.auto-alter-topics", "true");
        source.put("spring.cloud.stream.kafka.binder.auto-add-partitions", "true");
        source.put("spring.cloud.stream.kafka.binder.consider-down-when-any-partition-has-no-leader", "false");
        source.put("spring.cloud.stream.kafka.binder.header-mapper-bean-name", "mapper");
        source.put("spring.cloud.stream.kafka.binder.authorization-exception-retry-interval", "2s");
        source.put("spring.cloud.stream.kafka.binder.enable-observation", "true");
        source.put("spring.cloud.stream.kafka.binder.health-indicator-consumer-group", "health");
        source.put("spring.cloud.stream.kafka.binder.configuration[security.protocol]", "PLAINTEXT");
        source.put("spring.cloud.stream.kafka.binder.consumer-properties[fetch.min.bytes]", "1");
        source.put("spring.cloud.stream.kafka.binder.producer-properties[linger.ms]", "10");
        source.put("spring.cloud.stream.kafka.binder.jaas.login-module", "com.example.LoginModule");
        source.put("spring.cloud.stream.kafka.binder.jaas.control-flag", "sufficient");
        source.put("spring.cloud.stream.kafka.binder.jaas.options[useKeyTab]", "true");
        source.put("spring.cloud.stream.kafka.binder.transaction.transaction-id-prefix", "tx-");
        source.put("spring.cloud.stream.kafka.binder.transaction.producer.partition-count", "8");
        source.put("spring.cloud.stream.kafka.binder.transaction.producer.compression-type", "zstd");
        source.put("spring.cloud.stream.kafka.binder.metrics.default-offset-lag-metrics-enabled", "false");
        source.put("spring.cloud.stream.kafka.binder.metrics.offset-lag-metrics-interval", "15s");

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        KafkaBindingProperties binding = binder.bind(
                "spring.cloud.stream.kafka.bindings.orders-in",
                Bindable.of(KafkaBindingProperties.class)).get();
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        binder.bind("spring.cloud.stream.kafka.binder", Bindable.ofInstance(binderProperties));

        KafkaConsumerProperties consumer = binding.getConsumer();
        assertThat(consumer.getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        assertThat(consumer.isAutoRebalanceEnabled()).isFalse();
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.latest);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getDlqName()).isEqualTo("orders.errors");
        assertThat(consumer.getDlqPartitions()).isEqualTo(2);
        assertThat(consumer.getDlqProducerProperties().getCompressionType()).isEqualTo(CompressionType.lz4);
        assertThat(consumer.getTrustedPackages()).containsExactly("org.example.events");
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.getConverterBeanName()).isEqualTo("converter");
        assertThat(consumer.getIdleEventInterval()).isEqualTo(5_000L);
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getConfiguration()).containsEntry("max.poll.records", "25");
        assertThat(consumer.getTopic().getReplicationFactor()).isEqualTo((short) 2);
        assertThat(consumer.getTopic().getProperties()).containsEntry("cleanup.policy", "delete");

        KafkaProducerProperties producer = binding.getProducer();
        assertThat(producer.getBufferSize()).isEqualTo(4_096);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.snappy);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getBatchTimeout()).isEqualTo(42);
        assertThat(producer.getHeaderPatterns()).containsExactly("trace*", "type");
        assertThat(producer.getConfiguration()).containsEntry("retries", "3");
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("records");
        assertThat(producer.getCloseTimeout()).isEqualTo(7);

        assertThat(binderProperties.getBrokers()).containsExactly("broker-one:9094", "broker-two");
        assertThat(binderProperties.getKafkaConnectionString()).isEqualTo("broker-one:9094,broker-two:19092");
        assertThat(binderProperties.getHeaders()).containsExactly("x-trace-id");
        assertThat(binderProperties.getRequiredAcks()).isEqualTo("all");
        assertThat(binderProperties.getReplicationFactor()).isEqualTo((short) 2);
        assertThat(binderProperties.getMinPartitionCount()).isEqualTo(4);
        assertThat(binderProperties.getHealthTimeout()).isEqualTo(5);
        assertThat(binderProperties.isAutoCreateTopics()).isFalse();
        assertThat(binderProperties.isAutoAlterTopics()).isTrue();
        assertThat(binderProperties.isAutoAddPartitions()).isTrue();
        assertThat(binderProperties.isConsiderDownWhenAnyPartitionHasNoLeader()).isFalse();
        assertThat(binderProperties.getHeaderMapperBeanName()).isEqualTo("mapper");
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(binderProperties.isEnableObservation()).isTrue();
        assertThat(binderProperties.getHealthIndicatorConsumerGroup()).isEqualTo("health");
        assertThat(binderProperties.getConfiguration()).containsEntry("security.protocol", "PLAINTEXT");
        assertThat(binderProperties.getConsumerProperties()).containsEntry("fetch.min.bytes", "1");
        assertThat(binderProperties.getProducerProperties()).containsEntry("linger.ms", "10");
        assertThat(binderProperties.getJaas().getLoginModule()).isEqualTo("com.example.LoginModule");
        assertThat(binderProperties.getJaas().getControlFlag()).isEqualTo(ControlFlag.SUFFICIENT);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("useKeyTab", "true");
        assertThat(binderProperties.getTransaction().getTransactionIdPrefix()).isEqualTo("tx-");
        assertThat(binderProperties.getTransaction().getProducer().getPartitionCount()).isEqualTo(8);
        assertThat(binderProperties.getTransaction().getProducer().getCompressionType()).isEqualTo(CompressionType.zstd);
        assertThat(binderProperties.getMetrics().isDefaultOffsetLagMetricsEnabled()).isFalse();
        assertThat(binderProperties.getMetrics().getOffsetLagMetricsInterval()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void extendedBindingPropertiesReturnsConfiguredBindingEntries() {
        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        consumer.setStartOffset(StartOffset.earliest);
        KafkaProducerProperties producer = new KafkaProducerProperties();
        producer.setCompressionType(CompressionType.zstd);
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
    void binderHeaderMapperRoundTripsTrustedHeadersAndProtectsNeverHeaders() {
        BinderHeaderMapper mapper = new BinderHeaderMapper();
        RecordHeaders kafkaHeaders = new RecordHeaders();

        mapper.fromHeaders(MessageBuilder.withPayload("payload")
                .setHeader("traceId", "abc-123")
                .setHeader("contentType", MimeType.valueOf("application/json;charset=UTF-8"))
                .setHeader("uri", URI.create("https://example.test/orders"))
                .setHeader("bytes", new byte[] { 1, 2, 3 })
                .setHeader("largeNumber", new BigInteger("123456789"))
                .setHeader(BinderHeaders.NATIVE_HEADERS_PRESENT, true)
                .build().getHeaders(), kafkaHeaders);

        assertThat(kafkaHeaders.lastHeader("traceId")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("contentType")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("uri")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("bytes").value()).containsExactly(1, 2, 3);
        assertThat(kafkaHeaders.lastHeader("largeNumber")).isNotNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaders.NATIVE_HEADERS_PRESENT)).isNull();
        assertThat(kafkaHeaders.lastHeader(MessageHeaders.ID)).isNull();
        assertThat(kafkaHeaders.lastHeader(MessageHeaders.TIMESTAMP)).isNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaderMapper.JSON_TYPES)).isNotNull();

        Map<String, Object> mappedHeaders = new LinkedHashMap<>();
        mapper.toHeaders(kafkaHeaders, mappedHeaders);

        assertThat(mappedHeaders.get("traceId")).isEqualTo("abc-123");
        assertThat(String.valueOf(mappedHeaders.get("contentType")))
                .contains("application/json")
                .contains("charset=UTF-8");
        assertThat(mappedHeaders.get("uri")).isEqualTo("https://example.test/orders");
        assertThat(mappedHeaders.get("bytes")).isInstanceOfSatisfying(byte[].class, bytes ->
                assertThat(bytes).containsExactly(1, 2, 3));
        assertThat(mappedHeaders.get("largeNumber")).isInstanceOfSatisfying(NonTrustedHeaderType.class, header -> {
            assertThat(header.getUntrustedType()).isEqualTo(BigInteger.class.getName());
            assertThat(header.toString()).contains(BigInteger.class.getName());
        });
    }

    @Test
    void binderHeaderMapperSupportsStringEncodingPatternsAndStaticHeaderUtilities() {
        BinderHeaderMapper mapper = new BinderHeaderMapper(BinderHeaderMapper.addNeverHeaderPatterns(
                List.of("!secret", "trace*", "type")));
        mapper.setEncodeStrings(true);
        mapper.addTrustedPackages("*");
        mapper.addToStringClasses("java.lang.StringBuilder");
        RecordHeaders kafkaHeaders = new RecordHeaders();

        mapper.fromHeaders(MessageBuilder.withPayload("payload")
                .setHeader("traceId", "abc-123")
                .setHeader("type", new StringBuilder("order"))
                .setHeader("secret", "hidden")
                .build().getHeaders(), kafkaHeaders);

        assertThat(kafkaHeaders.lastHeader("traceId").value()[0]).isEqualTo((byte) '"');
        assertThat(kafkaHeaders.lastHeader("type")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("secret")).isNull();

        Map<String, Object> mappedHeaders = new LinkedHashMap<>();
        mapper.toHeaders(kafkaHeaders, mappedHeaders);

        assertThat(mappedHeaders).containsEntry("traceId", "abc-123").containsEntry("type", "order");

        RecordHeaders headersWithNeverHeaders = new RecordHeaders();
        headersWithNeverHeaders.add(new RecordHeader(MessageHeaders.ID, new byte[] { 1 }));
        headersWithNeverHeaders.add(new RecordHeader(MessageHeaders.TIMESTAMP, new byte[] { 2 }));
        headersWithNeverHeaders.add(new RecordHeader(BinderHeaders.NATIVE_HEADERS_PRESENT, new byte[] { 3 }));
        headersWithNeverHeaders.add(new RecordHeader("business", new byte[] { 4 }));

        BinderHeaderMapper.removeNeverHeaders(headersWithNeverHeaders);

        assertThat(headersWithNeverHeaders.lastHeader(MessageHeaders.ID)).isNull();
        assertThat(headersWithNeverHeaders.lastHeader(MessageHeaders.TIMESTAMP)).isNull();
        assertThat(headersWithNeverHeaders.lastHeader(BinderHeaders.NATIVE_HEADERS_PRESENT)).isNull();
        assertThat(headersWithNeverHeaders.lastHeader("business")).isNotNull();
    }

    @Test
    void bindingUtilsBuildsConsumerAndProducerConfigurations() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        binderProperties.setBrokers("broker-one:19092", "broker-two");
        binderProperties.setDefaultBrokerPort("29092");
        binderProperties.setRequiredAcks("all");
        binderProperties.setConfiguration(new LinkedHashMap<>(Map.of(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT",
                "schema.registry.url", "http://registry")));
        binderProperties.setConsumerProperties(new LinkedHashMap<>(Map.of(
                ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "2",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.GROUP_ID_CONFIG, "ignored")));
        binderProperties.setProducerProperties(new LinkedHashMap<>(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "producer")));

        KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
        kafkaConsumer.setStartOffset(StartOffset.latest);
        kafkaConsumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50"));
        ExtendedConsumerProperties<KafkaConsumerProperties> consumer = new ExtendedConsumerProperties<>(kafkaConsumer);

        Map<String, Object> consumerConfigs = BindingUtils.createConsumerConfigs(false, "orders", consumer,
                binderProperties);

        assertThat(consumerConfigs)
                .containsKeys(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders")
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-one:19092,broker-two:29092")
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                .containsEntry("schema.registry.url", "http://registry")
                .containsEntry(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "2")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");

        KafkaProducerProperties kafkaProducer = new KafkaProducerProperties();
        kafkaProducer.setBufferSize(2048);
        kafkaProducer.setBatchTimeout(17);
        kafkaProducer.setCompressionType(CompressionType.zstd);
        kafkaProducer.setConfiguration(Map.of(ProducerConfig.RETRIES_CONFIG, "4"));
        ExtendedProducerProperties<KafkaProducerProperties> producer = new ExtendedProducerProperties<>(kafkaProducer);

        Map<String, Object> producerConfigs = BindingUtils.createProducerConfigs(producer, binderProperties);

        assertThat(producerConfigs)
                .containsKeys(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-one:19092,broker-two:29092")
                .containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, "2048")
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, "17")
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd")
                .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "producer")
                .containsEntry(ProducerConfig.RETRIES_CONFIG, "4");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> {
                    kafkaProducer.setConfiguration(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "binding:9092"));
                    BindingUtils.createProducerConfigs(producer, binderProperties);
                })
                .withMessageContaining(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
    }

    @Test
    void bindingUtilsResolvesMessageConvertersAndHeaderMappersFromApplicationContext() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        GenericApplicationContext context = new GenericApplicationContext();
        KafkaHeaderMapper namedMapper = new BinderHeaderMapper();
        MessageConverter namedConverter = new MessagingMessageConverter();
        context.registerBean("explicitMapper", KafkaHeaderMapper.class, () -> namedMapper);
        context.registerBean("namedConverter", MessageConverter.class, () -> namedConverter);
        context.refresh();
        try {
            binderProperties.setHeaderMapperBeanName("explicitMapper");
            assertThat(BindingUtils.getHeaderMapper(context, binderProperties)).isSameAs(namedMapper);

            KafkaConsumerProperties defaultConsumer = new KafkaConsumerProperties();
            defaultConsumer.setStandardHeaders(StandardHeaders.both);
            MessageConverter defaultConverter = BindingUtils.getConsumerMessageConverter(context,
                    new ExtendedConsumerProperties<>(defaultConsumer), binderProperties);
            assertThat(defaultConverter).isInstanceOf(MessagingMessageConverter.class);

            KafkaConsumerProperties namedConsumer = new KafkaConsumerProperties();
            namedConsumer.setConverterBeanName("namedConverter");
            assertThat(BindingUtils.getConsumerMessageConverter(context,
                    new ExtendedConsumerProperties<>(namedConsumer), binderProperties)).isSameAs(namedConverter);

            KafkaConsumerProperties missingConsumer = new KafkaConsumerProperties();
            missingConsumer.setConverterBeanName("missingConverter");
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> BindingUtils.getConsumerMessageConverter(context,
                            new ExtendedConsumerProperties<>(missingConsumer), binderProperties))
                    .withMessageContaining("Converter bean not present");
        }
        finally {
            context.close();
        }
    }

    @Test
    void kafkaBinderConfigurationMergesPropertiesAndCopiesConnectionSettings() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        binderProperties.setBrokers("localhost");
        binderProperties.setDefaultBrokerPort("9093");
        binderProperties.setHeaders("trace", "tenant");
        binderProperties.setConfiguration(new LinkedHashMap<>(Map.of(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")));
        binderProperties.setConsumerProperties(new LinkedHashMap<>(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "20")));
        binderProperties.setProducerProperties(new LinkedHashMap<>(Map.of(ProducerConfig.LINGER_MS_CONFIG, "6")));
        binderProperties.setAuthorizationExceptionRetryInterval(Duration.ofSeconds(3));
        binderProperties.setCertificateStoreDirectory(System.getProperty("java.io.tmpdir"));
        binderProperties.setEnableObservation(true);
        binderProperties.setHealthIndicatorConsumerGroup("health-group");

        Map<String, Object> consumerConfiguration = binderProperties.mergedConsumerConfiguration();
        Map<String, Object> producerConfiguration = binderProperties.mergedProducerConfiguration();

        assertThat(binderProperties.getKafkaConnectionString()).isEqualTo("localhost:9093");
        assertThat(binderProperties.getDefaultKafkaConnectionString()).isEqualTo("localhost:9092");
        assertThat(binderProperties.getHeaders()).containsExactly("trace", "tenant");
        assertThat(consumerConfiguration)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "20");
        assertThat(producerConfiguration)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093")
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, "6");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> consumerConfiguration.put("new", "value"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> producerConfiguration.put("new", "value"));
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(binderProperties.getCertificateStoreDirectory()).isEqualTo(System.getProperty("java.io.tmpdir"));
        assertThat(binderProperties.isEnableObservation()).isTrue();
        assertThat(binderProperties.getHealthIndicatorConsumerGroup()).isEqualTo("health-group");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> binderProperties.setConsumerProperties(null))
                .withMessageContaining("consumerProperties");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> binderProperties.setProducerProperties(null))
                .withMessageContaining("producerProperties");
    }

    @Test
    void kafkaBinderConfigurationCopiesCertificateResourcesToConfiguredStoreDirectory() throws IOException {
        Path sourceDirectory = Files.createTempDirectory("kafka-cert-source");
        Path targetDirectory = Files.createTempDirectory("kafka-cert-target");
        try {
            Path truststore = writeCertificateResource(sourceDirectory, "truststore.jks", "truststore");
            Path keystore = writeCertificateResource(sourceDirectory, "keystore.jks", "keystore");
            Path schemaRegistryTruststore = writeCertificateResource(sourceDirectory, "schema-truststore.jks",
                    "schema-truststore");
            Path existingSchemaRegistryKeystore = writeCertificateResource(sourceDirectory,
                    "existing-schema-keystore.jks", "existing-schema-keystore");
            KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
            Map<String, String> configuration = new LinkedHashMap<>();
            configuration.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststore.toUri().toString());
            configuration.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystore.toUri().toString());
            configuration.put("schema.registry.ssl.truststore.location", schemaRegistryTruststore.toUri().toString());
            configuration.put("schema.registry.ssl.keystore.location", existingSchemaRegistryKeystore.toString());
            binderProperties.setConfiguration(configuration);
            binderProperties.setCertificateStoreDirectory(targetDirectory.toString());

            assertThat(binderProperties.getKafkaConnectionString()).isNotBlank();

            Path copiedTruststore = targetDirectory.resolve("truststore.jks");
            Path copiedKeystore = targetDirectory.resolve("keystore.jks");
            Path copiedSchemaRegistryTruststore = targetDirectory.resolve("schema-truststore.jks");
            assertThat(binderProperties.getConfiguration())
                    .containsEntry(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, copiedTruststore.toString())
                    .containsEntry(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, copiedKeystore.toString())
                    .containsEntry("schema.registry.ssl.truststore.location",
                            copiedSchemaRegistryTruststore.toString())
                    .containsEntry("schema.registry.ssl.keystore.location", existingSchemaRegistryKeystore.toString());
            assertThat(Files.readString(copiedTruststore, StandardCharsets.UTF_8)).isEqualTo("truststore");
            assertThat(Files.readString(copiedKeystore, StandardCharsets.UTF_8)).isEqualTo("keystore");
            assertThat(Files.readString(copiedSchemaRegistryTruststore, StandardCharsets.UTF_8))
                    .isEqualTo("schema-truststore");
        }
        finally {
            deleteRecursively(targetDirectory);
            deleteRecursively(sourceDirectory);
        }
    }

    @Test
    void transactionJaasTopicAndCustomizerTypesRemainUsableThroughPublicApi() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        CombinedProducerProperties combined = binderProperties.getTransaction().getProducer();
        Expression partitionKey = PARSER.parseExpression("payload.customerId");
        Expression partitionSelector = PARSER.parseExpression("headers['partition']");

        combined.setPartitionKeyExpression(partitionKey);
        combined.setPartitionSelectorExpression(partitionSelector);
        combined.setPartitionCount(6);
        combined.setRequiredGroups("accounting", "audit");
        combined.setHeaderMode(HeaderMode.headers);
        combined.setUseNativeEncoding(true);
        combined.setErrorChannelEnabled(true);
        combined.setBufferSize(1024);
        combined.setCompressionType(CompressionType.lz4);
        combined.setSync(true);
        combined.setBatchTimeout(33);
        combined.setMessageKeyExpression(PARSER.parseExpression("'message-key'"));
        combined.setHeaderPatterns(new String[] { "trace*" });
        combined.setConfiguration(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "tx-client"));
        combined.setTopic(topicProperties());

        assertThat(combined.getPartitionKeyExpression()).isSameAs(partitionKey);
        assertThat(combined.isPartitioned()).isTrue();
        assertThat(combined.getPartitionSelectorExpression()).isSameAs(partitionSelector);
        assertThat(combined.getPartitionCount()).isEqualTo(6);
        assertThat(combined.getRequiredGroups()).containsExactly("accounting", "audit");
        assertThat(combined.isValidPartitionKeyProperty()).isFalse();
        assertThat(combined.isValidPartitionSelectorProperty()).isFalse();
        assertThat(combined.getHeaderMode()).isEqualTo(HeaderMode.headers);
        assertThat(combined.isUseNativeEncoding()).isTrue();
        assertThat(combined.isErrorChannelEnabled()).isTrue();
        assertThat(combined.getBufferSize()).isEqualTo(1024);
        assertThat(combined.getCompressionType()).isEqualTo(CompressionType.lz4);
        assertThat(combined.isSync()).isTrue();
        assertThat(combined.getBatchTimeout()).isEqualTo(33);
        assertThat(combined.getMessageKeyExpression().getValue()).isEqualTo("message-key");
        assertThat(combined.getHeaderPatterns()).containsExactly("trace*");
        assertThat(combined.getConfiguration()).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "tx-client");
        assertThat(combined.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
        assertThat(combined.getExtension().getCompressionType()).isEqualTo(CompressionType.lz4);

        JaasLoginModuleConfiguration jaas = new JaasLoginModuleConfiguration();
        jaas.setLoginModule("com.example.LoginModule");
        jaas.setControlFlag("optional");
        jaas.setOptions(Map.of("debug", "true"));
        binderProperties.setJaas(jaas);

        assertThat(binderProperties.getJaas().getLoginModule()).isEqualTo("com.example.LoginModule");
        assertThat(binderProperties.getJaas().getControlFlag()).isEqualTo(ControlFlag.OPTIONAL);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("debug", "true");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> jaas.setLoginModule(null))
                .withMessageContaining("cannot be null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> jaas.setControlFlag(null))
                .withMessageContaining("cannot be null");

        Map<String, Object> consumerConfig = new LinkedHashMap<>();
        ConsumerConfigCustomizer consumerCustomizer = (properties, bindingName, destination) ->
                properties.put("consumer." + bindingName + "." + destination, "customized");
        consumerCustomizer.configure(consumerConfig, "orders-in", "orders");
        assertThat(consumerConfig).containsEntry("consumer.orders-in.orders", "customized");

        Map<String, Object> producerConfig = new LinkedHashMap<>();
        ProducerConfigCustomizer producerCustomizer = (properties, bindingName, destination) ->
                properties.put("producer." + bindingName + "." + destination, "customized");
        producerCustomizer.configure(producerConfig, "orders-out", "orders");
        assertThat(producerConfig).containsEntry("producer.orders-out.orders", "customized");
    }

    @Test
    void kafkaTopicProvisionerHandlesNoBrokerPathsAndPartitionValidation() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        binderProperties.setAutoCreateTopics(false);
        binderProperties.setBrokers("broker-one:9092");
        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(binderProperties, new KafkaProperties(),
                properties -> properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "admin-client"));
        provisioner.afterPropertiesSet();

        ProducerDestination producerDestination = provisioner.provisionProducerDestination("orders",
                new ExtendedProducerProperties<>(new KafkaProducerProperties()));
        assertThat(producerDestination.getName()).isEqualTo("orders");
        assertThat(producerDestination.getNameForPartition(3)).isEqualTo("orders");
        assertThat(producerDestination.toString()).contains("KafkaProducerDestination", "orders");

        KafkaConsumerProperties patternConsumerExtension = new KafkaConsumerProperties();
        patternConsumerExtension.setDestinationIsPattern(true);
        ExtendedConsumerProperties<KafkaConsumerProperties> patternConsumer =
                new ExtendedConsumerProperties<>(patternConsumerExtension);
        ConsumerDestination consumerDestination = provisioner.provisionConsumerDestination("orders-.*", "workers",
                patternConsumer);
        assertThat(consumerDestination.getName()).isEqualTo("orders-.*");
        assertThat(consumerDestination.toString()).contains("KafkaConsumerDestination", "orders-.*");

        assertThat(provisioner.getAdminClientProperties())
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-one:9092")
                .containsEntry(AdminClientConfig.CLIENT_ID_CONFIG, "admin-client");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> provisioner.getAdminClientProperties().put("new", "value"));

        List<PartitionInfo> partitions = List.of(
                new PartitionInfo("orders", 0, null, null, null),
                new PartitionInfo("orders", 1, null, null, null),
                new PartitionInfo("orders", 2, null, null, null));
        assertThat(provisioner.getPartitionsForTopic(2, false, () -> partitions, "orders"))
                .containsExactlyElementsOf(partitions);
        assertThat(provisioner.getPartitionsForTopic(5, true, () -> partitions, "orders"))
                .containsExactlyElementsOf(partitions);
        assertThatExceptionOfType(BinderException.class)
                .isThrownBy(() -> provisioner.getPartitionsForTopic(5, false, () -> partitions, "orders"))
                .withMessageContaining("Cannot initialize binder checking the topic");

        KafkaTopicUtils.validateTopicName("orders.created-1_2");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KafkaTopicUtils.validateTopicName("orders created"))
                .withMessageContaining("ASCII alphanumerics");
    }

    @Test
    void kafkaTopicProvisionerNormalizesBootAndBinderAdminProperties() {
        KafkaBinderConfigurationProperties binderProperties = kafkaBinderProperties();
        binderProperties.setBrokers("binder-host");
        binderProperties.setDefaultBrokerPort("19092");
        binderProperties.setConfiguration(new LinkedHashMap<>(Map.of(
                AdminClientConfig.CLIENT_ID_CONFIG, "binder-admin",
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "500")));
        Map<String, Object> adminProperties = new LinkedHashMap<>();
        adminProperties.put(AdminClientConfig.CLIENT_ID_CONFIG, "boot-admin");

        KafkaTopicProvisioner.normalalizeBootPropsWithBinder(adminProperties, new KafkaProperties(), binderProperties);

        assertThat(adminProperties)
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "binder-host:19092")
                .containsEntry(AdminClientConfig.CLIENT_ID_CONFIG, "binder-admin")
                .containsEntry(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "500");

        binderProperties.setConfiguration(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "wrong:9092"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> KafkaTopicProvisioner.normalalizeBootPropsWithBinder(
                        new LinkedHashMap<>(), new KafkaProperties(), binderProperties))
                .withMessageContaining("brokers");
    }

    @Test
    void topicInformationAndDlqUtilitiesExposeFunctionalContracts() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("orders", 4, 12L,
                new byte[] { 1 }, new byte[] { 2 });

        assertThat(DlqPartitionFunction.ORIGINAL_PARTITION.apply("workers", record,
                new IllegalStateException("failed"))).isEqualTo(4);
        assertThat(DlqPartitionFunction.PARTITION_ZERO.apply("workers", record,
                new IllegalStateException("failed"))).isZero();
        assertThat(DlqPartitionFunction.determineFallbackFunction(null, TEST_LOG)
                .apply("workers", record, new RuntimeException())).isEqualTo(4);
        assertThat(DlqPartitionFunction.determineFallbackFunction(1, TEST_LOG)
                .apply("workers", record, new RuntimeException())).isZero();
        assertThat(DlqPartitionFunction.determineFallbackFunction(3, TEST_LOG)
                .apply("workers", record, new RuntimeException())).isEqualTo(4);

        DlqDestinationResolver resolver = (consumerRecord, exception) ->
                consumerRecord.topic() + "." + exception.getClass().getSimpleName() + ".dlq";
        assertThat(resolver.apply(record, new IllegalArgumentException("bad")))
                .isEqualTo("orders.IllegalArgumentException.dlq");

        List<PartitionInfo> partitionInfos = new ArrayList<>();
        partitionInfos.add(new PartitionInfo("orders", 0, null, null, null));
        partitionInfos.add(new PartitionInfo("orders", 1, null, null, null));
        TopicInformation consumerTopic = new TopicInformation("workers", partitionInfos, false);
        TopicInformation producerTopic = new TopicInformation(null, List.of(), true);

        assertThat(consumerTopic.consumerGroup()).isEqualTo("workers");
        assertThat(consumerTopic.partitionInfos()).containsExactlyElementsOf(partitionInfos);
        assertThat(consumerTopic.isTopicPattern()).isFalse();
        assertThat(consumerTopic.isConsumerTopic()).isTrue();
        assertThat(producerTopic.isTopicPattern()).isTrue();
        assertThat(producerTopic.isConsumerTopic()).isFalse();
    }

    private static Path writeCertificateResource(Path directory, String fileName, String content) throws IOException {
        Path path = directory.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                }
                catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
        catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static KafkaTopicProperties topicProperties() {
        KafkaTopicProperties topic = new KafkaTopicProperties();
        topic.setReplicationFactor((short) 2);
        topic.setReplicasAssignments(Map.of(0, List.of(1, 2), 1, List.of(2, 1)));
        topic.setProperties(Map.of("cleanup.policy", "compact"));
        return topic;
    }

    private static KafkaBinderConfigurationProperties kafkaBinderProperties() {
        return new KafkaBinderConfigurationProperties(new KafkaProperties(), emptyKafkaConnectionDetails());
    }

    private static ObjectProvider<KafkaConnectionDetails> emptyKafkaConnectionDetails() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        return beanFactory.getBeanProvider(KafkaConnectionDetails.class);
    }
}
