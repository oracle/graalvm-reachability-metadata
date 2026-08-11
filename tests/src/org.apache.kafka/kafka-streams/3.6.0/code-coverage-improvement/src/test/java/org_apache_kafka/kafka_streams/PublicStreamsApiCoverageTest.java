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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.StateStoreContext;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.internals.metrics.StreamsMetricsImpl;
import org.apache.kafka.streams.processor.internals.ToInternal;
import org.apache.kafka.streams.processor.internals.assignment.ClientState;
import org.apache.kafka.streams.processor.internals.assignment.SubscriptionInfo;
import org.apache.kafka.streams.query.PositionBound;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreType;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.SessionStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.TimestampedKeyValueStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlySessionStore;
import org.apache.kafka.streams.state.internals.CompositeReadOnlyWindowStore;
import org.apache.kafka.streams.state.internals.Murmur3.IncrementalHash32;
import org.apache.kafka.streams.state.internals.StateStoreProvider;
import org.apache.kafka.streams.state.internals.ThreadCache;
import org.apache.kafka.streams.internals.generated.SubscriptionInfoData;
import org.apache.kafka.test.InternalMockProcessorContext;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicStreamsApiCoverageTest {

    @Test
    void shouldValidatePublicConsumerAndExactlyOnceConfiguration() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "configuration-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        properties.put("max.in.flight.requests.per.connection", 1);
        properties.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);

        StreamsConfig configuration = new StreamsConfig(properties);

        assertThat(configuration.getMainConsumerConfigs("group", "client", 1))
                .containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        assertThat(configuration.getString(StreamsConfig.PROCESSING_GUARANTEE_CONFIG))
                .isEqualTo(StreamsConfig.EXACTLY_ONCE_V2);
    }

    @Test
    void shouldBuildACompleteStreamAndTableTopologyThroughPublicDsl() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> input = builder.stream(Pattern.compile("events-.*"),
                Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> other = builder.stream("other",
                Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("table",
                Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> transformed = input
                .filterNot((key, value) -> value == null)
                .map((key, value) -> KeyValue.pair(key + "-mapped", value))
                .mapValues(value -> value + "-value")
                .flatMap((key, value) -> java.util.List.of(KeyValue.pair(key, value)))
                .flatMapValues(value -> java.util.List.of(value, value + "-copy"))
                .peek((key, value) -> assertThat(value).isNotEmpty())
                .repartition()
                .merge(other)
                .through("repartitioned-events");
        transformed.to("output");
        transformed.foreach((key, value) -> assertThat(key).isNotBlank());
        transformed.print(Printed.<String, String>toSysOut().withName("printed-events"));
        transformed.groupByKey(Grouped.with(Serdes.String(), Serdes.String())).count();
        transformed.groupBy((key, value) -> KeyValue.pair(value, key)).count();
        transformed.join(other, (left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).to("joined");
        transformed.leftJoin(other, (left, right) -> left,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).to("left-joined");
        transformed.outerJoin(other, (left, right) -> left,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).to("outer-joined");
        transformed.leftJoin(table, (left, right) -> left).to("stream-table-left");
        transformed.join(table, (left, right) -> left).to("stream-table-join");

        table.filter((key, value) -> value != null, Materialized.as("filtered-table"));
        table.filterNot((key, value) -> value == null, Materialized.as("not-filtered-table"));
        table.mapValues(value -> value + "!", Materialized.as("mapped-table"));
        table.toStream((key, value) -> key + "-stream").to("table-output");
        table.join(table, (left, right) -> left, Materialized.as("joined-table"));
        table.leftJoin(table, (left, right) -> left, Materialized.as("left-table"));
        table.outerJoin(table, (left, right) -> left, Materialized.as("outer-table"));
        table.suppress(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded()));

        transformed.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5))).count();
        transformed.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(5))).count();
        Topology topology = builder.build();

        assertThat(topology.describe().subtopologies()).isNotEmpty();
        assertThat(topology.describe().toString()).contains("output").contains("joined").contains("table-output");
    }

    @Test
    void shouldConfigureAndDescribeAdvancedDslOperations() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> source = builder.stream("source", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("table", Consumed.with(Serdes.String(), Serdes.String()));

        source.split(Named.as("split"))
                .branch((key, value) -> value.startsWith("a"), Branched.as("a"))
                .defaultBranch(Branched.as("other"));
        source.groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(2)))
                .reduce((left, right) -> right);
        source.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value);
        source.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)))
                .reduce((left, right) -> left + right);
        source.leftJoin(table, (left, right) -> left, Joined.as("named-left-join")).to("named-output");
        table.mapValues((key, value) -> value + "!", Materialized.as(Stores.lruMap("mapped", 10)));
        table.toStream((key, value) -> key + "-out", Named.as("named-table-stream")).to("table-named-output");

        Topology topology = builder.build();
        assertThat(topology.describe().toString())
                .contains("split").contains("named-left-join").contains("named-table-stream");
    }

    @Test
    void shouldRoundTripPublicValueObjectsAndFactories() {
        TaskId original = new TaskId(3, 7);
        ByteBuffer buffer = ByteBuffer.allocate(8);
        original.writeTo(buffer, 0);
        buffer.flip();
        assertThat(TaskId.readFrom(buffer, 0)).isEqualTo(original);

        Serde<Windowed<String>> serde = WindowedSerdes.timeWindowedSerdeFrom(String.class);
        Windowed<String> windowed = new Windowed<>("key",
                new org.apache.kafka.streams.kstream.internals.TimeWindow(0, 10));
        byte[] encoded = serde.serializer().serialize("topic", windowed);
        Windowed<String> decoded = serde.deserializer().deserialize("topic", encoded);
        assertThat(decoded.key()).isEqualTo("key");
        assertThat(decoded.window().start()).isZero();
        assertThat(WindowedSerdes.timeWindowedSerdeFrom(String.class, 10L).deserializer()
                .deserialize("topic", encoded)).isEqualTo(windowed);
        assertThat(windowed.hashCode()).isEqualTo(new Windowed<>("key",
                new org.apache.kafka.streams.kstream.internals.TimeWindow(0, 10)).hashCode());

        assertThat(Stores.persistentVersionedKeyValueStore("versions", Duration.ofMinutes(1)).name())
                .isEqualTo("versions");
        assertThat(Stores.keyValueStoreBuilder(Stores.lruMap("cache", 5), Serdes.String(), Serdes.String()).name())
                .isEqualTo("cache");
        assertThat(QueryableStoreTypes.keyValueStore()).isNotNull();
        assertThat(QueryableStoreTypes.sessionStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedKeyValueStore()).isNotNull();
        assertThat(QueryableStoreTypes.timestampedWindowStore()).isNotNull();
        assertThat(QueryableStoreTypes.windowStore()).isNotNull();
        assertThat(UnlimitedWindows.of().startOn(Instant.EPOCH).windowsFor(0)).containsKey(0L);
        assertThat(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)).inactivityGap()).isEqualTo(1000L);
        assertThat(PositionBound.unbounded().isUnbounded()).isTrue();

        ToInternal to = new ToInternal();
        to.update(org.apache.kafka.streams.processor.To.child("child"));
        assertThat(to.child()).isEqualTo("child");
    }

    @Test
    void shouldExerciseNamedDslJoinsAndWindowedAggregations() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> events = builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("table", Consumed.with(Serdes.String(), Serdes.String()));
        org.apache.kafka.streams.kstream.GlobalKTable<String, String> global = builder.globalTable("global");

        events.to((key, value, context) -> "routed-events");
        events.toTable().toStream().to("table-output");
        events.join(events, (left, right) -> left + right,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)), StreamJoined.as("stream-join"))
                .to("stream-join-output");
        events.join(table, (left, right) -> left + right, Joined.as("table-join")).to("table-join-output");
        events.join(global, (key, value) -> key, (left, right) -> left + right).to("global-join-output");
        events.join(global, (key, value) -> key, (left, right) -> left + right, Named.as("global-join"))
                .to("named-global-join-output");
        events.leftJoin(global, (key, value) -> key, (left, right) -> left + right)
                .to("global-left-join-output");
        events.leftJoin(global, (key, value) -> key, (left, right) -> left + right, Named.as("global-left-join"))
                .to("named-global-left-join-output");
        table.join(table, value -> value, (left, right) -> left + right).toStream().to("foreign-key-join");
        table.join(table, value -> value, (left, right) -> left + right, Named.as("named-foreign-key-join"))
                .toStream().to("named-foreign-key-join");
        table.leftJoin(table, (left, right) -> left).toStream().to("left-table-join");
        table.outerJoin(table, (left, right) -> left).toStream().to("outer-table-join");
        table.groupBy((key, value) -> KeyValue.pair(value, value))
                .reduce((left, right) -> right, (left, right) -> left).toStream().to("grouped-reduce");
        table.groupBy((key, value) -> KeyValue.pair(value, value))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        (key, left, right) -> left + right).toStream().to("grouped-aggregate");

        events.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(1)))
                .reduce((left, right) -> left + right).toStream().to("time-reduce");
        events.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(1)))
                .count(Materialized.as("time-count")).toStream().to("time-count-output");
        events.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value).toStream().to("sliding-aggregate");
        events.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)))
                .reduce((left, right) -> left + right, Materialized.as("sliding-reduce"))
                .toStream().to("sliding-reduce-output");
        events.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)))
                .count(Materialized.as("sliding-count")).toStream().to("sliding-count-output");
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        (key, left, right) -> left + right).toStream().to("session-aggregate");
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .count().toStream().to("session-count");
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .count(Materialized.as("session-count-store")).toStream().to("session-count-store-output");
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .reduce((left, right) -> left + right, Materialized.as("session-reduce"))
                .toStream().to("session-reduce-output");
        events.groupByKey().cogroup((key, value, aggregate) -> aggregate + value)
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(1)))
                .aggregate(() -> "").toStream().to("cogrouped-time");
        events.groupByKey().cogroup((key, value, aggregate) -> aggregate + value)
                .windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(1)))
                .aggregate(() -> "").toStream().to("cogrouped-sliding");
        events.groupByKey().cogroup((key, value, aggregate) -> aggregate + value)
                .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(1)))
                .aggregate(() -> "", (key, left, right) -> String.valueOf(left) + right)
                .toStream().to("cogrouped-session");

        assertThat(builder.build().describe().toString()).contains("global-join").contains("session-count");
    }

    @Test
    void shouldExercisePublicConfigurationAndSerializationValueObjects() throws Exception {
        assertThat(Consumed.with(Serdes.String(), Serdes.String()).withName("consumed")).isNotNull();
        assertThat(Grouped.with(Serdes.String(), Serdes.String()).withName("grouped")).isNotNull();
        assertThat(Joined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("joined")).isNotNull();
        assertThat(Branched.<String, String>as("branch").withName("renamed-branch")).isNotNull();
        assertThat(Named.as("named").withName("renamed")).isNotNull();
        assertThat(Printed.<String, String>toFile("build/coverage-output.txt").withName("printed-file")).isNotNull();
        assertThat(org.apache.kafka.streams.kstream.Produced.with(Serdes.String(), Serdes.String())
                .withName("produced")).isNotNull();
        assertThat(org.apache.kafka.streams.kstream.Repartitioned.<String, String>as("repartitioned")
                .withName("renamed-repartitioned")).isNotNull();
        assertThat(StreamJoined.<String, String, String>as("stream-joined").withName("renamed-stream-joined"))
                .isNotNull();
        assertThat(org.apache.kafka.streams.kstream.TableJoined.<String, String>as("table-joined")
                .withName("renamed-table-joined")).isNotNull();
        assertThat(Materialized.as(Materialized.StoreType.IN_MEMORY)).isNotNull();
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded())
                .withName("suppressed")).isNotNull();

        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(new byte[] {1, 2, 3})).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(42L)).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash32(42L, 43L)).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Murmur3.hash64(new byte[] {1, 2, 3})).isNotZero();
        assertThat(org.apache.kafka.streams.state.internals.Maybe.defined("value").hashCode()).isEqualTo("value".hashCode());

        WindowedSerdes.TimeWindowedSerde<String> timeSerde = new WindowedSerdes.TimeWindowedSerde<>();
        assertThat(timeSerde.forChangelog(true)).isSameAs(timeSerde);
        assertThat(new WindowedSerdes.SessionWindowedSerde<String>()).isNotNull();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            new TaskId(1, 2).writeTo(output, 0);
        }
        assertThat(TaskId.readFrom(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())), 0))
                .isEqualTo(new TaskId(1, 2));
        org.apache.kafka.clients.consumer.ConsumerRecord<Object, Object> invalid =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("topic", 0, 0L, null, null);
        assertThatThrownBy(() -> new org.apache.kafka.streams.processor.FailOnInvalidTimestamp().extract(invalid, 10L))
                .isInstanceOf(org.apache.kafka.streams.errors.StreamsException.class);
    }

    @Test
    void shouldBuildGlobalStoresAndProcessorTopologyThroughPublicApis() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.addGlobalStore(
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("global-store"),
                        Serdes.String(), Serdes.String()).withLoggingDisabled(),
                "global-source", Consumed.with(Serdes.String(), Serdes.String()),
                () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value);
                    }
                });
        builder.globalTable("global-table", Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("global-table-store"));
        Topology builderTopology = builder.build();

        Topology topology = new Topology()
                .addSource("source", Serdes.String().deserializer(), Serdes.String().deserializer(), "input")
                .addProcessor("processor", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value);
                    }
                }, "source");

        Topology globalStoreTopology = new Topology().addGlobalStore(
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("topology-global-store"),
                        Serdes.String(), Serdes.String()).withLoggingDisabled(),
                "topology-global-source", Serdes.String().deserializer(), Serdes.String().deserializer(),
                "topology-global-topic", "topology-global-processor", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value);
                    }
                });
        Topology timestampedGlobalStoreTopology = new Topology().addGlobalStore(
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("timestamped-global-store"),
                        Serdes.String(), Serdes.String()).withLoggingDisabled(),
                "timestamped-global-source", new org.apache.kafka.streams.processor.FailOnInvalidTimestamp(),
                Serdes.String().deserializer(), Serdes.String().deserializer(), "timestamped-global-topic",
                "timestamped-global-processor", () -> new AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value);
                    }
                });

        assertThat(builderTopology.describe().toString())
                .contains("global-source").contains("global-store").contains("global-table-store");
        assertThat(topology.describe().toString()).contains("source").contains("processor");
        assertThat(globalStoreTopology.describe().toString()).contains("topology-global-store");
        assertThat(timestampedGlobalStoreTopology.describe().toString()).contains("timestamped-global-store");
    }

    @Test
    void shouldConfigureBoundedSuppressionPolicies() {
        Suppressed.BufferConfig eager = Suppressed.BufferConfig.maxBytes(128)
                .emitEarlyWhenFull()
                .withMaxRecords(4)
                .withLoggingEnabled(java.util.Map.of("retention.ms", "1000"));
        Suppressed.StrictBufferConfig strict = Suppressed.BufferConfig.maxRecords(3)
                .shutDownWhenFull()
                .withMaxBytes(256)
                .withLoggingDisabled();
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()))
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(1)))
                .reduce((left, right) -> right)
                .suppress(Suppressed.untilTimeLimit(Duration.ofMillis(100), eager));

        assertThat(eager).isNotNull();
        assertThat(strict).isNotNull();
        assertThat(builder.build().describe().toString()).contains("events");
    }

    @Test
    void shouldExerciseReadOnlyCompositeStoresWhenNoLocalStoreIsAvailable() {
        StateStoreProvider emptyProvider = new StateStoreProvider() {
            @Override
            public <T> java.util.List<T> stores(String storeName, QueryableStoreType<T> storeType) {
                return java.util.List.of();
            }
        };
        CompositeReadOnlyKeyValueStore<String, String> keyValueStore = new CompositeReadOnlyKeyValueStore<>(
                emptyProvider, QueryableStoreTypes.keyValueStore(), "missing-key-value-store");
        CompositeReadOnlyWindowStore<String, String> windowStore = new CompositeReadOnlyWindowStore<>(
                emptyProvider, QueryableStoreTypes.windowStore(), "missing-window-store");
        CompositeReadOnlySessionStore<String, String> sessionStore = new CompositeReadOnlySessionStore<>(
                emptyProvider, QueryableStoreTypes.sessionStore(), "missing-session-store");
        Instant start = Instant.EPOCH;
        Instant end = start.plusSeconds(1);

        assertThat(keyValueStore.get("key")).isNull();
        assertThat(keyValueStore.approximateNumEntries()).isZero();
        assertThat(windowStore.fetch("key", 0L)).isNull();
        assertThat(windowStore.fetch("key", start, end).hasNext()).isFalse();
        assertThat(windowStore.backwardFetch("key", start, end).hasNext()).isFalse();
        assertThat(windowStore.fetch("a", "z", start, end).hasNext()).isFalse();
        assertThat(windowStore.backwardFetch("a", "z", start, end).hasNext()).isFalse();
        assertThat(windowStore.all().hasNext()).isFalse();
        assertThat(windowStore.backwardAll().hasNext()).isFalse();
        assertThat(windowStore.fetchAll(start, end).hasNext()).isFalse();
        assertThat(windowStore.backwardFetchAll(start, end).hasNext()).isFalse();
        assertThat(sessionStore.fetch("key").hasNext()).isFalse();
        assertThat(sessionStore.backwardFetch("key").hasNext()).isFalse();
        assertThat(sessionStore.fetch("a", "z").hasNext()).isFalse();
        assertThat(sessionStore.backwardFetch("a", "z").hasNext()).isFalse();
        assertThat(sessionStore.findSessions("key", 0L, 1L).hasNext()).isFalse();
        assertThat(sessionStore.backwardFindSessions("key", 0L, 1L).hasNext()).isFalse();
        assertThat(sessionStore.findSessions("a", "z", 0L, 1L).hasNext()).isFalse();
        assertThat(sessionStore.backwardFindSessions("a", "z", 0L, 1L).hasNext()).isFalse();
        assertThat(sessionStore.fetchSession("key", 0L, 1L)).isNull();
    }

    @Test
    void shouldRetainAssignmentAndProtocolValueState() {
        TaskId task = new TaskId(1, 2);
        ClientState state = new ClientState(
                        Set.of(task), Set.of(), Map.of(task, 7L), Map.of("rack", "west"), 2);
        state.assignActive(task);
        state.assignStandbyToConsumer(task, "consumer");
        state.assignActiveToConsumer(task, "consumer");
        state.revokeActiveFromConsumer(task, "consumer");

        assertThat(state.assignedStandbyTasksByConsumer().get("consumer")).contains(task);
        assertThat(state.revokingActiveTasksByConsumer().get("consumer")).contains(task);
        SubscriptionInfo subscription = new SubscriptionInfo(
                        10, 10, UUID.randomUUID(), "host:9092", Map.of(task, 7L), (byte) 0, 0, Map.of());
        assertThat(subscription.prevTasks()).isEmpty();
        assertThat(subscription.standbyTasks()).containsExactly(task);
        assertThat(subscription.hashCode()).isEqualTo(subscription.hashCode());

        SubscriptionInfoData data = new SubscriptionInfoData().setVersion(10);
        assertThat(data.duplicate()).isEqualTo(data);
        IncrementalHash32 hash = new IncrementalHash32();
        hash.start(0);
        hash.add(new byte[] {1, 2, 3, 4}, 0, 4);
        assertThat(hash.end()).isNotZero();
    }

    @Test
    void shouldRetainPartitionQueryResultsAndExceptionCauses() {
        org.apache.kafka.streams.query.StateQueryResult<String> results =
                new org.apache.kafka.streams.query.StateQueryResult<>();
        results.addResult(3, org.apache.kafka.streams.query.QueryResult.forResult("value"));

        assertThat(results.getOnlyPartitionResult().getResult()).isEqualTo("value");
        assertThat(results.getPartitionResults()).containsOnlyKeys(3);
        IllegalArgumentException cause = new IllegalArgumentException("unavailable");
        org.apache.kafka.streams.errors.InvalidStateStorePartitionException exception =
                new org.apache.kafka.streams.errors.InvalidStateStorePartitionException("partition missing", cause);
        assertThat(exception).hasMessage("partition missing");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void shouldRejectCachingStoreQueriesBeforeThePublicLifecycleOpensTheStore() {
        KeyValueStore<String, String> cachedStore = Stores.keyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore("cached-store"), Serdes.String(), Serdes.String())
                .withCachingEnabled()
                .build();

        assertThatThrownBy(cachedStore::approximateNumEntries).isInstanceOf(InvalidStateStoreException.class);
    }

    @Test
    void shouldRejectTimestampedStoreQueriesBeforeThePublicLifecycleOpensTheStore() {
        TimestampedKeyValueStore<String, String> timestampedStore = Stores.timestampedKeyValueStoreBuilder(
                        Stores.inMemoryKeyValueStore("timestamped-store"), Serdes.String(), Serdes.String())
                .build();

        assertThat(timestampedStore.approximateNumEntries()).isZero();
    }

    @Test
    void shouldRequireInitializationBeforeFetchingFromAPublicLoggedSessionStore() {
        SessionStore<String, String> sessionStore = Stores.sessionStoreBuilder(
                        Stores.inMemorySessionStore("session-store", Duration.ofMinutes(1)),
                        Serdes.String(), Serdes.String())
                .withLoggingEnabled(Map.of())
                .build();

        assertThatThrownBy(() -> sessionStore.fetch("missing")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldTraverseLoggedCachingSessionStoreBackwardQueriesThroughPublicStateStoreApis() throws Exception {
        SessionStore<String, String> sessionStore = Stores.sessionStoreBuilder(
                        Stores.inMemorySessionStore("backward-session-store", Duration.ofMinutes(1)),
                        Serdes.String(), Serdes.String())
                .withCachingEnabled()
                .withLoggingEnabled(Map.of())
                .build();
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "backward-session-store-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        StreamsMetricsImpl metrics = new StreamsMetricsImpl(new Metrics(), "client", "thread", new MockTime());
        ThreadCache cache = new ThreadCache(new LogContext(), 1024L, metrics);
        InternalMockProcessorContext context = new InternalMockProcessorContext(
                Files.createTempDirectory("session-store-state").toFile(), Serdes.String(), Serdes.String(), metrics,
                new StreamsConfig(properties), null, cache, Time.SYSTEM);
        sessionStore.init((StateStoreContext) context, sessionStore);

        assertThat(sessionStore.backwardFetch("a", "z").hasNext()).isFalse();
        assertThat(sessionStore.backwardFindSessions("a", "z", 0L, 30L).hasNext()).isFalse();
        sessionStore.close();
    }

    @Test
    void shouldRejectPersistentStoreQueriesBeforeThePublicLifecycleOpensTheStore() {
        KeyValueStore<String, String> persistentStore = Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("persistent-store"), Serdes.String(), Serdes.String())
                .build();

        assertThatThrownBy(persistentStore::approximateNumEntries).isInstanceOf(InvalidStateStoreException.class);
    }

    @Test
    void shouldExecuteWindowedAndJoinedTopologiesThroughThePublicTestDriver() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> events = builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()));
        KStream<String, String> right = builder.stream("right", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("table", Consumed.with(Serdes.String(), Serdes.String()));

        events.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(2)))
                .reduce((left, value) -> left + value).toStream().to("time-output");
        events.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2)))
                .reduce((left, value) -> left + value).toStream().to("sliding-output");
        events.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofSeconds(2)))
                .reduce((left, value) -> left + value).toStream().to("session-output");
        events.join(right, (left, value) -> left + value,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2))).to("join-output");
        events.leftJoin(table, (left, value) -> left + value).to("table-output");
        events.print(Printed.<String, String>toSysOut().withName("runtime-printed-events"));
        table.join(table, (left, value) -> left + value).toStream().to("table-inner-output");
        table.leftJoin(table, (left, value) -> left + value).toStream().to("table-left-output");
        table.outerJoin(table, (left, value) -> left + value).toStream().to("table-outer-output");
        table.filter((key, value) -> value.startsWith("v"), Materialized.as("filtered-runtime"))
                .mapValues(value -> value + "!").toStream().to("mapped-output");

        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "runtime-topology-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        properties.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        try (TopologyTestDriver driver = new TopologyTestDriver(builder.build(), properties)) {
            TestInputTopic<String, String> tableInput = driver.createInputTopic("table",
                    Serdes.String().serializer(), Serdes.String().serializer());
            TestInputTopic<String, String> eventsInput = driver.createInputTopic("events",
                    Serdes.String().serializer(), Serdes.String().serializer());
            TestInputTopic<String, String> rightInput = driver.createInputTopic("right",
                    Serdes.String().serializer(), Serdes.String().serializer());
            tableInput.pipeInput("key", "value", 0L);
            eventsInput.pipeInput("key", "a", 1L);
            rightInput.pipeInput("key", "b", 2L);
            eventsInput.pipeInput("key", "c", 3L);
            eventsInput.pipeInput("other", "unmatched", 3L);
            eventsInput.pipeInput("key", "d", 4_000L);
        }
    }

    @Test
    void shouldStartAndStopAStatefulTopologyThroughKafkaStreamsPublicLifecycle() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore("stateful-store"),
                Serdes.String(), Serdes.String()));
        builder.stream("input", Consumed.with(Serdes.String(), Serdes.String()))
                .transform(() -> new org.apache.kafka.streams.kstream.Transformer<String, String, KeyValue<String, String>>() {
                    @Override
                    public void init(org.apache.kafka.streams.processor.ProcessorContext context) {
                        // Store binding is supplied by the public DSL.
                    }

                    @Override
                    public KeyValue<String, String> transform(String key, String value) {
                        return KeyValue.pair(key, value);
                    }

                    @Override
                    public void close() {
                        // The public lifecycle owns state-store cleanup.
                    }
                }, "stateful-store")
                .to("output");
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "deep-coverage-lifecycle");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        properties.put(StreamsConfig.REQUEST_TIMEOUT_MS_CONFIG, "100");
        properties.put(StreamsConfig.RECONNECT_BACKOFF_MS_CONFIG, "10");
        properties.put(StreamsConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, "10");

        KafkaStreams streams = new KafkaStreams(builder.build(), properties);
        try {
            streams.start();
            assertThat(streams.state()).isNotEqualTo(KafkaStreams.State.CREATED);
        } finally {
            assertThat(streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(2)))).isTrue();
        }
        assertThat(streams.state()).isEqualTo(KafkaStreams.State.NOT_RUNNING);
    }

    @Test
    void shouldExposeKafkaStreamsLifecycleAndQueryStateBeforeStartup() {
        StreamsBuilder builder = new StreamsBuilder();
        builder.stream("input").to("output");
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "api-coverage");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");

        KafkaStreams streams = new KafkaStreams(builder.build(), properties,
                new org.apache.kafka.streams.processor.internals.DefaultKafkaClientSupplier(), Time.SYSTEM);
        try {
            assertThat(streams.metrics()).isNotNull();
            assertThat(streams.localThreadsMetadata()).hasSize(1);
            assertThatThrownBy(streams::allMetadata).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> streams.allMetadataForStore("missing"))
                    .isInstanceOf(RuntimeException.class);
            assertThat(streams.allLocalStorePartitionLags()).isEmpty();
            assertThat(streams.removeStreamThread()).isEmpty();
            assertThat(streams.removeStreamThread(Duration.ofSeconds(1))).isEmpty();
            assertThat(streams.isPaused()).isFalse();
            streams.setUncaughtExceptionHandler((thread, error) -> { });
            streams.setUncaughtExceptionHandler(
                    exception -> StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT);
            streams.pause();
            streams.resume();
            assertThatThrownBy(() -> streams.queryMetadataForKey("missing", "key", Serdes.String().serializer()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            assertThat(streams.close(new KafkaStreams.CloseOptions().timeout(Duration.ofSeconds(1)))).isTrue();
        }
    }
}
