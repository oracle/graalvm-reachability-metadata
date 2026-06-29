/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_core;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
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
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StandardHeaders;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties.StartOffset;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties.CompressionType;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaTopicProperties;
import org.springframework.cloud.stream.binder.kafka.utils.BindingUtils;
import org.springframework.cloud.stream.binder.kafka.utils.KafkaTopicUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer;
import org.springframework.kafka.support.KafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Spring_cloud_stream_binder_kafka_coreTest {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Test
    void binderHeaderMapperRoundTripsHeadersAndProtectsInternalHeaders() {
        BinderHeaderMapper mapper = new BinderHeaderMapper();
        Map<String, Object> source = new HashMap<>();
        source.put("plainText", "hello");
        source.put("binary", "raw-bytes".getBytes(StandardCharsets.UTF_8));
        source.put("mime", MimeTypeUtils.APPLICATION_JSON);
        source.put("numbers", new HashMap<>(Map.of("one", 1, "two", 2)));
        source.put(MessageHeaders.ID, UUID.randomUUID());
        source.put(MessageHeaders.TIMESTAMP, 123L);
        source.put(IntegrationMessageHeaderAccessor.DELIVERY_ATTEMPT, 2);
        source.put(BinderHeaders.NATIVE_HEADERS_PRESENT, true);

        Headers kafkaHeaders = new RecordHeaders();
        mapper.fromHeaders(new MessageHeaders(source), kafkaHeaders);

        assertThat(kafkaHeaders.lastHeader("plainText").value()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(kafkaHeaders.lastHeader("binary").value()).isEqualTo("raw-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(kafkaHeaders.lastHeader(BinderHeaderMapper.JSON_TYPES)).isNotNull();
        assertThat(kafkaHeaders.lastHeader(MessageHeaders.ID)).isNull();
        assertThat(kafkaHeaders.lastHeader(MessageHeaders.TIMESTAMP)).isNull();
        assertThat(kafkaHeaders.lastHeader(IntegrationMessageHeaderAccessor.DELIVERY_ATTEMPT)).isNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaders.NATIVE_HEADERS_PRESENT)).isNull();

        Map<String, Object> mapped = new HashMap<>();
        mapper.toHeaders(kafkaHeaders, mapped);

        assertThat(mapped.get("plainText")).isEqualTo("hello");
        assertThat(mapped.get("binary")).isEqualTo("raw-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(mapped.get("mime")).isEqualTo("application/json");
        assertThat(mapped.get("numbers")).isEqualTo(Map.of("one", 1, "two", 2));
    }

    @Test
    void binderHeaderMapperSupportsEncodedStringsLegacyMimeTypesAndUntrustedTypes() {
        BinderHeaderMapper encodedMapper = new BinderHeaderMapper();
        encodedMapper.setEncodeStrings(true);
        Headers encodedHeaders = new RecordHeaders();
        encodedMapper.fromHeaders(new MessageHeaders(Map.of("encoded", "value")), encodedHeaders);
        String encodedValue = new String(encodedHeaders.lastHeader("encoded").value(), StandardCharsets.UTF_8);
        assertThat(encodedValue).isEqualTo("\"value\"");

        BinderHeaderMapper mapper = new BinderHeaderMapper();
        Headers legacyMimeHeaders = new RecordHeaders();
        legacyMimeHeaders.add(new RecordHeader("legacyMime", bytes("""
                {"type":"text","subtype":"plain","parameters":{"charset":"UTF-8"}}
                """.trim())));
        legacyMimeHeaders.add(new RecordHeader(BinderHeaderMapper.JSON_TYPES, bytes("""
                {"legacyMime":"org.springframework.util.MimeType"}
                """.trim())));
        Map<String, Object> legacyMapped = new HashMap<>();
        mapper.toHeaders(legacyMimeHeaders, legacyMapped);

        assertThat(legacyMapped.get("legacyMime")).isEqualTo(new MimeType("text", "plain", Map.of("charset", "UTF-8")));

        Headers untrustedHeaders = new RecordHeaders();
        untrustedHeaders.add(new RecordHeader("suspicious", bytes("{}")));
        untrustedHeaders.add(new RecordHeader(BinderHeaderMapper.JSON_TYPES, bytes("""
                {"suspicious":"example.UntrustedType"}
                """.trim())));
        Map<String, Object> untrustedMapped = new HashMap<>();
        mapper.toHeaders(untrustedHeaders, untrustedMapped);

        assertThat(untrustedMapped.get("suspicious")).isInstanceOfSatisfying(NonTrustedHeaderType.class, header -> {
            assertThat(header.getHeaderValue()).isEqualTo(bytes("{}"));
            assertThat(header.getUntrustedType()).isEqualTo("example.UntrustedType");
            assertThat(header).hasToString(
                    "NonTrustedHeaderType [headerValue={}, untrustedType=example.UntrustedType]");
        });

        mapper.addTrustedPackages("*");
        Headers trustedHeaders = new RecordHeaders();
        trustedHeaders.add(new RecordHeader("integer", bytes("42")));
        trustedHeaders.add(new RecordHeader(BinderHeaderMapper.JSON_TYPES, bytes("""
                {"integer":"java.lang.Integer"}
                """.trim())));
        Map<String, Object> trustedMapped = new HashMap<>();
        mapper.toHeaders(trustedHeaders, trustedMapped);
        assertThat(trustedMapped.get("integer")).isEqualTo(42);
    }

    @Test
    void binderHeaderMapperPatternUtilitiesFilterNeverHeaders() {
        String[] patterns = BinderHeaderMapper.addNeverHeaderPatterns(List.of("trace-*", "*"));
        assertThat(patterns).containsSequence("!id", "!timestamp", "!deliveryAttempt",
                "!" + BinderHeaders.NATIVE_HEADERS_PRESENT, "trace-*", "*");

        Headers headers = new RecordHeaders();
        headers.add(MessageHeaders.ID, bytes("id"));
        headers.add(MessageHeaders.TIMESTAMP, bytes("timestamp"));
        headers.add(IntegrationMessageHeaderAccessor.DELIVERY_ATTEMPT, bytes("delivery"));
        headers.add(BinderHeaders.NATIVE_HEADERS_PRESENT, bytes("native"));
        headers.add("application", bytes("kept"));

        BinderHeaderMapper.removeNeverHeaders(headers);

        assertThat(headers.lastHeader(MessageHeaders.ID)).isNull();
        assertThat(headers.lastHeader(MessageHeaders.TIMESTAMP)).isNull();
        assertThat(headers.lastHeader(IntegrationMessageHeaderAccessor.DELIVERY_ATTEMPT)).isNull();
        assertThat(headers.lastHeader(BinderHeaders.NATIVE_HEADERS_PRESENT)).isNull();
        assertThat(headers.lastHeader("application").value()).isEqualTo(bytes("kept"));
    }

    @Test
    void kafkaBinderEnvironmentPostProcessorAddsByteArrayKafkaDefaultsWithoutOverridingUserProperties() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("userProperties", Map.of(
                "spring.kafka.producer.keySerializer", StringSerializer.class.getName())));

        KafkaBinderEnvironmentPostProcessor processor = new KafkaBinderEnvironmentPostProcessor();
        processor.postProcessEnvironment(environment, null);
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.kafka.producer.keySerializer"))
                .isEqualTo(StringSerializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.producer.valueSerializer"))
                .isEqualTo(ByteArraySerializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.keyDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("spring.kafka.consumer.valueDeserializer"))
                .isEqualTo(ByteArrayDeserializer.class.getName());
        assertThat(environment.getProperty("logging.level.kafka.server.KafkaConfig")).isEqualTo("ERROR");
        assertThat(environment.getPropertySources()).extracting(source -> source.getName())
                .containsOnlyOnce("kafkaBinderDefaultProperties");
    }

    @Test
    void binderConfigurationMergesConsumerAndProducerConfiguration() {
        KafkaBinderConfigurationProperties properties = binderProperties();
        properties.setBrokers("broker-one", "broker-two:19092");
        properties.setDefaultBrokerPort("19093");
        properties.setRequiredAcks("all");
        properties.setConfiguration(new HashMap<>(Map.of(
                ConsumerConfig.CLIENT_ID_CONFIG, "binder-client",
                "schema.registry.url", "mock://schema")));
        properties.setConsumerProperties(new HashMap<>(Map.of(
                ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "3",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.GROUP_ID_CONFIG, "ignored")));
        properties.setProducerProperties(new HashMap<>(Map.of(ProducerConfig.MAX_BLOCK_MS_CONFIG, "25")));

        Map<String, Object> consumerConfiguration = properties.mergedConsumerConfiguration();
        Map<String, Object> producerConfiguration = properties.mergedProducerConfiguration();

        assertThat(properties.getKafkaConnectionString()).isEqualTo("broker-one:19093,broker-two:19092");
        assertThat(consumerConfiguration).containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "broker-one:19093,broker-two:19092");
        assertThat(consumerConfiguration).containsEntry(ConsumerConfig.CLIENT_ID_CONFIG, "binder-client");
        assertThat(consumerConfiguration).containsEntry("schema.registry.url", "mock://schema");
        assertThat(consumerConfiguration).containsEntry(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "3");
        assertThat(consumerConfiguration).doesNotContainKeys(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                ConsumerConfig.GROUP_ID_CONFIG);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> consumerConfiguration.put("not", "mutable"));

        assertThat(producerConfiguration).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "broker-one:19093,broker-two:19092");
        assertThat(producerConfiguration).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "binder-client");
        assertThat(producerConfiguration).containsEntry("schema.registry.url", "mock://schema");
        assertThat(producerConfiguration).containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, "25");
    }

    @Test
    void bindingUtilsCreatesConsumerAndProducerConfigsFromExtendedProperties() {
        KafkaBinderConfigurationProperties binderProperties = binderProperties();
        binderProperties.setBrokers("kafka-broker");
        binderProperties.setRequiredAcks("all");

        KafkaConsumerProperties consumerExtension = new KafkaConsumerProperties();
        consumerExtension.setStartOffset(StartOffset.latest);
        consumerExtension.setConfiguration(new HashMap<>(Map.of(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "7")));
        ExtendedConsumerProperties<KafkaConsumerProperties> consumerProperties =
                new ExtendedConsumerProperties<>(consumerExtension);

        Map<String, Object> consumerConfigs = BindingUtils.createConsumerConfigs(false, "orders", consumerProperties,
                binderProperties);

        assertThat(consumerConfigs).containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "orders");
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-broker:9092");
        assertThat(consumerConfigs).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "7");

        KafkaProducerProperties producerExtension = new KafkaProducerProperties();
        producerExtension.setBufferSize(32_768);
        producerExtension.setBatchTimeout(55);
        producerExtension.setCompressionType(CompressionType.gzip);
        producerExtension.setConfiguration(new HashMap<>(Map.of(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                "1")));
        ExtendedProducerProperties<KafkaProducerProperties> producerProperties =
                new ExtendedProducerProperties<>(producerExtension);

        Map<String, Object> producerConfigs = BindingUtils.createProducerConfigs(producerProperties, binderProperties);

        assertThat(producerConfigs).containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        assertThat(producerConfigs).containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        assertThat(producerConfigs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
        assertThat(producerConfigs).containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, "32768");
        assertThat(producerConfigs).containsEntry(ProducerConfig.LINGER_MS_CONFIG, "55");
        assertThat(producerConfigs).containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        assertThat(producerConfigs).containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1");
    }

    @Test
    void bindingUtilsRejectsBindingLevelBootstrapOverrides() {
        KafkaConsumerProperties consumerExtension = new KafkaConsumerProperties();
        consumerExtension.setConfiguration(new HashMap<>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "forbidden")));
        ExtendedConsumerProperties<KafkaConsumerProperties> consumerProperties =
                new ExtendedConsumerProperties<>(consumerExtension);

        assertThatIllegalStateException().isThrownBy(() -> BindingUtils.createConsumerConfigs(false, "group",
                consumerProperties, binderProperties()))
            .withMessageContaining(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);

        KafkaProducerProperties producerExtension = new KafkaProducerProperties();
        producerExtension.setConfiguration(new HashMap<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "forbidden")));
        ExtendedProducerProperties<KafkaProducerProperties> producerProperties =
                new ExtendedProducerProperties<>(producerExtension);

        assertThatIllegalStateException().isThrownBy(() -> BindingUtils.createProducerConfigs(producerProperties,
                binderProperties()))
            .withMessageContaining(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
    }

    @Test
    void bindingUtilsResolvesMessageConvertersAndHeaderMappersFromApplicationContext() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            BinderHeaderMapper namedHeaderMapper = new BinderHeaderMapper();
            MessagingMessageConverter customConverter = new MessagingMessageConverter();
            context.registerBean("kafkaBinderHeaderMapper", KafkaHeaderMapper.class, () -> namedHeaderMapper);
            context.registerBean("customConverter", MessageConverter.class, () -> customConverter);
            context.refresh();

            KafkaBinderConfigurationProperties binderProperties = binderProperties();
            KafkaConsumerProperties defaultConsumerExtension = new KafkaConsumerProperties();
            defaultConsumerExtension.setStandardHeaders(StandardHeaders.both);
            ExtendedConsumerProperties<KafkaConsumerProperties> defaultConsumerProperties =
                    new ExtendedConsumerProperties<>(defaultConsumerExtension);

            MessageConverter defaultConverter = BindingUtils.getConsumerMessageConverter(context,
                    defaultConsumerProperties, binderProperties);
            assertThat(defaultConverter).isInstanceOf(MessagingMessageConverter.class);
            assertThat(BindingUtils.getHeaderMapper(context, binderProperties)).isSameAs(namedHeaderMapper);

            KafkaConsumerProperties customConsumerExtension = new KafkaConsumerProperties();
            customConsumerExtension.setConverterBeanName("customConverter");
            ExtendedConsumerProperties<KafkaConsumerProperties> customConsumerProperties =
                    new ExtendedConsumerProperties<>(customConsumerExtension);
            assertThat(BindingUtils.getConsumerMessageConverter(context, customConsumerProperties, binderProperties))
                    .isSameAs(customConverter);

            binderProperties.setHeaderMapperBeanName("kafkaBinderHeaderMapper");
            assertThat(BindingUtils.getHeaderMapper(context, binderProperties)).isSameAs(namedHeaderMapper);
        }
    }

    @Test
    void kafkaSpecificPropertyObjectsExposeNestedConfiguration() {
        KafkaProducerProperties producer = new KafkaProducerProperties();
        Expression keyExpression = parser.parseExpression("'order-' + payload");
        producer.setBufferSize(1_024);
        producer.setBatchTimeout(12);
        producer.setCompressionType(CompressionType.zstd);
        producer.setSync(true);
        producer.setMessageKeyExpression(keyExpression);
        producer.setSendTimeoutExpression(parser.parseExpression("5000"));
        producer.setHeaderPatterns(new String[] { "tenant-*", "trace-*" });
        producer.setUseTopicHeader(true);
        producer.setRecordMetadataChannel("metadataChannel");
        producer.setTransactionManager("kafkaTransactionManager");
        producer.setCloseTimeout(5);
        producer.setAllowNonTransactional(true);
        producer.setConfiguration(new HashMap<>(Map.of("delivery.timeout.ms", "120000")));

        assertThat(producer.getBufferSize()).isEqualTo(1_024);
        assertThat(producer.getBatchTimeout()).isEqualTo(12);
        assertThat(producer.getCompressionType()).isEqualTo(CompressionType.zstd);
        assertThat(producer.isSync()).isTrue();
        assertThat(producer.getMessageKeyExpression()).isSameAs(keyExpression);
        assertThat(producer.getTheMessageKeyExpression()).isEqualTo("'order-' + payload");
        assertThat(producer.getSendTimeoutExpression().getValue()).isEqualTo(5000);
        assertThat(producer.getHeaderPatterns()).containsExactly("tenant-*", "trace-*");
        assertThat(producer.isUseTopicHeader()).isTrue();
        assertThat(producer.getRecordMetadataChannel()).isEqualTo("metadataChannel");
        assertThat(producer.getTransactionManager()).isEqualTo("kafkaTransactionManager");
        assertThat(producer.getCloseTimeout()).isEqualTo(5);
        assertThat(producer.isAllowNonTransactional()).isTrue();
        assertThat(producer.getConfiguration()).containsEntry("delivery.timeout.ms", "120000");

        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        KafkaProducerProperties dlqProducer = new KafkaProducerProperties();
        dlqProducer.setCompressionType(CompressionType.lz4);
        consumer.setAckMode(ContainerProperties.AckMode.MANUAL);
        consumer.setStartOffset(StartOffset.earliest);
        consumer.setResetOffsets(true);
        consumer.setEnableDlq(true);
        consumer.setAutoCommitOnError(Boolean.FALSE);
        consumer.setAutoRebalanceEnabled(false);
        consumer.setDlqName("orders.dlq");
        consumer.setDlqPartitions(3);
        consumer.setTrustedPackages(new String[] { "com.example" });
        consumer.setDlqProducerProperties(dlqProducer);
        consumer.setStandardHeaders(StandardHeaders.timestamp);
        consumer.setConverterBeanName("converter");
        consumer.setIdleEventInterval(1_500L);
        consumer.setDestinationIsPattern(true);
        consumer.setPollTimeout(250L);
        consumer.setTransactionManager("consumerTxManager");
        consumer.setTxCommitRecovered(false);
        consumer.setCommonErrorHandlerBeanName("handler");
        consumer.setReactiveAutoCommit(true);
        consumer.setReactiveAtMostOnce(true);

        assertThat(consumer.getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL);
        assertThat(consumer.getStartOffset()).isEqualTo(StartOffset.earliest);
        assertThat(StartOffset.earliest.getReferencePoint()).isEqualTo(-2L);
        assertThat(StartOffset.latest.getReferencePoint()).isEqualTo(-1L);
        assertThat(consumer.isResetOffsets()).isTrue();
        assertThat(consumer.isEnableDlq()).isTrue();
        assertThat(consumer.getAutoCommitOnError()).isFalse();
        assertThat(consumer.isAutoRebalanceEnabled()).isFalse();
        assertThat(consumer.getDlqName()).isEqualTo("orders.dlq");
        assertThat(consumer.getDlqPartitions()).isEqualTo(3);
        assertThat(consumer.getTrustedPackages()).containsExactly("com.example");
        assertThat(consumer.getDlqProducerProperties().getCompressionType()).isEqualTo(CompressionType.lz4);
        assertThat(consumer.getStandardHeaders()).isEqualTo(StandardHeaders.timestamp);
        assertThat(consumer.getConverterBeanName()).isEqualTo("converter");
        assertThat(consumer.getIdleEventInterval()).isEqualTo(1_500L);
        assertThat(consumer.isDestinationIsPattern()).isTrue();
        assertThat(consumer.getPollTimeout()).isEqualTo(250L);
        assertThat(consumer.getTransactionManager()).isEqualTo("consumerTxManager");
        assertThat(consumer.isTxCommitRecovered()).isFalse();
        assertThat(consumer.getCommonErrorHandlerBeanName()).isEqualTo("handler");
        assertThat(consumer.isReactiveAutoCommit()).isTrue();
        assertThat(consumer.isReactiveAtMostOnce()).isTrue();
    }

    @Test
    void binderNestedPropertiesExposeTransactionsMetricsJaasAndBindings() {
        KafkaBinderConfigurationProperties binderProperties = binderProperties();
        binderProperties.setAutoCreateTopics(false);
        binderProperties.setAutoAlterTopics(true);
        binderProperties.setAutoAddPartitions(true);
        binderProperties.setConsiderDownWhenAnyPartitionHasNoLeader(false);
        binderProperties.setMinPartitionCount(4);
        binderProperties.setReplicationFactor((short) 2);
        binderProperties.setHealthTimeout(10);
        binderProperties.setHeaders("tenant", "trace");
        binderProperties.setAuthorizationExceptionRetryInterval(Duration.ofSeconds(2));
        binderProperties.setCertificateStoreDirectory("/tmp");
        binderProperties.setEnableObservation(true);
        binderProperties.setHealthIndicatorConsumerGroup("health-group");
        binderProperties.getMetrics().setDefaultOffsetLagMetricsEnabled(false);
        binderProperties.getMetrics().setOffsetLagMetricsInterval(Duration.ofSeconds(15));
        binderProperties.getTransaction().setTransactionIdPrefix("tx-");
        KafkaBinderConfigurationProperties.CombinedProducerProperties combinedProducer =
                binderProperties.getTransaction().getProducer();
        combinedProducer.setPartitionCount(8);
        combinedProducer.setRequiredGroups("analytics", "audit");
        combinedProducer.setHeaderMode(HeaderMode.headers);
        combinedProducer.setUseNativeEncoding(true);
        combinedProducer.setErrorChannelEnabled(true);
        combinedProducer.setPartitionKeyExpression(parser.parseExpression("payload.id"));
        combinedProducer.setPartitionSelectorExpression(parser.parseExpression("headers['partition']"));
        combinedProducer.setBufferSize(2_048);
        combinedProducer.setCompressionType(CompressionType.snappy);
        combinedProducer.setSync(true);
        combinedProducer.setBatchTimeout(21);

        JaasLoginModuleConfiguration jaas = new JaasLoginModuleConfiguration();
        jaas.setLoginModule("com.example.LoginModule");
        jaas.setControlFlag("optional");
        jaas.setOptions(new HashMap<>(Map.of("useKeyTab", "true")));
        binderProperties.setJaas(jaas);

        assertThat(binderProperties.isAutoCreateTopics()).isFalse();
        assertThat(binderProperties.isAutoAlterTopics()).isTrue();
        assertThat(binderProperties.isAutoAddPartitions()).isTrue();
        assertThat(binderProperties.isConsiderDownWhenAnyPartitionHasNoLeader()).isFalse();
        assertThat(binderProperties.getMinPartitionCount()).isEqualTo(4);
        assertThat(binderProperties.getReplicationFactor()).isEqualTo((short) 2);
        assertThat(binderProperties.getHealthTimeout()).isEqualTo(10);
        assertThat(binderProperties.getHeaders()).containsExactly("tenant", "trace");
        assertThat(binderProperties.getAuthorizationExceptionRetryInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(binderProperties.getCertificateStoreDirectory()).isEqualTo("/tmp");
        assertThat(binderProperties.isEnableObservation()).isTrue();
        assertThat(binderProperties.getHealthIndicatorConsumerGroup()).isEqualTo("health-group");
        assertThat(binderProperties.getMetrics().isDefaultOffsetLagMetricsEnabled()).isFalse();
        assertThat(binderProperties.getMetrics().getOffsetLagMetricsInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(binderProperties.getTransaction().getTransactionIdPrefix()).isEqualTo("tx-");
        assertThat(combinedProducer.getPartitionCount()).isEqualTo(8);
        assertThat(combinedProducer.getRequiredGroups()).containsExactly("analytics", "audit");
        assertThat(combinedProducer.getHeaderMode()).isEqualTo(HeaderMode.headers);
        assertThat(combinedProducer.isUseNativeEncoding()).isTrue();
        assertThat(combinedProducer.isErrorChannelEnabled()).isTrue();
        assertThat(combinedProducer.getPartitionKeyExpression().getExpressionString()).isEqualTo("payload.id");
        assertThat(combinedProducer.getPartitionSelectorExpression().getExpressionString())
                .isEqualTo("headers['partition']");
        assertThat(combinedProducer.getBufferSize()).isEqualTo(2_048);
        assertThat(combinedProducer.getCompressionType()).isEqualTo(CompressionType.snappy);
        assertThat(combinedProducer.isSync()).isTrue();
        assertThat(combinedProducer.getBatchTimeout()).isEqualTo(21);
        assertThat(binderProperties.getJaas().getLoginModule()).isEqualTo("com.example.LoginModule");
        assertThat(binderProperties.getJaas().getControlFlag())
                .isEqualTo(KafkaJaasLoginModuleInitializer.ControlFlag.OPTIONAL);
        assertThat(binderProperties.getJaas().getOptions()).containsEntry("useKeyTab", "true");
    }

    @Test
    void extendedBindingPropertiesTopicPropertiesAndTopicUtilitiesWorkTogether() {
        KafkaTopicProperties topic = new KafkaTopicProperties();
        topic.setReplicationFactor((short) 3);
        topic.setReplicasAssignments(new HashMap<>(Map.of(0, List.of(1, 2), 1, List.of(2, 3))));
        topic.setProperties(new HashMap<>(Map.of("cleanup.policy", "compact")));

        KafkaBindingProperties binding = new KafkaBindingProperties();
        KafkaProducerProperties producer = new KafkaProducerProperties();
        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        producer.setTopic(topic);
        consumer.setTopic(topic);
        binding.setProducer(producer);
        binding.setConsumer(consumer);

        KafkaExtendedBindingProperties extended = new KafkaExtendedBindingProperties();
        extended.setBindings(new HashMap<>(Map.of("orders-out-0", binding)));

        assertThat(extended.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.kafka.default");
        assertThat(extended.getBindings()).containsEntry("orders-out-0", binding);
        assertThat(extended.getExtendedPropertiesEntryClass()).isEqualTo(KafkaBindingProperties.class);
        assertThat(extended.getExtendedProducerProperties("orders-out-0").getTopic().getReplicationFactor())
                .isEqualTo((short) 3);
        assertThat(extended.getExtendedConsumerProperties("orders-out-0").getTopic().getProperties())
                .containsEntry("cleanup.policy", "compact");

        KafkaTopicUtils.validateTopicName("orders.valid-topic_1");
        assertThatIllegalArgumentException().isThrownBy(() -> KafkaTopicUtils.validateTopicName("orders invalid"))
                .withMessageContaining("ASCII alphanumerics");

        TopicInformation consumerTopic = new TopicInformation("orders-group", List.of(), false);
        TopicInformation producerTopic = new TopicInformation(null, List.of(), true);
        assertThat(consumerTopic.isConsumerTopic()).isTrue();
        assertThat(producerTopic.isConsumerTopic()).isFalse();
        assertThat(producerTopic.isTopicPattern()).isTrue();
        assertThat(producerTopic.partitionInfos()).isEmpty();
    }

    private static KafkaBinderConfigurationProperties binderProperties() {
        ObjectProvider<KafkaConnectionDetails> noConnectionDetails = new ObjectProvider<>() {
            @Override
            public KafkaConnectionDetails getIfAvailable() {
                return null;
            }
        };
        return new KafkaBinderConfigurationProperties(new KafkaProperties(), noConnectionDetails);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
