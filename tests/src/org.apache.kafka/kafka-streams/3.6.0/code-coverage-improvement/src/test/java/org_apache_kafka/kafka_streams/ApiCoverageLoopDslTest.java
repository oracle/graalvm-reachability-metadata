/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.EmitStrategy;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KGroupedStream;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Repartitioned;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.StreamJoined;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.kstream.internals.suppress.EagerBufferConfigImpl;
import org.apache.kafka.streams.kstream.internals.suppress.FinalResultsSuppressionBuilder;
import org.apache.kafka.streams.kstream.internals.suppress.StrictBufferConfigImpl;
import org.apache.kafka.streams.kstream.internals.suppress.SuppressedInternal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the public topology DSL through the join, grouping, suppression, and window subsystems. */
public class ApiCoverageLoopDslTest {
    @Test
    void shouldBuildAllWindowedGroupingAndJoinVariants() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> left = builder.stream("coverage-dsl-left",
                Consumed.with(Serdes.String(), Serdes.String()).withName("consumed"));
        KStream<String, String> right = builder.stream("coverage-dsl-right");
        KTable<String, String> table = builder.table("coverage-dsl-table");
        Materialized<String, Long, org.apache.kafka.streams.state.KeyValueStore<Bytes, byte[]>> materialized =
                Materialized.with(Serdes.String(), Serdes.Long());
        ValueJoiner<String, String, String> joiner = (a, b) -> String.valueOf(a) + b;
        StreamJoined<String, String, String> streamJoined = StreamJoined.with(
                Serdes.String(), Serdes.String(), Serdes.String()).as("stream-joined");
        JoinWindows joinWindows = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(2));

        assertThat(left.leftJoin(right, joiner, joinWindows, streamJoined)).isNotNull();
        assertThat(left.outerJoin(right, joiner, joinWindows, StreamJoined.with(
                Serdes.String(), Serdes.String(), Serdes.String()).as("outer-joined"))).isNotNull();
        assertThat(left.leftJoin(table, joiner)).isNotNull();
        assertThat(left.leftJoin(table, joiner)).isNotNull();
        KGroupedStream<String, String> grouped = left.groupByKey(Grouped.with(Serdes.String(), Serdes.String()));
        assertThat(grouped.cogroup((key, value, aggregate) -> aggregate + value)).isNotNull();
        assertThat(grouped.windowedBy(TimeWindows.of(Duration.ofSeconds(5))).count(
                Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(grouped.windowedBy(TimeWindows.of(Duration.ofSeconds(5))).reduce((a, b) -> a + b,
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(grouped.windowedBy(TimeWindows.of(Duration.ofSeconds(5))).aggregate(
                () -> "", (key, value, aggregate) -> aggregate + value, Named.as("time-aggregate"),
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(grouped.windowedBy(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1)))
                .count(Named.as("sliding-count"), Materialized.with(Serdes.String(), Serdes.Long()))).isNotNull();
        assertThat(grouped.windowedBy(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1)))
                .reduce((a, b) -> a + b, Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();
        assertThat(grouped.windowedBy(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value, Named.as("sliding-aggregate"))).isNotNull();
        assertThat(grouped.windowedBy(SlidingWindows.withTimeDifferenceAndGrace(Duration.ofSeconds(3), Duration.ofSeconds(1)))
                .emitStrategy(EmitStrategy.onWindowClose())).isNotNull();
        assertThat(grouped.windowedBy(SessionWindows.with(Duration.ofSeconds(4))).aggregate(
                () -> "", (key, value, aggregate) -> aggregate + value,
                (key, aggregate, merged) -> aggregate + merged)).isNotNull();
        assertThat(grouped.windowedBy(SessionWindows.with(Duration.ofSeconds(4))).aggregate(
                () -> "", (key, value, aggregate) -> aggregate + value,
                (key, aggregate, merged) -> aggregate + merged, Named.as("session-aggregate"),
                Materialized.with(Serdes.String(), Serdes.String()))).isNotNull();

        KTable<String, String> suppressedSource = builder.table("coverage-suppression-table");
        assertThat(suppressedSource.suppress(org.apache.kafka.streams.kstream.Suppressed.untilTimeLimit(
                Duration.ofSeconds(1), org.apache.kafka.streams.kstream.Suppressed.BufferConfig.maxRecords(10)))).isNotNull();
        assertThat(builder.build().describe().toString()).contains("coverage-dsl-left", "coverage-dsl-table");
    }

    @Test
    void shouldExerciseConfigurationValueObjectsAndNamedOperations() {
        assertThat(Named.as("named").withName("renamed")).isNotNull();
        assertThat(Branched.as("branch").withName("branch-renamed")).isNotNull();
        assertThat(Consumed.with(Serdes.String(), Serdes.String()).withName("input")).isNotNull();
        assertThat(Grouped.with(Serdes.String(), Serdes.String()).withName("group")).isNotNull();
        assertThat(Printed.<String, String>toSysOut().withName("print")).isNotNull();
        assertThat(Repartitioned.<String, String>with(Serdes.String(), Serdes.String()).withName("repartition")).isNotNull();
        assertThat(Produced.with(Serdes.String(), Serdes.String()).withName("output")).isNotNull();

        EagerBufferConfigImpl eager = new EagerBufferConfigImpl(10, 100, Map.of("segment.ms", "1"));
        assertThat(eager.withMaxRecords(4).withMaxBytes(40).withLoggingDisabled()).isNotNull();
        eager.withLoggingEnabled(Map.of("segment.ms", "2"));
        assertThat(eager.isLoggingEnabled()).isTrue();
        assertThat(eager.bufferFullStrategy()).isNotNull();
        assertThat(eager).isEqualTo(new EagerBufferConfigImpl(10, 100, Map.of("segment.ms", "1")));
        assertThat(eager.hashCode()).isEqualTo(new EagerBufferConfigImpl(10, 100, Map.of("segment.ms", "1")).hashCode());
        assertThat(eager.toString()).contains("EagerBufferConfig");

        StrictBufferConfigImpl strict = new StrictBufferConfigImpl();
        assertThat(strict.withMaxRecords(3).withMaxBytes(30).withLoggingDisabled()).isNotNull();
        strict.withLoggingEnabled(Map.of("retention.ms", "10"));
        assertThat(strict.isLoggingEnabled()).isTrue();
        assertThat(strict).isEqualTo(new StrictBufferConfigImpl());
        assertThat(strict.hashCode()).isEqualTo(new StrictBufferConfigImpl().hashCode());
        assertThat(strict.toString()).contains("StrictBufferConfig");

        FinalResultsSuppressionBuilder<Windowed<String>> finalBuilder =
                new FinalResultsSuppressionBuilder<>("final-name", new StrictBufferConfigImpl());
        SuppressedInternal<Windowed<String>> suppression = finalBuilder.buildFinalResultsSuppression(Duration.ofSeconds(2));
        assertThat(suppression).isNotNull();
        assertThat(finalBuilder.name()).isEqualTo("final-name");
        assertThat(finalBuilder.withName("other")).isNotNull();
        assertThat(finalBuilder.toString()).contains("final-name");
    }
}
