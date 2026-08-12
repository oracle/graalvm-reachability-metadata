/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.CogroupedKStream;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.FailOnInvalidTimestamp;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier;
import org.apache.kafka.streams.processor.internals.PendingUpdateAction;
import org.apache.kafka.streams.query.PositionBound;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.internals.Maybe;
import org.apache.kafka.streams.state.internals.Murmur3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicApiCoverageTest {

    @Test
    void shouldExecuteStreamTransformationsJoinsAndBranches() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream(
                "input", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream(
                "other", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table(
                "table", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table2 = builder.table(
                "table2", Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> transformed = input
                .filterNot((key, value) -> "skip".equals(value))
                .map((key, value) -> KeyValue.pair(key, value + "!"))
                .flatMap((key, value) -> java.util.List.of(KeyValue.pair(key, value)))
                .mapValues(value -> value + "v")
                .flatMapValues(value -> java.util.List.of(value, value + "2"))
                .peek((key, value) -> assertThat(value).isNotEmpty());
        transformed.groupByKey(Grouped.with(Serdes.String(), Serdes.String())).count();
        transformed.groupBy((key, value) -> value,
                Grouped.<String, String>with(Serdes.String(), Serdes.String())).count();
        transformed.join(other, (left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5)))
                .to("joined", Produced.with(Serdes.String(), Serdes.String()));
        transformed.leftJoin(table, (left, right) -> left + right).to("left-table");
        transformed.join(table, (left, right) -> left + right).to("joined-table");
        transformed.outerJoin(other, (left, right) -> String.valueOf(left) + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).to("outer");
        transformed.leftJoin(other, (left, right) -> String.valueOf(left) + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).to("left-stream");
        transformed.split().branch((key, value) -> value.startsWith("a")).defaultBranch()
                .forEach((name, stream) -> stream.to(name));
        table.filter((key, value) -> value != null, Materialized.as("filtered-store"))
                .mapValues(value -> value + "mapped", Materialized.as("mapped-store"))
                .toStream().to("table-output");
        table.join(table2, (left, right) -> left + right).toStream().to("table-join");
        table.leftJoin(table2, (left, right) -> String.valueOf(left) + right)
                .toStream().to("table-left-join");
        table.outerJoin(table2, (left, right) -> String.valueOf(left) + right)
                .toStream().to("table-outer-join");

        Topology topology = builder.build();
        assertThat(topology.describe().toString())
                .contains("joined")
                .contains("table-output")
                .contains("joined-table")
                .contains("table-outer-join")
                .contains("filtered-store")
                .contains("mapped-store");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-transformations");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        try (TopologyTestDriver driver = new TopologyTestDriver(topology, properties)) {
            TestInputTopic<String, String> otherInput = driver.createInputTopic(
                    "other", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> tableInput = driver.createInputTopic(
                    "table", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> table2Input = driver.createInputTopic(
                    "table2", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> inputTopic = driver.createInputTopic(
                    "input", new StringSerializer(), new StringSerializer());
            otherInput.pipeInput("key", "other", 1L);
            tableInput.pipeInput("key", "table", 1L);
            table2Input.pipeInput("key", "table2", 1L);
            inputTopic.pipeInput("key", "a", 2L);
            inputTopic.pipeInput("key", "skip", 3L);
            assertThat(driver.createOutputTopic("joined", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("a!vother");
            assertThat(driver.createOutputTopic("joined-table", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("a!vtable");
        }
    }

    @Test
    void shouldBuildWindowedAggregationsAndPublicConfigurationObjects() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream(
                "events", Consumed.with(Serdes.String(), Serdes.String()));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("time-count"));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .reduce((left, right) -> left + right)
                .toStream().to("time-reduce");
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value)
                .toStream().to("time-aggregate");
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)))
                .reduce((left, right) -> left + right, Materialized.as("sliding-reduce"));
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("sliding-count"));
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(10)))
                .reduce((left, right) -> left + right)
                .toStream().to("session-reduce");
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(10)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        (key, left, right) -> left + right)
                .toStream().to("session-aggregate");
        stream.print(Printed.<String, String>toSysOut().withName("event-printer"));
        Topology topology = builder.build();
        assertThat(topology.describe().toString())
                .contains("event-printer")
                .contains("time-count")
                .contains("session-aggregate");

        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class)).isNotNull();
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class, 10L)).isNotNull();
        assertThat(Stores.persistentVersionedKeyValueStore("versions", Duration.ofMinutes(1)).name()).isEqualTo("versions");
    }

    @Test
    void shouldBuildNamedAndWindowedTopologyVariants() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> source = builder.stream(Pattern.compile("events-.*"));
        KStream<String, String> right = builder.stream("right");
        KTable<String, String> table = builder.table("table");

        source.repartition(Repartitioned.as("repartitioned"))
                .through("through")
                .merge(right)
                .groupByKey()
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(2)))
                .count(Materialized.as("session-count"));
        source.join(table, (left, value) -> left + value, Joined.as("table-join"))
                .toTable(Named.as("as-table"), Materialized.as("result-table"));
        CogroupedKStream<String, String> cogrouped = source.groupByKey()
                .cogroup((key, value, aggregate) -> aggregate + value);
        cogrouped.aggregate(() -> "").toStream().to("cogrouped");
        cogrouped.windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(2)))
                .aggregate(() -> "", (key, left, aggregateValue) -> left + aggregateValue)
                .toStream().to("session-cogrouped");
        cogrouped.windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)))
                .aggregate(() -> "").toStream().to("sliding-cogrouped");
        cogrouped.windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
                .aggregate(() -> "").toStream().to("time-cogrouped");
        table.groupBy((key, value) -> KeyValue.pair(key, value))
                .reduce((left, rightValue) -> rightValue, (left, rightValue) -> left,
                        Named.as("table-reduce"), Materialized.as("reduced-table"));
        table.suppress(Suppressed.untilTimeLimit(Duration.ofSeconds(1),
                Suppressed.BufferConfig.unbounded()).withName("suppressed"));
        builder.globalTable("global-table");

        assertThat(builder.build().describe().toString())
                .contains("repartitioned")
                .contains("session-count")
                .contains("cogrouped")
                .contains("suppressed");
    }

    @Test
    void shouldPreservePublicConfigurationNamesAndSerdeOptions() {
        assertThat(Named.as("named")).isNotNull();
        assertThat(Branched.as("branch").withName("renamed-branch")).isNotNull();
        assertThat(Consumed.as("consumed").withName("renamed-consumed")).isNotNull();
        assertThat(Grouped.as("grouped").withName("renamed-grouped")).isNotNull();
        assertThat(Joined.as("joined").withName("renamed-joined")).isNotNull();
        assertThat(Produced.as("produced").withName("renamed-produced")).isNotNull();
        assertThat(Repartitioned.as("repartitioned").withName("renamed-repartitioned")).isNotNull();
        assertThat(TableJoined.as("table-joined").withName("renamed-table-joined")).isNotNull();
        assertThat(UnlimitedWindows.of()).isNotNull();
        assertThat(Suppressed.BufferConfig.unbounded().withMaxBytes(1024).withMaxRecords(10)
                .withLoggingEnabled(Map.of("cleanup.policy", "compact"))).isNotNull();
        assertThat(Suppressed.BufferConfig.unbounded().withLoggingDisabled()).isNotNull();
        assertThat(new WindowedSerdes.TimeWindowedSerde<String>().forChangelog(true)).isNotNull();
        assertThat(new WindowedSerdes.SessionWindowedSerde<String>()).isNotNull();
    }

    @Test
    void shouldExercisePublicConfigurationAndUtilityContracts() {
        assertThat(QueryableStoreTypes.keyValueStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedKeyValueStore()).isNotNull();
        assertThat(QueryableStoreTypes.windowStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedWindowStore()).isNotNull();
        assertThat(QueryableStoreTypes.sessionStore()).isNotNull();

        Maybe<String> value = Maybe.defined("value");
        assertThat(value.hashCode()).isEqualTo(Maybe.defined("value").hashCode());
        assertThat(Murmur3.hash32(new byte[] {1, 2, 3})).isEqualTo(Murmur3.hash32(new byte[] {1, 2, 3}));
        assertThat(Murmur3.hash32(42L)).isNotEqualTo(0);
        assertThat(Murmur3.hash32(42L, 17L)).isNotEqualTo(0);
        assertThat(Murmur3.hash64(new byte[] {1, 2, 3})).isEqualTo(Murmur3.hash64(new byte[] {1, 2, 3}));
        assertThat(PositionBound.unbounded().isUnbounded()).isTrue();

        assertThat(Printed.<String, String>toFile("build/coverage-output.txt").withName("file-printer"))
                .isNotNull();
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded())
                .withName("named-suppression")).isNotNull();
        assertThat(Materialized.as(Stores.lruMap("materialized", 5))).isNotNull();
        assertThat(Materialized.as(Materialized.StoreType.ROCKS_DB)).isNotNull();
        assertThatThrownBy(() -> new FailOnInvalidTimestamp().extract(
                new ConsumerRecord<>("topic", 0, 1L, "key", "value"), 0L))
                .hasMessageContaining("invalid (negative) timestamp");
    }

    @Test
    void shouldDriveStateStoresAndProcessorsThroughTheTopologyPublicApi() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream("coverage-input", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> lookup = builder.table("coverage-lookup", Materialized.as("lookup-store"));
        input.groupByKey().count(Materialized.as("count-store"));
        input.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("window-count-store"));
        input.leftJoin(lookup, (left, right) -> left + ":" + right).to("coverage-output");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-topology-driver");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        Topology topology = builder.build();
        try (TopologyTestDriver driver = new TopologyTestDriver(topology, properties)) {
            TestInputTopic<String, String> lookupInput = driver.createInputTopic(
                    "coverage-lookup", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> inputTopic = driver.createInputTopic(
                    "coverage-input", new StringSerializer(), new StringSerializer());
            lookupInput.pipeInput("key", "table-value", 1L);
            inputTopic.pipeInput("key", "stream-value", 2L);

            KeyValueStore<String, Long> counts = driver.getKeyValueStore("count-store");
            assertThat(counts.get("key")).isEqualTo(1L);
            try (KeyValueIterator<String, Long> entries = counts.all()) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next()).isEqualTo(KeyValue.pair("key", 1L));
            }
            WindowStore<String, Long> windows = driver.getWindowStore("window-count-store");
            try (WindowStoreIterator<Long> entries = windows.fetch("key", 0L, 10_000L)) {
                assertThat(entries.hasNext()).isTrue();
                assertThat(entries.next().value).isEqualTo(1L);
            }
            assertThat(driver.createOutputTopic("coverage-output", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("stream-value:table-value");
        }
    }

    @Test
    void shouldDrivePersistentStoresThroughPublicTopologyOperations() throws Exception {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> table = builder.table(
                "persistent-table", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("persistent-key-values"));
        builder.stream("persistent-events", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("persistent-windows"));
        table.toStream().to("persistent-output", Produced.with(Serdes.String(), Serdes.String()));

        Path stateDirectory = Files.createTempDirectory("kafka-streams-coverage-");
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-persistent-stores");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, stateDirectory.toString());
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties)) {
            TestInputTopic<String, String> tableInput = driver.createInputTopic(
                    "persistent-table", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> eventInput = driver.createInputTopic(
                    "persistent-events", new StringSerializer(), new StringSerializer());
            tableInput.pipeInput("key", "value", 1L);
            eventInput.pipeInput("key", "event", 2L);

            KeyValueStore<String, String> values = driver.getKeyValueStore("persistent-key-values");
            assertThat(values.get("key")).isEqualTo("value");
            values.flush();
            WindowStore<String, Long> windows = driver.getWindowStore("persistent-windows");
            try (WindowStoreIterator<Long> entries = windows.fetch("key", 0L, 10_000L)) {
                assertThat(entries.next().value).isEqualTo(1L);
            }
            windows.flush();
            assertThat(driver.createOutputTopic("persistent-output", new StringDeserializer(),
                    new StringDeserializer()).readValue()).isEqualTo("value");
        }
    }

    @Test
    void shouldExecuteTableJoinsSuppressionAndWindowedProcessors() {
        StreamsBuilder builder = new StreamsBuilder();
        KTable<String, String> left = builder.table("join-left", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> right = builder.table("join-right", Consumed.with(Serdes.String(), Serdes.String()));
        left.join(right, (leftValue, rightValue) -> leftValue + rightValue).toStream().to("inner-results");
        left.leftJoin(right, (leftValue, rightValue) -> leftValue + ":" + rightValue)
                .toStream().to("left-results");
        left.outerJoin(right, (leftValue, rightValue) -> String.valueOf(leftValue) + ":" + rightValue)
                .toStream().to("outer-results");
        left.groupBy((key, value) -> KeyValue.pair(key, value))
                .reduce((oldValue, newValue) -> newValue, (oldValue, newValue) -> oldValue,
                        Materialized.as("suppressed-values"))
                .suppress(Suppressed.untilTimeLimit(Duration.ofMillis(1), Suppressed.BufferConfig.unbounded()))
                .toStream().to("suppressed-results");

        KStream<String, String> events = builder.stream("window-events", Consumed.with(Serdes.String(), Serdes.String()));
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .reduce((oldValue, newValue) -> oldValue + newValue, Materialized.as("executed-session"));
        events.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)))
                .count(Materialized.as("executed-sliding"));

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "coverage-joined-topology");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties)) {
            TestInputTopic<String, String> leftInput = driver.createInputTopic(
                    "join-left", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> rightInput = driver.createInputTopic(
                    "join-right", new StringSerializer(), new StringSerializer());
            TestInputTopic<String, String> eventsInput = driver.createInputTopic(
                    "window-events", new StringSerializer(), new StringSerializer());
            rightInput.pipeInput("key", "right", 0L);
            leftInput.pipeInput("key", "left", 1L);
            // Drive both the in-order and late-record branches of the public sliding-window DSL.
            // A later record advances stream time so final-result scans are also observable.
            eventsInput.pipeInput("key", "first", 3_000L);
            eventsInput.pipeInput("key", "late", 2_000L);
            eventsInput.pipeInput("key", "second", 4_000L);
            eventsInput.pipeInput("key", "advance-stream-time", 20_000L);
            driver.advanceWallClockTime(Duration.ofMillis(2));

            assertThat(driver.createOutputTopic("inner-results", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("leftright");
            assertThat(driver.createOutputTopic("left-results", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("left:right");
            assertThat(driver.createOutputTopic("outer-results", new StringDeserializer(), new StringDeserializer())
                    .readValue()).isEqualTo("null:right");
        }
    }

    @Test
    void shouldExposeKafkaStreamsMetadataBeforeStartupAndCloseCleanly() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("input").to("output");
        Properties properties = new Properties();
        properties.put("application.id", "coverage-api");
        properties.put("bootstrap.servers", "localhost:1");
        KafkaStreams streams = new KafkaStreams(builder.build(), properties, Time.SYSTEM);
        assertThat(streams.metrics()).isNotNull();
        assertThatThrownBy(streams::allMetadata).hasMessageContaining("has not been started");
        assertThatThrownBy(() -> streams.allMetadataForStore("unknown"))
                .hasMessageContaining("has not been started");
        assertThat(streams.localThreadsMetadata()).hasSize(1);
        assertThat(streams.allLocalStorePartitionLags()).isEmpty();
        assertThatThrownBy(() -> streams.queryMetadataForKey("unknown", "key", Serdes.String().serializer()))
                .hasMessageContaining("has not been started");
        streams.pause();
        streams.resume();
        assertThat(streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(1)))).isTrue();
    }

    @Test
    void shouldBuildLowLevelTopologySourceSinkAndProcessorVariants() {
        Topology topology = new Topology();
        TimestampExtractor timestampExtractor = (record, previousTimestamp) -> record.timestamp();
        topology.addSource("plain-source", "plain-topic")
                .addSource("pattern-source", Pattern.compile("pattern-.*"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, "reset-source", "reset-topic")
                .addSource(Topology.AutoOffsetReset.LATEST, "reset-pattern-source", Pattern.compile("latest-.*"))
                .addSource("serde-source", Serdes.String().deserializer(), Serdes.String().deserializer(), "serde-topic")
                .addSource("serde-pattern-source", Serdes.String().deserializer(), Serdes.String().deserializer(),
                        Pattern.compile("deserialized-.*"))
                .addSource(Topology.AutoOffsetReset.EARLIEST, "full-source", timestampExtractor,
                        Serdes.String().deserializer(), Serdes.String().deserializer(), "full-topic")
                .addProcessor("processor", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        assertThat(key).isNotNull();
                    }
                }, "plain-source")
                .addSink("plain-sink", "plain-output", "processor")
                .addSink("serde-sink", "serde-output", Serdes.String().serializer(), Serdes.String().serializer(),
                        "serde-source")
                .addSink("partitioned-sink", "partitioned-output", Serdes.String().serializer(),
                        Serdes.String().serializer(), (topic, key, value, partitions) -> 0, "full-source")
                .addSink("extracted-sink", (key, value, context) -> "dynamic-output", "pattern-source")
                .addSink("extracted-partitioned-sink", (key, value, context) -> "dynamic-partitioned-output",
                        (topic, key, value, partitions) -> 0, "reset-source")
                .addSink("extracted-serde-sink", (key, value, context) -> "dynamic-serde-output",
                        Serdes.String().serializer(), Serdes.String().serializer(), "reset-pattern-source")
                .addSink("extracted-full-sink", (key, value, context) -> "dynamic-full-output",
                        Serdes.String().serializer(), Serdes.String().serializer(),
                        (topic, key, value, partitions) -> 0, "serde-pattern-source");

        assertThat(topology.describe().toString())
                .contains("plain-output")
                .contains("extracted-full-sink")
                .contains("processor");
    }

    @Test
    void shouldConfigureKafkaStreamsThroughPublicConstructorAndLifecycleApi() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("input").to("output");
        Properties properties = new Properties();
        properties.put("application.id", "coverage-api-lifecycle");
        properties.put("bootstrap.servers", "localhost:1");
        KafkaStreams streams = new KafkaStreams(builder.build(), properties, new DefaultKafkaClientSupplier(), Time.SYSTEM);
        streams.setUncaughtExceptionHandler((Thread thread, Throwable failure) -> assertThat(failure).isNotNull());
        streams.setUncaughtExceptionHandler(failure ->
                org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD);
        streams.setGlobalStateRestoreListener(new StateRestoreListener() {
            @Override
            public void onRestoreStart(org.apache.kafka.common.TopicPartition partition, String store,
                                       long startingOffset, long endingOffset) {
            }

            @Override
            public void onBatchRestored(org.apache.kafka.common.TopicPartition partition, String store,
                                        long batchEndOffset, long numRestored) {
            }

            @Override
            public void onRestoreEnd(org.apache.kafka.common.TopicPartition partition, String store,
                                     long totalRestored) {
            }
        });
        streams.pause();
        assertThat(streams.isPaused()).isTrue();
        streams.resume();
        assertThat(streams.isPaused()).isFalse();
        assertThat(streams.removeStreamThread()).isEmpty();
        assertThat(streams.removeStreamThread(Duration.ofMillis(1))).isEmpty();
        assertThatThrownBy(() -> streams.metadataForAllStreamsClients())
                .hasMessageContaining("has not been started");
        assertThatThrownBy(() -> streams.streamsMetadataForStore("unknown"))
                .hasMessageContaining("has not been started");
        assertThatThrownBy(() -> streams.queryMetadataForKey("unknown", "key", (topic, key, value, partitions) -> 0))
                .hasMessageContaining("has not been started");
        assertThat(streams.close(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void shouldExposeValueObjectAndConfigurationContracts() {
        KeyQueryMetadata metadata = new KeyQueryMetadata(
                new org.apache.kafka.streams.state.HostInfo("active", 9092),
                Set.of(new org.apache.kafka.streams.state.HostInfo("standby", 9093)), 4);
        assertThat(metadata.activeHost().host()).isEqualTo("active");
        assertThat(metadata.getActiveHost()).isEqualTo(metadata.activeHost());
        assertThat(metadata.partition()).isEqualTo(4);
        assertThat(metadata.getPartition()).isEqualTo(4);
        assertThat(metadata.standbyHosts()).containsExactlyElementsOf(metadata.getStandbyHosts());

        StoreQueryParameters<?> query = StoreQueryParameters
                .fromNameAndType("store", QueryableStoreTypes.keyValueStore())
                .withPartition(2)
                .enableStaleStores();
        assertThat(query.storeName()).isEqualTo("store");
        assertThat(query.partition()).isEqualTo(2);
        assertThat(query.staleStoresEnabled()).isTrue();

        assertThat(StreamsConfig.consumerPrefix("client.")).isEqualTo("consumer.client.");
        assertThat(StreamsConfig.mainConsumerPrefix("client.")).isEqualTo("main.consumer.client.");
        assertThat(StreamsConfig.globalConsumerPrefix("client.")).isEqualTo("global.consumer.client.");
        assertThat(StreamsConfig.adminClientPrefix("client.")).isEqualTo("admin.client.");
        assertThat(StreamsConfig.clientTagPrefix("region")).isEqualTo("client.tag.region");
        assertThat(StreamsConfig.configDef().names()).contains(StreamsConfig.APPLICATION_ID_CONFIG);

        assertThat(KafkaStreams.State.valueOf("RUNNING")).isEqualTo(KafkaStreams.State.RUNNING);
        assertThat(KafkaStreams.State.values()).contains(KafkaStreams.State.CREATED);
        assertThat(KafkaStreams.State.PENDING_SHUTDOWN.hasStartedOrFinishedShuttingDown()).isTrue();
        assertThat(new KafkaStreams.CloseOptions().leaveGroup(true)).isNotNull();

        org.apache.kafka.streams.state.internals.Murmur3.IncrementalHash32 hash =
                new org.apache.kafka.streams.state.internals.Murmur3.IncrementalHash32();
        hash.start(0);
        hash.add(new byte[] {1, 2, 3}, 0, 3);
        int incrementalHash = hash.end();
        hash.start(0);
        hash.add(new byte[] {1, 2, 3}, 0, 3);
        assertThat(hash.end()).isEqualTo(incrementalHash);
        assertThatThrownBy(() -> PendingUpdateAction.createCloseClean().getInputPartitions())
                .hasMessageContaining("does not have a set of input partitions");
    }

    @Test
    void shouldRoundTripTaskIds() {
        TaskId taskId = new TaskId(3, 7);
        ByteBuffer buffer = ByteBuffer.allocate(8);
        taskId.writeTo(buffer, 2);
        buffer.flip();
        assertThat(TaskId.readFrom(buffer, 2)).isEqualTo(taskId);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            taskId.writeTo(output, 2);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
        assertThat(bytes.toByteArray()).hasSize(8);
    }

}
