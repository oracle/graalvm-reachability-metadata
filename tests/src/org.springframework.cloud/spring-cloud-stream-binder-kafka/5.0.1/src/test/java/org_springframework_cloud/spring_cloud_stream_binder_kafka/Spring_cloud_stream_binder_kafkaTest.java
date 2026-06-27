/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.cloud.function.context.config.MessageConverterHelper;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.KafkaExpressionEvaluatingInterceptor;
import org.springframework.cloud.stream.binder.kafka.KafkaMessageChannelBinder;
import org.springframework.cloud.stream.binder.kafka.KafkaNullConverter;
import org.springframework.cloud.stream.binder.kafka.aot.KafkaBinderRuntimeHints;
import org.springframework.cloud.stream.binder.kafka.common.BinderHeaderMapper;
import org.springframework.cloud.stream.binder.kafka.config.DefaultMessageConverterHelper;
import org.springframework.cloud.stream.binder.kafka.config.ExtendedBindingHandlerMappingsProviderConfiguration;
import org.springframework.cloud.stream.binder.kafka.config.MessageConverterHelperConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.provisioning.KafkaTopicProvisioner;
import org.springframework.cloud.stream.config.BindingHandlerAdvise.MappingsProvider;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

public class Spring_cloud_stream_binder_kafkaTest {

    @Test
    @Timeout(60)
    void embeddedKafkaBinderProcessesFunctionAndProvisionerCreatesTopics() throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        String inputTopic = "scst-input-" + id;
        String outputTopic = "scst-output-" + id;
        String provisionedTopic = "scst-provisioned-" + id;
        String dlqTopic = "scst-dlq-" + id;
        EmbeddedKafkaKraftBroker embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1, inputTopic, outputTopic);
        embeddedKafka.adminTimeout(15);
        embeddedKafka.afterPropertiesSet();
        try {
            KafkaTopicProvisioner provisioner = kafkaTopicProvisioner(embeddedKafka);
            ProducerDestination producerDestination = provisioner.provisionProducerDestination(provisionedTopic,
                    extendedProducerProperties(3));
            KafkaConsumerProperties consumerProperties = new KafkaConsumerProperties();
            consumerProperties.setEnableDlq(true);
            consumerProperties.setDlqName(dlqTopic);
            ExtendedConsumerProperties<KafkaConsumerProperties> extendedConsumerProperties =
                    new ExtendedConsumerProperties<>(consumerProperties);
            extendedConsumerProperties.setInstanceCount(1);
            extendedConsumerProperties.setConcurrency(2);

            ConsumerDestination consumerDestination = provisioner.provisionConsumerDestination(
                    provisionedTopic, "analytics", extendedConsumerProperties);
            Set<String> topics;
            try (AdminClient adminClient = AdminClient.create(provisioner.getAdminClientProperties())) {
                topics = adminClient.listTopics().names().get(15, TimeUnit.SECONDS);
            }

            assertThat(producerDestination.getName()).isEqualTo(provisionedTopic);
            assertThat(producerDestination.getNameForPartition(2)).isEqualTo(provisionedTopic);
            assertThat(consumerDestination.getName()).isEqualTo(provisionedTopic);
            assertThat(topics).contains(provisionedTopic, dlqTopic);

            Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                    embeddedKafka, "result-consumer-" + id, false);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000");
            consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
            DefaultKafkaConsumerFactory<String, String> consumerFactory =
                    new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(),
                            new StringDeserializer());
            Consumer<String, String> resultConsumer = consumerFactory.createConsumer();
            DefaultKafkaProducerFactory<String, String> producerFactory = null;
            KafkaTemplate<String, String> kafkaTemplate = null;
            try {
                embeddedKafka.consumeFromAnEmbeddedTopic(resultConsumer, outputTopic);
                Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
                producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
                producerProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "15000");
                producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
                kafkaTemplate = new KafkaTemplate<>(producerFactory);

                try (ConfigurableApplicationContext context =
                        new SpringApplicationBuilder(StreamFunctionApplication.class)
                                .web(WebApplicationType.NONE)
                                .properties(streamApplicationProperties(embeddedKafka, inputTopic, outputTopic, id))
                                .run()) {
                    kafkaTemplate.send(inputTopic, "order-1", "native-image").get(15, TimeUnit.SECONDS);

                    ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                            resultConsumer, outputTopic, Duration.ofSeconds(15));

                    assertThat(context.containsBean("uppercase")).isTrue();
                    assertThat(record.value()).isEqualTo("NATIVE-IMAGE");
                }
            } finally {
                if (kafkaTemplate != null) {
                    kafkaTemplate.destroy();
                }
                if (producerFactory != null) {
                    producerFactory.destroy();
                }
                resultConsumer.close(Duration.ofSeconds(10));
            }
        } finally {
            embeddedKafka.destroy();
        }
    }

    @Test
    @Timeout(60)
    void expressionConverterHeadersAndBatchFailureHelpersUseKafkaSpecificTypes() {
        SpelExpressionParser parser = new SpelExpressionParser();
        KafkaExpressionEvaluatingInterceptor interceptor = new KafkaExpressionEvaluatingInterceptor(
                parser.parseExpression("payload['tenant'] + '-' + headers['eventType']"),
                new StandardEvaluationContext());
        Message<Map<String, String>> message = MessageBuilder
                .withPayload(Map.of("tenant", "tenant-a"))
                .setHeader("eventType", "created")
                .build();

        Message<?> keyedMessage = interceptor.preSend(message, null);

        assertThat(keyedMessage.getHeaders())
                .containsEntry(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER, "tenant-a-created");
        assertThat(message.getHeaders()).doesNotContainKey(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new KafkaExpressionEvaluatingInterceptor(parser.parseExpression("'key'"), null))
                .withMessageContaining("evaluationContext");

        MessageConverter converter = new KafkaNullConverter();
        Message<KafkaNull> kafkaNullMessage = MessageBuilder.withPayload(KafkaNull.INSTANCE).build();
        assertThat(converter.fromMessage(kafkaNullMessage, KafkaNull.class)).isSameAs(KafkaNull.INSTANCE);
        assertThat(converter.toMessage(KafkaNull.INSTANCE, null).getPayload()).isSameAs(KafkaNull.INSTANCE);

        DefaultMessageConverterHelper helper = new DefaultMessageConverterHelper();
        ArrayList<String> receivedTopics = new ArrayList<>(List.of("first", "second", "third"));
        ArrayList<Integer> receivedPartitions = new ArrayList<>(List.of(0, 1, 2));
        Message<List<String>> batchMessage = MessageBuilder
                .withPayload(List.of("first", "second", "third"))
                .setHeader(KafkaHeaders.RECEIVED_TOPIC, receivedTopics)
                .setHeader(KafkaHeaders.RECEIVED_PARTITION, receivedPartitions)
                .setHeader("applicationHeader", new ArrayList<>(List.of("kept")))
                .build();

        assertThat(helper.shouldFailIfCantConvert(batchMessage)).isFalse();
        helper.postProcessBatchMessageOnFailure(batchMessage, 1);

        assertThat(receivedTopics).containsExactly("first", "third");
        assertThat(receivedPartitions).containsExactly(0, 2);
        assertThat(batchMessage.getHeaders().get("applicationHeader")).isEqualTo(List.of("kept"));
    }

    @Test
    @Timeout(60)
    void binderHeaderMapperRoundTripsTrustedHeadersAndFiltersNeverMappedHeaders() {
        BinderHeaderMapper mapper = new BinderHeaderMapper();
        Map<String, Object> sourceHeaders = new HashMap<>();
        sourceHeaders.put("tenant", "blue");
        sourceHeaders.put("attempt", 2);
        sourceHeaders.put(KafkaHeaders.RECEIVED_TOPIC, "input-topic");
        sourceHeaders.put(BinderHeaders.NATIVE_HEADERS_PRESENT, Boolean.TRUE);
        RecordHeaders kafkaHeaders = new RecordHeaders();

        Message<String> message = MessageBuilder.withPayload("payload").copyHeaders(sourceHeaders).build();
        mapper.fromHeaders(message.getHeaders(), kafkaHeaders);

        assertThat(kafkaHeaders.lastHeader("tenant")).isNotNull();
        assertThat(kafkaHeaders.lastHeader("attempt")).isNotNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaderMapper.JSON_TYPES)).isNotNull();
        assertThat(kafkaHeaders.lastHeader(KafkaHeaders.RECEIVED_TOPIC)).isNull();
        assertThat(kafkaHeaders.lastHeader(BinderHeaders.NATIVE_HEADERS_PRESENT)).isNull();

        Map<String, Object> mappedHeaders = new HashMap<>();
        mapper.toHeaders(kafkaHeaders, mappedHeaders);

        assertThat(mappedHeaders).containsEntry("tenant", "blue");
        assertThat(mappedHeaders).containsEntry("attempt", 2);
        assertThat(new String(kafkaHeaders.lastHeader("tenant").value(), StandardCharsets.UTF_8)).isEqualTo("blue");
    }

    @Test
    @Timeout(60)
    void configurationPropertiesMappingsRuntimeHintsAndBinderAccessorsAreAvailable() throws Exception {
        KafkaBinderConfigurationProperties properties = kafkaBinderProperties();
        properties.setBrokers("broker-a:19092", "broker-b");
        properties.setDefaultBrokerPort("29092");
        properties.setConfiguration(Map.of(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip",
                "schema.registry.url", "http://schema-registry"));
        properties.setConsumerProperties(Map.of(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.GROUP_ID_CONFIG, "ignored-group",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "17"));
        properties.setProducerProperties(Map.of(ProducerConfig.ACKS_CONFIG, "all"));

        Map<String, Object> consumerConfiguration = properties.mergedConsumerConfiguration();
        Map<String, Object> producerConfiguration = properties.mergedProducerConfiguration();

        assertThat(properties.getKafkaConnectionString()).isEqualTo("broker-a:19092,broker-b:29092");
        assertThat(consumerConfiguration)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-a:19092,broker-b:29092")
                .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "17")
                .containsEntry("schema.registry.url", "http://schema-registry")
                .doesNotContainKeys(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, ConsumerConfig.GROUP_ID_CONFIG);
        assertThat(producerConfiguration)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker-a:19092,broker-b:29092")
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip")
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all");

        Map<String, Object> adminProperties = new HashMap<>();
        KafkaTopicProvisioner.normalalizeBootPropsWithBinder(adminProperties, new KafkaProperties(), properties);
        assertThat(adminProperties).containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                "broker-a:19092,broker-b:29092");
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> {
            KafkaBinderConfigurationProperties invalid = kafkaBinderProperties();
            invalid.setConfiguration(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093"));
            KafkaTopicProvisioner.normalalizeBootPropsWithBinder(new HashMap<>(), new KafkaProperties(), invalid);
        }).withMessageContaining("brokers");

        MessageConverterHelper converterHelper =
                new MessageConverterHelperConfiguration().kafkaMessageConverterHelper();
        MappingsProvider mappingsProvider = new ExtendedBindingHandlerMappingsProviderConfiguration()
                .kafkaExtendedPropertiesDefaultMappingsProvider();
        assertThat(converterHelper).isInstanceOf(DefaultMessageConverterHelper.class);
        assertThat(mappingsProvider.getDefaultMappings()).containsEntry(
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.bindings"),
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.default"));

        RuntimeHints hints = new RuntimeHints();
        new KafkaBinderRuntimeHints().registerHints(hints, getClass().getClassLoader());
        assertRegisteredBindingPropertyHint(hints, KafkaConsumerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaProducerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaExtendedBindingProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaBindingProperties.class);

        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(properties, new KafkaProperties(),
                adminClientProperties -> adminClientProperties.put(
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000"));
        KafkaMessageChannelBinder binder = new KafkaMessageChannelBinder(properties, provisioner);
        KafkaConsumerProperties consumer = new KafkaConsumerProperties();
        consumer.setDlqName("errors.orders");
        KafkaProducerProperties producer = new KafkaProducerProperties();
        producer.setSync(true);
        KafkaBindingProperties binding = new KafkaBindingProperties();
        binding.setConsumer(consumer);
        binding.setProducer(producer);
        KafkaExtendedBindingProperties extendedBindingProperties = new KafkaExtendedBindingProperties();
        extendedBindingProperties.setBindings(Map.of("orders-in", binding));
        binder.setExtendedBindingProperties(extendedBindingProperties);

        assertThat(binder.getDefaultsPrefix()).isEqualTo("spring.cloud.stream.kafka.default");
        assertThat(binder.getExtendedPropertiesEntryClass()).isEqualTo(KafkaBindingProperties.class);
        assertThat(binder.getExtendedConsumerProperties("orders-in")).isSameAs(consumer);
        assertThat(binder.getExtendedProducerProperties("orders-in")).isSameAs(producer);
        assertThat(binder.getBinderIdentity()).startsWith("kafka-");

    }

    private static KafkaTopicProvisioner kafkaTopicProvisioner(EmbeddedKafkaBroker embeddedKafka) {
        KafkaBinderConfigurationProperties properties = kafkaBinderProperties();
        properties.setBrokers(embeddedKafka.getBrokersAsString());
        properties.setReplicationFactor((short) 1);
        properties.setMinPartitionCount(2);
        properties.setAutoCreateTopics(true);
        properties.setAutoAddPartitions(true);
        properties.setConfiguration(Map.of(
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000"));
        KafkaTopicProvisioner provisioner = new KafkaTopicProvisioner(properties, new KafkaProperties(),
                (adminClientProperties) -> adminClientProperties.put(
                        AdminClientConfig.RETRIES_CONFIG, "1"));
        provisioner.afterPropertiesSet();
        return provisioner;
    }

    private static ExtendedProducerProperties<KafkaProducerProperties> extendedProducerProperties(int partitions) {
        ExtendedProducerProperties<KafkaProducerProperties> properties =
                new ExtendedProducerProperties<>(new KafkaProducerProperties());
        properties.setPartitionCount(partitions);
        return properties;
    }

    private static Map<String, Object> streamApplicationProperties(EmbeddedKafkaBroker embeddedKafka,
            String inputTopic, String outputTopic, String id) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.jmx.enabled", "false");
        properties.put("spring.main.banner-mode", "off");
        properties.put("spring.main.lazy-initialization", "false");
        properties.put("spring.cloud.function.definition", "uppercase");
        properties.put("spring.cloud.stream.bindings.uppercase-in-0.destination", inputTopic);
        properties.put("spring.cloud.stream.bindings.uppercase-in-0.group", "uppercase-group-" + id);
        properties.put("spring.cloud.stream.bindings.uppercase-in-0.consumer.max-attempts", "1");
        properties.put("spring.cloud.stream.bindings.uppercase-out-0.destination", outputTopic);
        properties.put("spring.cloud.stream.kafka.binder.brokers", embeddedKafka.getBrokersAsString());
        properties.put("spring.cloud.stream.kafka.binder.replication-factor", "1");
        properties.put("spring.cloud.stream.kafka.bindings.uppercase-in-0.consumer.start-offset", "earliest");
        properties.put("spring.kafka.consumer.auto-offset-reset", "earliest");
        properties.put("spring.kafka.consumer.properties.request.timeout.ms", "10000");
        properties.put("spring.kafka.consumer.properties.default.api.timeout.ms", "10000");
        properties.put("spring.kafka.producer.properties.request.timeout.ms", "10000");
        properties.put("spring.kafka.producer.properties.delivery.timeout.ms", "15000");
        properties.put("spring.kafka.admin.properties.request.timeout.ms", "10000");
        properties.put("spring.kafka.admin.properties.default.api.timeout.ms", "10000");
        return properties;
    }

    private static KafkaBinderConfigurationProperties kafkaBinderProperties() {
        return new KafkaBinderConfigurationProperties(new KafkaProperties(), new EmptyKafkaConnectionDetailsProvider());
    }

    private static void assertRegisteredBindingPropertyHint(RuntimeHints hints, Class<?> type) {
        TypeHint typeHint = hints.reflection().getTypeHint(type);
        assertThat(typeHint).as("runtime hint for %s", type.getName()).isNotNull();
        assertThat(typeHint.getMemberCategories()).contains(MemberCategory.INVOKE_DECLARED_METHODS);
    }

    @SpringBootApplication(proxyBeanMethods = false)
    public static class StreamFunctionApplication {

        @Bean
        public Function<String, String> uppercase() {
            return new UppercaseFunction();
        }
    }

    public static class UppercaseFunction implements Function<String, String> {

        @Override
        public String apply(String value) {
            return value.toUpperCase();
        }
    }

    private static final class EmptyKafkaConnectionDetailsProvider implements ObjectProvider<KafkaConnectionDetails> {

        @Override
        public KafkaConnectionDetails getObject() throws BeansException {
            return null;
        }

        @Override
        public KafkaConnectionDetails getIfAvailable() throws BeansException {
            return null;
        }
    }
}
