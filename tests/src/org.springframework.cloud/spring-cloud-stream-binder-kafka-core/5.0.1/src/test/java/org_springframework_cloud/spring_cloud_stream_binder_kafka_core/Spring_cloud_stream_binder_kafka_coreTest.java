/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.HeaderMode;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper;
import org.springframework.cloud.stream.binder.kafka.common.KafkaBinderEnvironmentPostProcessor;
import org.springframework.cloud.stream.binder.kafka.common.TopicInformation;
import org.springframework.cloud.stream.binder.kafka.properties.JaasLoginModuleConfiguration;
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
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer.ControlFlag;
import org.springframework.kafka.support.KafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeTypeUtils;

public class Spring_cloud_stream_binder_kafka_coreTest {

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
        assertThat(consumer.getDlqProducerProperties()).isNotNull();
        assertThat(consumer.getTopic()).isNotNull();

        assertThat(producer.getBufferSize()).isEqualTo(16_384);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.none);
        assertThat(producer.getConfiguration()).isEmpty();
        assertThat(producer.getTopic()).isNotNull();

        KafkaTopicProperties topic = new KafkaTopicProperties();
        topic.setReplicationFactor((short) 3);
        topic.setReplicasAssignments(Map.of(0, List.of(1, 2), 1, List.of(2, 3)));
        topic.setProperties(Map.of("retention.ms", "60000", "cleanup.policy", "compact"));

        KafkaProducerProperties dlqProducer = new KafkaProducerProperties();
        dlqProducer.setCompressionType(CompressionType.zstd);
        dlqProducer.setBufferSize(4_096);

        consumer.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        consumer.setStartOffset(StartOffset.earliest);
        consumer.setResetOffsets(true);
        consumer.setEnableDlq(true);
        consumer.setAutoCommitOnError(Boolean.FALSE);
        consumer.setAutoRebalanceEnabled(false);
        consumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25"));
        consumer.setDlqName("orders.dlq");
        consumer.setDlqPartitions(2);
        consumer.setTrustedPackages(new String[] {"com.example.events", "java.time"});
        consumer.setDlqProducerProperties(dlqProducer);
        consumer.setStandardHeaders(StandardHeaders.both);
        consumer.setConverterBeanName("kafkaConverter");
        consumer.setIdleEventInterval(1_500L);
        consumer.setDestinationIsPattern(true);
        consumer.setTopic(topic);
        consumer.setPollTimeout(12_000L);
        consumer.setTransactionManager("kafkaTxManager");
        consumer.setTxCommitRecovered(false);
        consumer.setCommonErrorHandlerBeanName("commonErrorHandler");
        consumer.setReactiveAutoCommit(true);
        consumer.setReactiveAtMostOnce(true);

        assertThat(consumer.getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(StartOffset.earliest.getReferencePoint()).isEqualTo(-2L);
        assertThat(StartOffset.latest.getReferencePoint()).isEqualTo(-1L);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getAutoCommitOnError()).isFalse();
        assertThat(consumer.isAutoRebalanceEnabled()).isFalse();
        assertThat(consumer.getConfiguration()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25");
        assertThat(consumer.getDlqName()).isEqualTo("orders.dlq");
        assertThat(consumer.getDlqPartitions()).isEqualTo(2);
        assertThat(consumer.getTrustedPackages()).containsExactly("com.example.events", "java.time");
        assertThat(consumer.getDlqProducerProperties()).isSameAs(dlqProducer);
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.getConverterBeanName()).isEqualTo("kafkaConverter");
        assertThat(consumer.getIdleEventInterval()).isEqualTo(1_500L);
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
        assertThat(consumer.getPollTimeout()).isEqualTo(12_000L);
        assertThat(consumer.getTransactionManager()).isEqualTo("kafkaTxManager");
        assertThat(consumer.isTxCommitRecovered()).isFalse();
        assertThat(consumer.getCommonErrorHandlerBeanName()).isEqualTo("commonErrorHandler");
        assertThat(consumer.isReactiveAutoCommit()).isTrue();
        assertThat(consumer.isReactiveAtMostOnce()).isTrue();

        Expression sendTimeout = new SpelExpressionParser().parseExpression("1000 + 250");
        Expression messageKey = new SpelExpressionParser().parseExpression("headers['partitionKey']");
        producer.setBufferSize(8_192);
        producer.setCompressionType(CompressionType.gzip);
        producer.setSync(true);
        producer.setSendTimeoutExpression(sendTimeout);
        producer.setBatchTimeout(250);
        producer.setMessageKeyExpression(messageKey);
        producer.setHeaderPatterns(new String[] {"trace*", "type"});
        producer.setConfiguration(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "orders-producer"));
        producer.setTopic(topic);
        producer.setUseTopicHeader(true);
        producer.setRecordMetadataChannel("metadataChannel");
        producer.setTransactionManager("producerTxManager");
        producer.setCloseTimeout(30);
        producer.setAllowNonTransactional(true);

        assertThat(producer.getBufferSize()).isEqualTo(8_192);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.gzip);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getSendTimeoutExpression().getValue()).isEqualTo(1_250);
        assertThat(producer.getBatchTimeout()).isEqualTo(250);
        assertThat(producer.getMessageKeyExpression()).isSameAs(messageKey);
        assertThat(producer.getTheMessageKeyExpression()).isEqualTo("headers['partitionKey']");
        assertThat(producer.getHeaderPatterns()).containsExactly("trace*", "type");
        assertThat(producer.getConfiguration()).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "orders-producer");
        assertThat(producer.getTopic()).isSameAs(topic);
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadataChannel");
        assertThat(producer.getTransactionManager()).isEqualTo("producerTxManager");
        assertThat(producer.getCloseTimeout()).isEqualTo(30);
        assertThat(producer.isAllowNonTransactional()).isTrue();
    }

    @Test
    void binderMapsExternalConfigurationToKafkaBindingProperties() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.ack-mode", "MANUAL");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.start-offset", "earliest");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.reset-offsets", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.enable-dlq", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-name", "orders.dead");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.dlq-partitions", "2");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.standard-headers", "both");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.destination-is-pattern", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.replication-factor", "3");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.topic.properties[retention.ms]", "60000");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.consumer.configuration[max.poll.records]", "50");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.compression-type", "lz4");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.sync", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.batch-timeout", "75");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.buffer-size", "4096");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[0]", "trace*");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.header-patterns[1]", "eventType");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.use-topic-header", "true");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.record-metadata-channel", "metadata");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.topic.properties[cleanup.policy]", "compact");
        source.put("spring.cloud.stream.kafka.bindings.orders-in.producer.configuration[linger.ms]", "10");

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        KafkaBindingProperties binding = binder.bind(
                "spring.cloud.stream.kafka.bindings.orders-in",
                Bindable.of(KafkaBindingProperties.class)).get();

        KafkaConsumerProperties consumer = binding.getConsumer();
        assertThat(consumer.getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getDlqName()).isEqualTo("orders.dead");
        assertThat(consumer.getDlqPartitions()).isEqualTo(2);
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.both);
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getTopic().getReplicationFactor()).isEqualTo((short) 3);
        assertThat(consumer.getTopic().getProperties()).containsEntry("retention.ms", "60000");
        assertThat(consumer.getConfiguration()).containsEntry("max.poll.records", "50");

        KafkaProducerProperties producer = binding.getProducer();
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.lz4);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getBatchTimeout()).isEqualTo(75);
        assertThat(producer.getBufferSize()).isEqualTo(4_096);
        assertThat(producer.getHeaderPatterns()).containsExactly("trace*", "eventType");
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadata");
        assertThat(producer.getTopic().getProperties()).containsEntry("cleanup.policy", "compact");
        assertThat(producer.getConfiguration()).containsEntry("linger.ms", "10");
    }

    @Test
    void binderConfigurationMergesBootBinderAndClientSpecificKafkaProperties() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("boot-one:9092", "boot-two:9092"));
        kafkaProperties.getConsumer().setGroupId("boot-group");
        kafkaProperties.getProducer().setClientId("boot-producer");

        KafkaBinderConfigurationProperties binderProperties = newBinderProperties(kafkaProperties);
        binderProperties.setBrokers("binder-one:19092", "binder-two");
        binderProperties.setDefaultBrokerPort("29092");
        binderProperties.setHeaders("traceId", "eventType");
        binderProperties.setRequiredAcks("all");
        binderProperties.setReplicationFactor((short) 2);
        binderProperties.setMinPartitionCount(4);
        binderProperties.setHealthTimeout(15);
        binderProperties.setAutoCreateTopics(false);
        binderProperties.setAutoAlterTopics(true);
        binderProperties.setAutoAddPartitions(true);
        binderProperties.setConfiguration(Map.of(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy",
                "schema.registry.url", "http://schema-registry",
                "not.a.kafka.client.property", "ignored"));
        binderProperties.setConsumerProperties(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25"));
        binderProperties.setProducerProperties(Map.of(ProducerConfig.LINGER_MS_CONFIG, "20"));
        binderProperties.setHeaderMapperBeanName("customHeaderMapper");
        binderProperties.setAuthorizationExceptionRetryInterval(Duration.ofSeconds(10));
        binderProperties.setConsiderDownWhenAnyPartitionHasNoLeader(false);
        binderProperties.setCertificateStoreDirectory("cert-store");
        binderProperties.setEnableObservation(true);
        binderProperties.setHealthIndicatorConsumerGroup("health-group");

        JaasLoginModuleConfiguration jaas = new JaasLoginModuleConfiguration();
        jaas.setLoginModule("com.sun.security.auth.module.Krb5LoginModule");
        jaas.setControlFlag("sufficient");
        jaas.setOptions(Map.of("useKeyTab", "true", "storeKey", "true"));
        binderProperties.setJaas(jaas);
        binderProperties.getMetrics().setDefaultOffsetLagMetricsEnabled(false);
        binderProperties.getMetrics().setOffsetLagMetricsInterval(Duration.ofSeconds(30));
        binderProperties.getTransaction().setTransactionIdPrefix("tx-");
        binderProperties.getTransaction().getProducer().setPartitionCount(6);
        binderProperties.getTransaction().getProducer().setRequiredGroups("audit", "archive");
        binderProperties.getTransaction().getProducer().setHeaderMode(HeaderMode.none);
        binderProperties.getTransaction().getProducer().setUseNativeEncoding(true);
        binderProperties.getTransaction().getProducer().setCompressionType(CompressionType.gzip);

        assertThat(binderProperties.getKafkaConnectionString()).isEqualTo("binder-one:19092,binder-two:29092");
        assertThat(binderProperties.getDefaultKafkaConnectionString()).isEqualTo("localhost:9092");
        assertThat(binderProperties.getHeaders()).containsExactly("traceId", "eventType");
        assertThat(binderProperties.getRequiredAcks()).isEqualTo("all");
        assertThat(binderProperties.getReplicationFactor()).isEqualTo((short) 2);
        assertThat(binderProperties.getMinPartitionCount()).isEqualTo(4);
        assertThat(binderProperties.getHealthTimeout()).isEqualTo(15);
        assertThat(binderProperties.isAutoCreateTopics()).isFalse();
        assertThat(binderProperties.isAutoAlterTopics()).isTrue();
        assertThat(binderProperties.isAutoAddPartitions()).isTrue();
        assertThat(binderProperties.getJaas().getControlFlag()).isEqualTo(ControlFlag.SUFFICIENT);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("useKeyTab", "true");
        assertThat(binderProperties.getHeaderMapperBeanName()).isEqualTo("customHeaderMapper");
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(binderProperties.isConsiderDownWhenAnyPartitionHasNoLeader()).isFalse();
        assertThat(binderProperties.getCertificateStoreDirectory()).isEqualTo("cert-store");
        assertThat(binderProperties.isEnableObservation()).isTrue();
        assertThat(binderProperties.getHealthIndicatorConsumerGroup()).isEqualTo("health-group");
        assertThat(binderProperties.getMetrics().isDefaultOffsetLagMetricsEnabled()).isFalse();
        assertThat(binderProperties.getMetrics().getOffsetLagMetricsInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(binderProperties.getTransaction().getTransactionIdPrefix()).isEqualTo("tx-");
        assertThat(binderProperties.getTransaction().getProducer().getPartitionCount()).isEqualTo(6);
        assertThat(binderProperties.getTransaction().getProducer().getRequiredGroups())
                .containsExactly("audit", "archive");
        assertThat(binderProperties.getTransaction().getProducer().getHeaderMode()).isEqualTo(HeaderMode.none);
        assertThat(binderProperties.getTransaction().getProducer().isUseNativeEncoding()).isTrue();
        assertThat(binderProperties.getTransaction().getProducer().getExtension().getCompressionType())
                .isEqualTo(CompressionType.gzip);

        Map<String, Object> consumerConfig = binderProperties.mergedConsumerConfiguration();
        assertThat(consumerConfig)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "25")
                .containsEntry("schema.registry.url", "http://schema-registry")
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "binder-one:19092,binder-two:29092")
                .doesNotContainKey("not.a.kafka.client.property");

        Map<String, Object> producerConfig = binderProperties.mergedProducerConfiguration();
        assertThat(producerConfig)
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, "20")
                .containsEntry("schema.registry.url", "http://schema-registry")
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "binder-one:19092,binder-two:29092")
                .doesNotContainKey("not.a.kafka.client.property");
    }

    @Test
    void bindingUtilsCreatesKafkaClientConfigurationAndRejectsBindingBootstrapOverrides() {
        KafkaBinderConfigurationProperties binderProperties = newBinderProperties(new KafkaProperties());
        binderProperties.setBrokers("broker-a", "broker-b:19092");
        binderProperties.setDefaultBrokerPort("9093");
        binderProperties.setRequiredAcks("all");
        binderProperties.setConfiguration(Map.of("schema.registry.url", "http://registry"));
        binderProperties.setConsumerProperties(Map.of(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000"));
        binderProperties.setProducerProperties(Map.of(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "11000"));

        KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
        kafkaConsumer.setStartOffset(StartOffset.latest);
        kafkaConsumer.setConfiguration(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"));
        ExtendedConsumerProperties<KafkaConsumerProperties> consumer = new ExtendedConsumerProperties<>(kafkaConsumer);
        Map<String, Object> consumerConfig = BindingUtils.createConsumerConfigs(false, "orders-group", consumer,
                binderProperties);

        assertThat(consumerConfig)
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders-group")
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-a:9093,broker-b:19092")
                .containsEntry(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10")
                .containsEntry("schema.registry.url", "http://registry");

        KafkaProducerProperties kafkaProducer = new KafkaProducerProperties();
        kafkaProducer.setBufferSize(32_768);
        kafkaProducer.setBatchTimeout(125);
        kafkaProducer.setCompressionType(CompressionType.zstd);
        kafkaProducer.setConfiguration(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "orders-client"));
        ExtendedProducerProperties<KafkaProducerProperties> producer = new ExtendedProducerProperties<>(kafkaProducer);
        Map<String, Object> producerConfig = BindingUtils.createProducerConfigs(producer, binderProperties);

        assertThat(producerConfig)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-a:9093,broker-b:19092")
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "11000")
                .containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, "32768")
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, "125")
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd")
                .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "orders-client")
                .containsEntry("schema.registry.url", "http://registry");

        KafkaConsumerProperties invalidConsumer = new KafkaConsumerProperties();
        invalidConsumer.setConfiguration(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "other:9092"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> BindingUtils.createConsumerConfigs(true, "group",
                        new ExtendedConsumerProperties<>(invalidConsumer), binderProperties))
                .withMessageContaining("bootstrap.servers cannot be overridden");

        KafkaProducerProperties invalidProducer = new KafkaProducerProperties();
        invalidProducer.setConfiguration(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "other:9092"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> BindingUtils.createProducerConfigs(
                        new ExtendedProducerProperties<>(invalidProducer), binderProperties))
                .withMessageContaining("bootstrap.servers cannot be overridden");
    }

    @Test
    void bindingUtilsResolvesConfiguredHeaderMapperAndConsumerMessageConverterBeans() {
        BinderHeaderMapper fallbackHeaderMapper = new BinderHeaderMapper("fallback*");
        BinderHeaderMapper namedHeaderMapper = new BinderHeaderMapper("named*");
        MessagingMessageConverter namedMessageConverter = new MessagingMessageConverter();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("kafkaBinderHeaderMapper", KafkaHeaderMapper.class, () -> fallbackHeaderMapper);
            context.registerBean("ordersKafkaHeaderMapper", KafkaHeaderMapper.class, () -> namedHeaderMapper);
            context.registerBean("ordersKafkaConverter", MessageConverter.class, () -> namedMessageConverter);
            context.refresh();

            KafkaBinderConfigurationProperties binderProperties = newBinderProperties(new KafkaProperties());
            assertThat(BindingUtils.getHeaderMapper(context, binderProperties)).isSameAs(fallbackHeaderMapper);

            binderProperties.setHeaderMapperBeanName("ordersKafkaHeaderMapper");
            assertThat(BindingUtils.getHeaderMapper(context, binderProperties)).isSameAs(namedHeaderMapper);

            KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
            kafkaConsumer.setConverterBeanName("ordersKafkaConverter");
            ExtendedConsumerProperties<KafkaConsumerProperties> consumer =
                    new ExtendedConsumerProperties<>(kafkaConsumer);

            assertThat(BindingUtils.getConsumerMessageConverter(context, consumer, binderProperties))
                    .isSameAs(namedMessageConverter);
        }
    }

    @Test
    void environmentPostProcessorContributesKafkaBinderDefaultsWithoutOverridingUserProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("userProperties", Map.of(
                "spring.kafka.producer.keySerializer", StringSerializer.class.getName(),
                "logging.level.kafka.server.KafkaConfig", "INFO")));

        KafkaBinderEnvironmentPostProcessor postProcessor = new KafkaBinderEnvironmentPostProcessor();
        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.kafka.producer.keySerializer"))
                .isEqualTo(StringSerializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.producer.valueSerializer"))
                .isEqualTo(ByteArraySerializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.keyDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.valueDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("logging.level.kafka.server.KafkaConfig")).isEqualTo("INFO");
        assertThat(environment.getProperty("logging.level.org.I0Itec.zkclient")).isEqualTo("ERROR");
        assertThat(environment.getPropertySources().stream()
                .filter(propertySource -> propertySource.getName().equals("kafkaBinderDefaultProperties")))
                .hasSize(1);
    }

    @Test
    void headerMapperRoundTripsTrustedHeadersAndRemovesNeverHeaders() {
        BinderHeaderMapper mapper = new BinderHeaderMapper(BinderHeaderMapper.addNeverHeaderPatterns(List.of("*")));
        mapper.setEncodeStrings(true);
        mapper.addTrustedPackages("java.time");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("stringHeader", "alpha");
        source.put("integerHeader", 42);
        source.put("mimeHeader", MimeTypeUtils.APPLICATION_JSON);

        Headers kafkaHeaders = new RecordHeaders();
        mapper.fromHeaders(new MessageHeaders(source), kafkaHeaders);

        assertThat(kafkaHeaders.lastHeader("stringHeader")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("integerHeader")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("mimeHeader")).isNotNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaderMapper.JSON_TYPES)).isNotNull();
        assertThat(kafkaHeaders.lastHeader("id")).isNull();
        assertThat(kafkaHeaders.lastHeader("timestamp")).isNull();

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapper.toHeaders(kafkaHeaders, mapped);

        assertThat(mapped)
                .containsEntry("stringHeader", "alpha")
                .containsEntry("integerHeader", 42)
                .containsEntry("mimeHeader", "application/json");

        kafkaHeaders.add(new RecordHeader("deliveryAttempt", "3".getBytes(StandardCharsets.UTF_8)));
        kafkaHeaders.add(new RecordHeader("scst_nativeHeadersPresent", "true".getBytes(StandardCharsets.UTF_8)));
        BinderHeaderMapper.removeNeverHeaders(kafkaHeaders);
        assertThat(kafkaHeaders.lastHeader("deliveryAttempt")).isNull();
        assertThat(kafkaHeaders.lastHeader("scst_nativeHeadersPresent")).isNull();
    }

    @Test
    void extendedBindingPropertiesReturnsConfiguredBindingEntries() {
        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        consumer.setEnableDlq(true);
        consumer.setDlqName("orders.dlq");
        KafkaProducerProperties producer = new KafkaProducerProperties();
        producer.setCompressionType(CompressionType.snappy);
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
    void provisionerBuildsDestinationsAndAdminPropertiesWithoutBrokerConnection() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("boot:9092"));
        KafkaBinderConfigurationProperties binderProperties = newBinderProperties(kafkaProperties);
        binderProperties.setBrokers("admin-broker");
        binderProperties.setDefaultBrokerPort("19092");
        binderProperties.setAutoCreateTopics(false);
        binderProperties.setConfiguration(Map.of(
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000",
                ProducerConfig.LINGER_MS_CONFIG, "ignored-for-admin"));

        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(binderProperties, kafkaProperties,
                List.of(properties -> properties.put("client.id", "provisioner-test")));

        assertThat(provisioner.getAdminClientProperties())
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "admin-broker:19092")
                .containsEntry(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
                .containsEntry("client.id", "provisioner-test")
                .doesNotContainEntry(ProducerConfig.LINGER_MS_CONFIG, "ignored-for-admin");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> provisioner.getAdminClientProperties().put("x", "y"));

        KafkaProducerProperties kafkaProducer = new KafkaProducerProperties();
        ExtendedProducerProperties<KafkaProducerProperties> producer = new ExtendedProducerProperties<>(kafkaProducer);
        producer.setPartitionCount(3);
        ProducerDestination producerDestination = provisioner.provisionProducerDestination("orders", producer);
        assertThat(producerDestination.getName()).isEqualTo("orders");
        assertThat(producerDestination.getNameForPartition(2)).isEqualTo("orders");
        assertThat(producerDestination.toString()).contains("KafkaProducerDestination", "orders");

        KafkaConsumerProperties kafkaConsumer = new KafkaConsumerProperties();
        ExtendedConsumerProperties<KafkaConsumerProperties> consumer = new ExtendedConsumerProperties<>(kafkaConsumer);
        consumer.setMultiplex(true);
        ConsumerDestination consumerDestination = provisioner.provisionConsumerDestination("orders,returns", "workers",
                consumer);
        assertThat(consumerDestination.getName()).isEqualTo("orders,returns");
        assertThat(consumerDestination.toString()).contains("KafkaConsumerDestination", "orders,returns");

        kafkaConsumer.setDestinationIsPattern(true);
        kafkaConsumer.setEnableDlq(true);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> provisioner.provisionConsumerDestination("orders-.*", "workers", consumer))
                .withMessageContaining("enableDLQ is not allowed when listening to topic patterns");
    }

    @Test
    void topicUtilitiesRecordsAndDlqFunctionsRemainUsableThroughPublicApi() {
        KafkaTopicUtils.validateTopicName("orders.created-1");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> KafkaTopicUtils.validateTopicName("orders created"));

        List<PartitionInfo> partitions = List.of(
                new PartitionInfo("orders", 0, null, null, null),
                new PartitionInfo("orders", 1, null, null, null));
        TopicInformation information = new TopicInformation("workers", partitions, false);
        assertThat(information.consumerGroup()).isEqualTo("workers");
        assertThat(information.partitionInfos()).containsExactlyElementsOf(partitions);
        assertThat(information.isTopicPattern()).isFalse();
        assertThat(information.isConsumerTopic()).isTrue();
        assertThat(information.toString()).contains("workers", "orders");
        assertThat(new TopicInformation("workers", partitions, false)).isEqualTo(information);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("orders", 3, 12L, new byte[] {1},
                new byte[] {2});
        assertThat(DlqPartitionFunction.ORIGINAL_PARTITION.apply("orders.dlq", record, new RuntimeException("failed")))
                .isEqualTo(3);
        assertThat(DlqPartitionFunction.PARTITION_ZERO.apply("orders.dlq", record, new RuntimeException("failed")))
                .isZero();
        assertThat(DlqPartitionFunction.determineFallbackFunction(null, LogFactory.getLog(getClass()))
                .apply("orders.dlq", record, null)).isEqualTo(3);
        assertThat(DlqPartitionFunction.determineFallbackFunction(1, LogFactory.getLog(getClass()))
                .apply("orders.dlq", record, null)).isZero();

        DlqDestinationResolver resolver = (consumerRecord, exception) ->
                consumerRecord.topic() + "." + exception.getClass().getSimpleName();
        assertThat(resolver.apply(record, new IllegalStateException("boom")))
                .isEqualTo("orders.IllegalStateException");
    }

    private static KafkaBinderConfigurationProperties newBinderProperties(KafkaProperties kafkaProperties) {
        return new KafkaBinderConfigurationProperties(kafkaProperties, noConnectionDetails());
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
            public KafkaConnectionDetails getObject() {
                return null;
            }
        };
    }
}
