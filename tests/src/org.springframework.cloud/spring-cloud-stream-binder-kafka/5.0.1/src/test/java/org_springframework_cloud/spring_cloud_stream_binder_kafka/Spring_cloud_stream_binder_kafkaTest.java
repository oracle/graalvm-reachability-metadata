/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.stream.binder.kafka.KafkaExpressionEvaluatingInterceptor;
import org.springframework.cloud.stream.binder.kafka.KafkaNullConverter;
import org.springframework.cloud.stream.binder.kafka.aot.KafkaBinderRuntimeHints;
import org.springframework.cloud.stream.binder.kafka.config.ExtendedBindingHandlerMappingsProviderConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.config.BindingHandlerAdvise.MappingsProvider;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class Spring_cloud_stream_binder_kafkaTest {

    private static final Duration KAFKA_POLL_TIMEOUT = Duration.ofSeconds(10);

    private static final BlockingQueue<byte[]> RECEIVED_MESSAGES = new LinkedBlockingQueue<>();

    private static EmbeddedKafkaBroker embeddedKafka;

    @BeforeAll
    static void startKafka() {
        embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1)
                .adminTimeout(10)
                .brokerListProperty("spring.kafka.bootstrap-servers");
        embeddedKafka.afterPropertiesSet();
    }

    @AfterAll
    static void stopKafka() {
        if (embeddedKafka != null) {
            embeddedKafka.destroy();
        }
    }

    @Test
    void streamBridgeProducesRecordsThroughKafkaBinder() {
        String topic = topicName("streambridge-out");
        embeddedKafka.addTopics(topic);
        try (KafkaConsumer<Integer, byte[]> consumer = byteArrayConsumer(topic);
                ConfigurableApplicationContext context = springApplication(ProducerApplication.class,
                        "spring.cloud.stream.bindings.orders-out-0.destination=" + topic,
                        "spring.cloud.stream.bindings.orders-out-0.producer.useNativeEncoding=true")) {

            boolean sent = context.getBean(StreamBridge.class)
                    .send("orders-out-0", MessageBuilder.withPayload(bytes("created"))
                            .setHeader("tenant", "acme")
                            .build());
            ConsumerRecord<Integer, byte[]> record = KafkaTestUtils.getSingleRecord(
                    consumer, topic, KAFKA_POLL_TIMEOUT);

            assertThat(sent).isTrue();
            assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("created");
        }
    }

    @Test
    void functionConsumerReceivesRecordsThroughKafkaBinder() throws Exception {
        String topic = topicName("function-in");
        String group = topicName("group");
        RECEIVED_MESSAGES.clear();
        embeddedKafka.addTopics(topic);
        try (ConfigurableApplicationContext context = springApplication(ConsumerApplication.class,
                "spring.cloud.function.definition=inputConsumer",
                "spring.cloud.stream.bindings.inputConsumer-in-0.destination=" + topic,
                "spring.cloud.stream.bindings.inputConsumer-in-0.group=" + group,
                "spring.cloud.stream.bindings.inputConsumer-in-0.consumer.maxAttempts=1",
                "spring.cloud.stream.bindings.inputConsumer-in-0.consumer.useNativeDecoding=true");
                KafkaProducer<Integer, byte[]> producer = byteArrayProducer()) {

            producer.send(new ProducerRecord<>(topic, bytes("inbound"))).get(10, TimeUnit.SECONDS);
            byte[] payload = RECEIVED_MESSAGES.poll(10, TimeUnit.SECONDS);

            assertThat(payload).isNotNull();
            assertThat(new String(payload, StandardCharsets.UTF_8)).isEqualTo("inbound");
        }
    }

    @Test
    void failingFunctionConsumerRoutesRecordsToKafkaDeadLetterTopic() throws Exception {
        String topic = topicName("function-dlq-in");
        String group = topicName("dlq-group");
        String dlqTopic = topicName("function-dlq-out");
        embeddedKafka.addTopics(topic, dlqTopic);
        try (ConfigurableApplicationContext context = springApplication(FailingConsumerApplication.class,
                "spring.cloud.function.definition=failingConsumer",
                "spring.cloud.stream.bindings.failingConsumer-in-0.destination=" + topic,
                "spring.cloud.stream.bindings.failingConsumer-in-0.group=" + group,
                "spring.cloud.stream.bindings.failingConsumer-in-0.consumer.maxAttempts=1",
                "spring.cloud.stream.bindings.failingConsumer-in-0.consumer.useNativeDecoding=true",
                "spring.cloud.stream.kafka.bindings.failingConsumer-in-0.consumer.enableDlq=true",
                "spring.cloud.stream.kafka.bindings.failingConsumer-in-0.consumer.dlqName=" + dlqTopic,
                "spring.cloud.stream.kafka.bindings.failingConsumer-in-0.consumer.dlqProducerProperties.configuration"
                        + "[key.serializer]=org.apache.kafka.common.serialization.IntegerSerializer",
                "spring.cloud.stream.kafka.bindings.failingConsumer-in-0.consumer.dlqProducerProperties.configuration"
                        + "[value.serializer]=org.apache.kafka.common.serialization.ByteArraySerializer");
                KafkaProducer<Integer, byte[]> producer = byteArrayProducer();
                KafkaConsumer<Integer, byte[]> dlqConsumer = byteArrayConsumer(dlqTopic)) {

            producer.send(new ProducerRecord<>(topic, bytes("poison"))).get(10, TimeUnit.SECONDS);
            ConsumerRecord<Integer, byte[]> deadLetterRecord = KafkaTestUtils.getSingleRecord(
                    dlqConsumer, dlqTopic, KAFKA_POLL_TIMEOUT);

            assertThat(new String(deadLetterRecord.value(), StandardCharsets.UTF_8)).isEqualTo("poison");
        }
    }

    @Test
    void kafkaNullConverterRoundTripsTombstonePayload() {
        KafkaNullConverter converter = new KafkaNullConverter();
        Message<KafkaNull> message = MessageBuilder.withPayload(KafkaNull.INSTANCE)
                .setHeader("source", "delete-event")
                .build();

        Object convertedFromMessage = converter.fromMessage(message, KafkaNull.class);
        Message<?> convertedToMessage = converter.toMessage(KafkaNull.INSTANCE, message.getHeaders());

        assertThat(convertedFromMessage).isSameAs(KafkaNull.INSTANCE);
        assertThat(convertedToMessage.getPayload()).isSameAs(KafkaNull.INSTANCE);
        assertThat(convertedToMessage.getHeaders()).containsEntry("source", "delete-event");
    }

    @Test
    void expressionInterceptorAddsEvaluatedKafkaMessageKeyHeader() {
        KafkaExpressionEvaluatingInterceptor interceptor = new KafkaExpressionEvaluatingInterceptor(
                new SpelExpressionParser().parseExpression("payload['tenant'] + ':' + headers['eventType']"),
                new StandardEvaluationContext());
        Message<Map<String, String>> message = MessageBuilder.withPayload(Map.of("tenant", "acme"))
                .setHeader("eventType", "order.created")
                .build();

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result.getPayload()).isSameAs(message.getPayload());
        assertThat(result.getHeaders())
                .containsEntry(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER, "acme:order.created");
        assertThat(message.getHeaders()).doesNotContainKey(KafkaExpressionEvaluatingInterceptor.MESSAGE_KEY_HEADER);
    }

    @Test
    void configurationObjectsExposeKafkaExtendedBindingMappingsAndRuntimeHints() {
        MappingsProvider mappingsProvider = new ExtendedBindingHandlerMappingsProviderConfiguration()
                .kafkaExtendedPropertiesDefaultMappingsProvider();
        RuntimeHints hints = new RuntimeHints();

        new KafkaBinderRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(mappingsProvider.getDefaultMappings()).containsEntry(
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.bindings"),
                ConfigurationPropertyName.of("spring.cloud.stream.kafka.default"));
        assertRegisteredBindingPropertyHint(hints, KafkaConsumerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaProducerProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaExtendedBindingProperties.class);
        assertRegisteredBindingPropertyHint(hints, KafkaBindingProperties.class);
    }

    private static ConfigurableApplicationContext springApplication(Class<?> source, String... properties) {
        return new SpringApplicationBuilder(source)
                .web(WebApplicationType.NONE)
                .properties(commonProperties())
                .properties(properties)
                .run();
    }

    private static Map<String, Object> commonProperties() {
        String brokers = embeddedKafka.getBrokersAsString();
        return Map.ofEntries(
                Map.entry("spring.main.banner-mode", "off"),
                Map.entry("spring.jmx.enabled", "false"),
                Map.entry("spring.kafka.bootstrap-servers", brokers),
                Map.entry("spring.cloud.stream.defaultBinder", "kafka"),
                Map.entry("spring.cloud.stream.kafka.binder.brokers", brokers),
                Map.entry("spring.cloud.stream.kafka.binder.healthTimeout", "10"),
                Map.entry("spring.cloud.stream.kafka.binder.autoCreateTopics", "true"),
                Map.entry("management.health.binders.enabled", "true"),
                Map.entry("logging.level.root", "ERROR"));
    }

    private static KafkaConsumer<Integer, byte[]> byteArrayConsumer(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(embeddedKafka, topicName("consumer"), false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        KafkaConsumer<Integer, byte[]> consumer = new KafkaConsumer<>(props);
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private static KafkaProducer<Integer, byte[]> byteArrayProducer() {
        Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafka);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaProducer<>(props);
    }

    private static byte[] bytes(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static String topicName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void assertRegisteredBindingPropertyHint(RuntimeHints hints, Class<?> type) {
        TypeHint typeHint = hints.reflection().getTypeHint(type);
        assertThat(typeHint).as("runtime hint for %s", type.getName()).isNotNull();
        assertThat(typeHint.getMemberCategories()).contains(MemberCategory.INVOKE_DECLARED_METHODS);
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class ProducerApplication {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class ConsumerApplication {

        @Bean
        public Consumer<Message<byte[]>> inputConsumer() {
            return message -> RECEIVED_MESSAGES.add(message.getPayload());
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static class FailingConsumerApplication {

        @Bean
        public Consumer<Message<byte[]>> failingConsumer() {
            return message -> {
                throw new IllegalStateException("cannot process test record");
            };
        }
    }
}
