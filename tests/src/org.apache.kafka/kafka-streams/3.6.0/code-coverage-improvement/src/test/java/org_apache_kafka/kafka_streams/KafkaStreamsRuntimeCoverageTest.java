/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.zk.EmbeddedZookeeper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.TimestampedWindowStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaStreamsRuntimeCoverageTest {

    private static final String KAFKA_SERVER = "localhost:9093";

    private EmbeddedZookeeper zookeeper;

    private KafkaServer kafkaServer;

    @BeforeAll
    void startBroker() {
        zookeeper = new EmbeddedZookeeper();
        Properties brokerProperties = new Properties();
        brokerProperties.setProperty("zookeeper.connect", "localhost:" + zookeeper.port());
        brokerProperties.setProperty("log.dirs", TestUtils.tempDir().getPath());
        brokerProperties.setProperty("listeners", "PLAINTEXT://" + KAFKA_SERVER);
        brokerProperties.setProperty("offsets.topic.replication.factor", "1");
        kafkaServer = TestUtils.createServer(new KafkaConfig(brokerProperties), Time.SYSTEM);
    }

    @AfterAll
    void stopBroker() {
        if (kafkaServer != null) {
            kafkaServer.shutdown();
        }
        if (zookeeper != null) {
            zookeeper.shutdown();
        }
    }

    @Test
    void processesJoinsAggregatesWindowsAndStateStoresThroughKafkaStreams() throws Exception {
        createTopics("orders", "payments", "discounts", "order-count-output", "joined-output", "table-joined-output",
                "window-output", "transformed-output", "selected-count-output", "self-join-output");
        StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(Stores.timestampedKeyValueStoreBuilder(
                Stores.persistentTimestampedKeyValueStore("timestamped-key-values"), Serdes.String(), Serdes.String()));
        builder.addStateStore(Stores.timestampedWindowStoreBuilder(
                Stores.persistentTimestampedWindowStore("timestamped-windows", Duration.ofHours(1),
                        Duration.ofMinutes(1), false), Serdes.String(), Serdes.String()));
        KStream<String, String> orders = builder.stream("orders", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> payments = builder.stream("payments", Consumed.with(Serdes.String(), Serdes.String()));

        orders.process(() -> new TimestampedKeyValueWriter(), "timestamped-key-values");
        orders.process(() -> new TimestampedWindowWriter(), "timestamped-windows");
        orders.filter((key, value) -> value.startsWith("paid"))
                .mapValues(value -> value + "-normalized")
                .flatMapValues(value -> List.of(value, value + "-audit"))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("order-counts"))
                .toStream()
                .to("order-count-output", Produced.with(Serdes.String(), Serdes.Long()));
        orders.leftJoin(payments, (order, payment) -> order + ":" + payment,
                        org.apache.kafka.streams.kstream.JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)))
                .to("joined-output", Produced.with(Serdes.String(), Serdes.String()));
        KTable<String, String> discounts = builder.table("discounts", Consumed.with(Serdes.String(), Serdes.String()));
        orders.leftJoin(discounts, (order, discount) -> order + ":" + discount)
                .to("table-joined-output", Produced.with(Serdes.String(), Serdes.String()));
        orders.selectKey((key, value) -> value.substring(0, 1))
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("selected-counts"))
                .toStream()
                .to("selected-count-output", Produced.with(Serdes.String(), Serdes.Long()));
        discounts.groupBy((key, value) -> org.apache.kafka.streams.KeyValue.pair(value.substring(0, 1), value))
                .reduce((left, right) -> left + right, (left, right) -> right,
                        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("discount-groups"));
        orders.join(orders, (left, right) -> left + ":" + right,
                        org.apache.kafka.streams.kstream.JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)))
                .to("self-join-output", Produced.with(Serdes.String(), Serdes.String()));
        orders.map((key, value) -> org.apache.kafka.streams.KeyValue.pair(key + "-mapped", value))
                .flatMap((key, value) -> List.of(org.apache.kafka.streams.KeyValue.pair(key, value),
                        org.apache.kafka.streams.KeyValue.pair(key + "-copy", value + "-copy")))
                .split()
                .branch((key, value) -> key.endsWith("-mapped"))
                .defaultBranch()
                .values()
                .forEach(stream -> stream.to("transformed-output", Produced.with(Serdes.String(), Serdes.String())));
        orders.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("window-counts").withCachingEnabled())
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
                .toStream()
                .to("window-output");
        orders.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("queryable-window-counts")
                        .withCachingEnabled());

        Properties properties = streamProperties();
        try (KafkaStreams streams = new KafkaStreams(builder.build(), properties);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties());
             KafkaConsumer<String, Long> counts = new KafkaConsumer<>(countConsumerProperties());
             KafkaConsumer<String, String> transforms = new KafkaConsumer<>(stringConsumerProperties("transforms"));
             KafkaConsumer<String, String> joins = new KafkaConsumer<>(stringConsumerProperties());
             KafkaConsumer<String, String> tableJoins = new KafkaConsumer<>(stringConsumerProperties("table-joins"))) {
            streams.start();
            assertThat(streams.addStreamThread()).isPresent();
            counts.subscribe(List.of("order-count-output"));
            transforms.subscribe(List.of("transformed-output"));
            joins.subscribe(List.of("joined-output"));
            tableJoins.subscribe(List.of("table-joined-output"));

            producer.send(new ProducerRecord<>("payments", "customer-1", "card")).get();
            producer.send(new ProducerRecord<>("discounts", "customer-1", "gold")).get();
            long recordTime = System.currentTimeMillis();
            producer.send(new ProducerRecord<>("orders", null, recordTime, "customer-1", "paid-order")).get();
            producer.send(new ProducerRecord<>("orders", null, recordTime + 1L, "customer-1", "paid-order")).get();
            producer.send(new ProducerRecord<>("orders", null, recordTime + Duration.ofSeconds(30).toMillis(),
                    "customer-2", "ignored-order")).get();

            assertThat(readValues(counts, 2)).contains(4L);
            assertThat(readValues(transforms, 4)).contains("paid-order", "paid-order-copy");
            assertThat(readValues(joins, 2)).contains("paid-order:card");
            assertThat(readValues(tableJoins, 2)).contains("paid-order:gold");
            assertThat(streams.allMetadata()).isNotNull();
            assertThat(streams.allMetadataForStore("order-counts")).isNotNull();
            assertThat(streams.streamsMetadataForStore("order-counts")).isNotNull();
            assertThat(streams.queryMetadataForKey("order-counts", "customer-1", Serdes.String().serializer())).isNotNull();
            assertThat(streams.queryMetadataForKey("order-counts", "customer-1",
                    (topic, key, value, partitions) -> 0)).isNotNull();

            ReadOnlyKeyValueStore<String, Long> stateStore = streams.store(StoreQueryParameters
                    .fromNameAndType("order-counts", QueryableStoreTypes.keyValueStore()));
            assertThat(stateStore.get("customer-1")).isEqualTo(4L);
            try (KeyValueIterator<String, Long> entries = stateStore.all()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key).isEqualTo("customer-1");
            }
            try (KeyValueIterator<String, Long> entries = stateStore.range("customer-0", "customer-z")) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.peekNextKey()).isEqualTo("customer-1");
                assertThat(entries.next().value).isEqualTo(4L);
            }
            try (KeyValueIterator<String, Long> entries = stateStore.reverseRange("customer-0", "customer-z")) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.peekNextKey()).isEqualTo("customer-1");
                assertThat(entries.next().value).isEqualTo(4L);
            }
            try (KeyValueIterator<String, Long> entries = stateStore.reverseAll()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key).isEqualTo("customer-1");
            }
            ReadOnlyKeyValueStore<String, Long> selectedStore = streams.store(StoreQueryParameters
                    .fromNameAndType("selected-counts", QueryableStoreTypes.keyValueStore()));
            assertThat(selectedStore.get("p")).isEqualTo(2L);
            try (KeyValueIterator<String, Long> entries = selectedStore.all()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.peekNextKey()).isEqualTo("i");
            }
            ReadOnlyKeyValueStore<String, String> discountGroups = streams.store(StoreQueryParameters
                    .fromNameAndType("discount-groups", QueryableStoreTypes.keyValueStore()));
            assertThat(discountGroups.get("g")).isEqualTo("gold");
            ReadOnlyKeyValueStore<String, ValueAndTimestamp<String>> timestampedValues = streams.store(StoreQueryParameters
                    .fromNameAndType("timestamped-key-values", QueryableStoreTypes.timestampedKeyValueStore()));
            assertThat(timestampedValues.get("customer-1").value()).isEqualTo("paid-order");
            try (KeyValueIterator<String, ValueAndTimestamp<String>> entries = timestampedValues.reverseRange("customer-0",
                    "customer-z")) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key).isEqualTo("customer-2");
            }
            ReadOnlyWindowStore<String, ValueAndTimestamp<String>> timestampedWindows = streams.store(StoreQueryParameters
                    .fromNameAndType("timestamped-windows", QueryableStoreTypes.timestampedWindowStore()));
            try (WindowStoreIterator<ValueAndTimestamp<String>> entries = timestampedWindows.backwardFetch("customer-1",
                    Instant.ofEpochMilli(recordTime - 1_000L), Instant.ofEpochMilli(recordTime + 1_000L))) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().value.value()).isEqualTo("paid-order");
            }
            try (KeyValueIterator<Windowed<String>, ValueAndTimestamp<String>> entries = timestampedWindows.all()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key.key()).isEqualTo("customer-1");
            }
            try (KeyValueIterator<Windowed<String>, ValueAndTimestamp<String>> entries = timestampedWindows.backwardAll()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key.key()).isEqualTo("customer-2");
            }
            try (KeyValueIterator<Windowed<String>, ValueAndTimestamp<String>> entries = timestampedWindows.fetchAll(
                    Instant.ofEpochMilli(recordTime - 1_000L), Instant.ofEpochMilli(recordTime + 1_000L))) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().key.key()).isEqualTo("customer-1");
            }
            ReadOnlyWindowStore<String, Long> windowCounts = streams.store(StoreQueryParameters
                    .fromNameAndType("queryable-window-counts", QueryableStoreTypes.windowStore()));
            try (WindowStoreIterator<Long> entries = windowCounts.fetch("customer-1", Instant.ofEpochMilli(recordTime - 1_000L),
                    Instant.ofEpochMilli(recordTime + 1_000L))) {
                assertThat(entries).isNotNull();
                entries.hasNext();
            }
            try (WindowStoreIterator<Long> entries = windowCounts.backwardFetch("customer-1",
                    Instant.ofEpochMilli(recordTime - 1_000L), Instant.ofEpochMilli(recordTime + 1_000L))) {
                assertThat(entries).isNotNull();
                entries.hasNext();
            }
            try (KeyValueIterator<Windowed<String>, Long> entries = windowCounts.fetchAll(
                    Instant.ofEpochMilli(recordTime - 1_000L), Instant.ofEpochMilli(recordTime + 1_000L))) {
                assertThat(entries).isNotNull();
                entries.hasNext();
            }
            try (KeyValueIterator<Windowed<String>, Long> entries = windowCounts.all()) {
                assertThat(entries).isNotNull();
                entries.hasNext();
            }
            try (KeyValueIterator<Windowed<String>, Long> entries = windowCounts.backwardAll()) {
                assertThat(entries).isNotNull();
                entries.hasNext();
            }

        }
    }

    private static final class TimestampedKeyValueWriter extends ContextualProcessor<String, String, Void, Void> {
        @Override
        public void process(Record<String, String> record) {
            TimestampedKeyValueStore<String, String> store = context().getStateStore("timestamped-key-values");
            store.put(record.key(), ValueAndTimestamp.make(record.value(), record.timestamp()));
        }
    }

    private static final class TimestampedWindowWriter extends ContextualProcessor<String, String, Void, Void> {
        @Override
        public void process(Record<String, String> record) {
            TimestampedWindowStore<String, String> store = context().getStateStore("timestamped-windows");
            store.put(record.key(), ValueAndTimestamp.make(record.value(), record.timestamp()), record.timestamp());
        }
    }

    private void createTopics(String... topicNames) throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        try (Admin admin = Admin.create(properties)) {
            admin.createTopics(java.util.Arrays.stream(topicNames)
                    .map(topic -> new NewTopic(topic, 1, (short) 1)).toList()).all().get();
        }
    }

    private <T> List<T> readValues(KafkaConsumer<String, T> consumer, int expected) {
        java.util.ArrayList<T> values = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (values.size() < expected && System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, T> record : consumer.poll(Duration.ofMillis(100))) {
                values.add(record.value());
            }
        }
        return values;
    }

    private Properties streamProperties() {
        Properties properties = new Properties();
        String runId = UUID.randomUUID().toString();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "runtime-coverage-" + runId);
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        properties.put(StreamsConfig.STATE_DIR_CONFIG, System.getProperty("java.io.tmpdir") + "/runtime-coverage-" + runId);
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 100);
        return properties;
    }

    private Map<String, Object> producerProperties() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    }

    private Map<String, Object> countConsumerProperties() {
        return consumerProperties(LongDeserializer.class);
    }

    private Map<String, Object> stringConsumerProperties() {
        return stringConsumerProperties("joins");
    }

    private Map<String, Object> stringConsumerProperties(String groupSuffix) {
        return consumerProperties(StringDeserializer.class, groupSuffix);
    }

    private Map<String, Object> consumerProperties(Class<?> valueDeserializer) {
        return consumerProperties(valueDeserializer, valueDeserializer.getSimpleName());
    }

    private Map<String, Object> consumerProperties(Class<?> valueDeserializer, String groupSuffix) {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER,
                ConsumerConfig.GROUP_ID_CONFIG, "runtime-coverage-" + groupSuffix,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer);
    }
}
