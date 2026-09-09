/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_stream_binder_kafka_streams;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = KTableBoundElementFactoryInnerKTableWrapperHandlerTest.Application.class,
        properties = {
                "spring.cloud.function.definition=enrich",
                "spring.cloud.stream.kafka.streams.binder.applicationId=k-table-wrapper-handler",
                "spring.cloud.stream.kafka.streams.binder.brokers=${spring.embedded.kafka.brokers}",
                "spring.cloud.stream.bindings.enrich-in-0.destination=k-table-wrapper-stream-input",
                "spring.cloud.stream.bindings.enrich-in-1.destination=k-table-wrapper-table-input",
                "spring.cloud.stream.bindings.enrich-out-0.destination=k-table-wrapper-output",
                "spring.cloud.stream.kafka.streams.bindings.enrich-in-0.consumer.key-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.enrich-in-0.consumer.value-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.enrich-in-1.consumer.key-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.enrich-in-1.consumer.value-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.enrich-in-1.consumer.materialized-as="
                        + "k-table-wrapper-store",
                "spring.cloud.stream.kafka.streams.bindings.enrich-out-0.producer.key-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde",
                "spring.cloud.stream.kafka.streams.bindings.enrich-out-0.producer.value-serde="
                        + "org.apache.kafka.common.serialization.Serdes$StringSerde"
        })
@EmbeddedKafka(partitions = 1, topics = {
        KTableBoundElementFactoryInnerKTableWrapperHandlerTest.STREAM_INPUT_TOPIC,
        KTableBoundElementFactoryInnerKTableWrapperHandlerTest.TABLE_INPUT_TOPIC,
        KTableBoundElementFactoryInnerKTableWrapperHandlerTest.OUTPUT_TOPIC
}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class KTableBoundElementFactoryInnerKTableWrapperHandlerTest {
    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(10);
    static final String STREAM_INPUT_TOPIC = "k-table-wrapper-stream-input";
    static final String TABLE_INPUT_TOPIC = "k-table-wrapper-table-input";
    static final String OUTPUT_TOPIC = "k-table-wrapper-output";
    private static final String STORE_NAME = "k-table-wrapper-store";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void kTableBindingForwardsCallsAndEnrichesStreamRecords() throws Exception {
        KTable<?, ?> bindingTarget = this.applicationContext.getBean("enrich-in-1", KTable.class);
        assertThat(bindingTarget.queryableStoreName()).isEqualTo(STORE_NAME);

        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties());
        try {
            consumer.assign(List.of(new TopicPartition(OUTPUT_TOPIC, 0)));
            send(producer, TABLE_INPUT_TOPIC, "order-42", "priority");
            producer.send(new ProducerRecord<>(STREAM_INPUT_TOPIC, "order-42", "created"));

            ConsumerRecords<String, String> records = consumer.poll(CLIENT_TIMEOUT);
            assertThat(records.records(OUTPUT_TOPIC)).anySatisfy(record -> {
                assertThat(record.key()).isEqualTo("order-42");
                assertThat(record.value()).isEqualTo("created:priority");
            });
        } finally {
            consumer.close(CLIENT_TIMEOUT);
            producer.close(CLIENT_TIMEOUT);
        }
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
                ConsumerConfig.GROUP_ID_CONFIG, "k-table-wrapper-handler-consumer",
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
    public static class Application {
        @Bean
        public BiFunction<KStream<String, String>, KTable<String, String>, KStream<String, String>> enrich() {
            return new TableEnrichment();
        }
    }

    private static final class TableEnrichment
            implements BiFunction<KStream<String, String>, KTable<String, String>, KStream<String, String>> {

        @Override
        public KStream<String, String> apply(KStream<String, String> stream, KTable<String, String> table) {
            return stream.leftJoin(table, new OrderEnricher());
        }
    }

    private static final class OrderEnricher implements ValueJoiner<String, String, String> {
        @Override
        public String apply(String value, String tableValue) {
            return value + ":" + tableValue;
        }
    }
}
