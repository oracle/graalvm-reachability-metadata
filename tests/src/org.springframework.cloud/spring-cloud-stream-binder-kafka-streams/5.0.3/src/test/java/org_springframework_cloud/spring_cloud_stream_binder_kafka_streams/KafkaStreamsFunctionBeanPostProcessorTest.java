/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = KafkaStreamsFunctionBeanPostProcessorTest.Application.class,
        properties = {
                "spring.cloud.function.definition=inheritedStreamProcessor",
                "spring.cloud.stream.kafka.streams.binder.applicationId=function-bean-post-processor",
                "spring.cloud.stream.kafka.streams.binder.brokers=${spring.embedded.kafka.brokers}",
                "spring.cloud.stream.bindings.inheritedStreamProcessor-in-0.destination=function-processor-input",
                "spring.cloud.stream.bindings.inheritedStreamProcessor-out-0.destination=function-processor-output",
                "spring.cloud.stream.kafka.streams.bindings.inheritedStreamProcessor-in-0.consumer.key-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.inheritedStreamProcessor-in-0.consumer.value-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.inheritedStreamProcessor-out-0.producer.key-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.inheritedStreamProcessor-out-0.producer.value-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde"
        })
@EmbeddedKafka(partitions = 1, topics = {
        KafkaStreamsFunctionBeanPostProcessorTest.INPUT_TOPIC,
        KafkaStreamsFunctionBeanPostProcessorTest.OUTPUT_TOPIC
}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KafkaStreamsFunctionBeanPostProcessorTest {
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESULT_TIMEOUT = Duration.ofSeconds(30);
    static final String INPUT_TOPIC = "function-processor-input";
    static final String OUTPUT_TOPIC = "function-processor-output";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void inheritedComponentFunctionProcessesStreamRecords() throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties());
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.assign(List.of(new TopicPartition(OUTPUT_TOPIC, 0)));
            send(producer, INPUT_TOPIC, "message-42", "native image");

            List<ConsumerRecord<String, String>> records = awaitRecords(consumer);
            boolean transformedRecordFound = false;
            for (ConsumerRecord<String, String> record : records) {
                if ("message-42".equals(record.key()) && "NATIVE IMAGE".equals(record.value())) {
                    transformedRecordFound = true;
                    break;
                }
            }
            assertThat(transformedRecordFound).isTrue();
        }
    }

    private List<ConsumerRecord<String, String>> awaitRecords(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + RESULT_TIMEOUT.toNanos();
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> polled = consumer.poll(CLIENT_TIMEOUT);
            for (ConsumerRecord<String, String> record : polled) {
                records.add(record);
            }
            if (!records.isEmpty()) {
                return records;
            }
        }
        return records;
    }

    private Map<String, Object> producerProperties() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) CLIENT_TIMEOUT.toMillis(),
                ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) CLIENT_TIMEOUT.toMillis());
    }

    private Map<String, Object> consumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.embeddedKafkaBroker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "function-bean-post-processor-consumer",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) CLIENT_TIMEOUT.toMillis());
    }

    private static void send(KafkaProducer<String, String> producer, String topic, String key, String value)
            throws Exception {
        producer.send(new ProducerRecord<>(topic, key, value)).get(CLIENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = InheritedStreamProcessor.class, useDefaultFilters = false,
            includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = InheritedStreamProcessor.class))
    public static class Application {
    }

    @Component("inheritedStreamProcessor")
    public static class InheritedStreamProcessor extends AbstractStreamProcessor {
    }

    public abstract static class AbstractStreamProcessor
            implements Function<KStream<String, String>, KStream<String, String>> {
        @Override
        public KStream<String, String> apply(KStream<String, String> input) {
            return input.mapValues(new UppercaseValueMapper());
        }
    }

    private static final class UppercaseValueMapper implements ValueMapper<String, String> {
        @Override
        public String apply(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }
}
