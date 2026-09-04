/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.internals.StreamsConfigUtils;
import org.apache.kafka.streams.internals.UpgradeFromValues;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindowedDeserializer;
import org.apache.kafka.streams.kstream.SessionWindowedSerializer;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TableJoined;
import org.apache.kafka.streams.kstream.TimeWindowedDeserializer;
import org.apache.kafka.streams.kstream.TimeWindowedSerializer;
import org.apache.kafka.streams.kstream.UnlimitedWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.WindowedSerdes;
import org.apache.kafka.streams.kstream.internals.NamedInternal;
import org.apache.kafka.streams.kstream.internals.UnlimitedWindow;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.StreamPartitioner;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies public value objects, codecs, enums, and configuration names used by the streams API. */
public class ApiCoverageBatchValuesTest {
    @Test
    void shouldRoundTripWindowedRecordsAndFixedKeyValues() throws Exception {
        Windowed<String> timeWindow = new Windowed<>("order", new org.apache.kafka.streams.kstream.internals.TimeWindow(10, 20));
        TimeWindowedSerializer<String> timeSerializer = new TimeWindowedSerializer<>(Serdes.String().serializer());
        TimeWindowedDeserializer<String> timeDeserializer = new TimeWindowedDeserializer<>(Serdes.String().deserializer(), 10L);
        byte[] timeBytes = timeSerializer.serialize("orders", timeWindow);
        assertThat(timeDeserializer.deserialize("orders", timeBytes)).isEqualTo(timeWindow);

        Windowed<String> sessionWindow = new Windowed<>("order", new org.apache.kafka.streams.kstream.internals.SessionWindow(10, 20));
        SessionWindowedSerializer<String> sessionSerializer = new SessionWindowedSerializer<>(Serdes.String().serializer());
        SessionWindowedDeserializer<String> sessionDeserializer = new SessionWindowedDeserializer<>(Serdes.String().deserializer());
        byte[] sessionBytes = sessionSerializer.serialize("orders", sessionWindow);
        assertThat(sessionDeserializer.deserialize("orders", sessionBytes)).isEqualTo(sessionWindow);
        assertThat(WindowedSerdes.sessionWindowedSerdeFrom(String.class)).isNotNull();
        assertThat(new WindowedSerdes.SessionWindowedSerde<>()).isNotNull();

        Headers headers = new RecordHeaders().add("trace", new byte[] {1});
        FixedKeyRecord<String, Integer> original = fixedKeyRecord("order", 3, 100L, headers);
        FixedKeyRecord<String, Integer> same = fixedKeyRecord("order", 3, 100L, headers);
        assertThat(original).isEqualTo(same);
        assertThat(original.hashCode()).isEqualTo(same.hashCode());
        assertThat(original.toString()).contains("order", "100");
        assertThat(original.withTimestamp(200L).timestamp()).isEqualTo(200L);
        assertThat(original.withHeaders(new RecordHeaders().add("route", new byte[] {2})).headers()).isNotEqualTo(headers);
        Record<String, Integer> record = new Record<>("order", 3, 100L);
        assertThat(record.withTimestamp(101L).timestamp()).isEqualTo(101L);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> FixedKeyRecord<K, V> fixedKeyRecord(K key, V value, long timestamp, Headers headers)
            throws Exception {
        Constructor<FixedKeyRecord> constructor = FixedKeyRecord.class.getDeclaredConstructor(
                Object.class, Object.class, long.class, Headers.class);
        constructor.setAccessible(true);
        return constructor.newInstance(key, value, timestamp, headers);
    }

    @Test
    void shouldUseEveryNamedOperationFactoryAsAStableConfiguration() {
        assertThat(NamedInternal.with("internal").withName("renamed").name()).isEqualTo("renamed");
        assertThat(NamedInternal.empty().withName("filled").name()).isEqualTo("filled");
        assertThat(Named.as("named").withName("renamed")).isNotNull();
        assertThat(Branched.<String, String>as("branch").withName("renamed")).isNotNull();
        assertThat(Consumed.with(Serdes.String(), Serdes.String()).withName("input")).isNotNull();
        assertThat(Grouped.with(Serdes.String(), Serdes.String()).withName("group")).isNotNull();
        assertThat(Joined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("join")).isNotNull();
        assertThat(Printed.<String, String>toSysOut().withName("print")).isNotNull();
        assertThat(Produced.with(Serdes.String(), Serdes.String()).withName("output")).isNotNull();
        assertThat(Repartitioned.with(Serdes.String(), Serdes.String()).withName("repartition")).isNotNull();
        assertThat(StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String()).withName("stream-join")).isNotNull();
        assertThat(TableJoined.as("table-join").withName("table-renamed")).isNotNull();
        assertThat(Suppressed.untilTimeLimit(Duration.ofSeconds(1), Suppressed.BufferConfig.unbounded()).withName("suppressed")).isNotNull();
    }

    @Test
    void shouldInspectEnumsAndWindowPartitionBehavior() {
        assertThat(KafkaStreams.State.valueOf("CREATED")).isEqualTo(KafkaStreams.State.CREATED);
        assertThat(KafkaStreams.State.values()).contains(KafkaStreams.State.RUNNING);
        assertThat(Topology.AutoOffsetReset.valueOf("EARLIEST")).isEqualTo(Topology.AutoOffsetReset.EARLIEST);
        assertThat(Topology.AutoOffsetReset.values()).isNotEmpty();
        assertThat(DeserializationExceptionHandler.DeserializationHandlerResponse.valueOf("FAIL")).isNotNull();
        assertThat(DeserializationExceptionHandler.DeserializationHandlerResponse.values()).isNotEmpty();
        assertThat(ProductionExceptionHandler.ProductionExceptionHandlerResponse.values()).isNotEmpty();
        assertThat(StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.values()).isNotEmpty();
        assertThat(StreamsConfigUtils.ProcessingMode.values()).isNotEmpty();
        assertThat(UpgradeFromValues.values()).isNotEmpty();
        assertThat(EmitStrategy.StrategyType.values()).isNotEmpty();
        assertThat(Materialized.StoreType.values()).isNotEmpty();
        assertThat(PunctuationType.values()).contains(PunctuationType.STREAM_TIME);
        assertThat(org.apache.kafka.streams.kstream.internals.suppress.BufferFullStrategy.values()).isNotEmpty();
        assertThat(org.apache.kafka.streams.kstream.internals.foreignkeyjoin.SubscriptionWrapper.Instruction.values()).isNotEmpty();

        UnlimitedWindow unlimited = new UnlimitedWindow(100L);
        assertThat(unlimited.overlap(new UnlimitedWindow(200L))).isTrue();
        assertThat(UnlimitedWindows.of().startOn(java.time.Instant.ofEpochMilli(100))).isNotNull();
        JoinWindows windows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2));
        assertThatThrownBy(() -> windows.windowsFor(100L)).isInstanceOf(UnsupportedOperationException.class);
        StreamPartitioner<String, String> partitioner = (topic, key, value, partitions) -> 1;
        assertThat(partitioner.partitions("orders", "key", "value", 3)).contains(java.util.Set.of(1));
        assertThat(new KeyQueryMetadata(null, java.util.Set.of(), 0).hashCode()).isNotZero();
    }
}
