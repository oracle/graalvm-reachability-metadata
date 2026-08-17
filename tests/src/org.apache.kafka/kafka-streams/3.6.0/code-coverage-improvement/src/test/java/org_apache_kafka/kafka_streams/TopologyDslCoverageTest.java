/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_streams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.SessionWindows;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.apache.kafka.streams.state.Stores;
import org.junit.Test;

public class TopologyDslCoverageTest {
    @Test
    public void buildsJoinsBranchesGroupsAndDynamicSink() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> events = builder.stream("events", Consumed.with(Serdes.String(), Serdes.String()));
        KTable<String, String> table = builder.table("table", Consumed.with(Serdes.String(), Serdes.String()));
        GlobalKTable<String, String> global = builder.globalTable("global", Consumed.with(Serdes.String(), Serdes.String()));

        events.join(table, (left, right) -> left + right).to("inner");
        events.join(table, (key, left, right) -> key + left + right,
                Joined.with(Serdes.String(), Serdes.String(), Serdes.String())).to("keyed-inner");
        events.leftJoin(table, (left, right) -> left + right).to("left");
        events.leftJoin(table, (key, left, right) -> key + left + right,
                Joined.with(Serdes.String(), Serdes.String(), Serdes.String())).to("keyed-left");
        events.join(global, (key, value) -> key, (left, right) -> left + right).to("global-inner");
        events.join(global, (key, value) -> key, (key, left, right) -> key + left + right,
                Named.as("named-global-inner")).to("named-global-inner-out");
        events.leftJoin(global, (key, value) -> key, (left, right) -> left + right).to("global-left");
        events.leftJoin(global, (key, value) -> key, (key, left, right) -> key + left + right,
                Named.as("named-global-left")).to("named-global-left-out");
        events.branch((key, value) -> value.isEmpty(), (key, value) -> !value.isEmpty());
        events.groupBy((key, value) -> value).count().toStream().to("counts");
        TopicNameExtractor<String, String> extractor = (key, value, context) -> "dynamic";
        events.to(extractor);

        Topology topology = builder.build();
        String description = topology.describe().toString();
        assertThat(description).contains("events", "table", "global", "extractor class", "counts");
    }

    @Test
    public void buildsWindowedAggregationsAndSuppression() {
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> stream = builder.stream("input", Consumed.with(Serdes.String(), Serdes.String()));

        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(1)))
                .count(Materialized.as("session-counts"));
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(2)))
                .reduce((left, right) -> left + right, Materialized.as("session-reduce"));
        stream.groupByKey().windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(3)))
                .aggregate(() -> "", (key, value, aggregate) -> aggregate + value,
                        (key, left, right) -> left + right, Named.as("session-aggregate"));
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(10)))
                .count(Materialized.as("sliding-counts"));
        stream.groupByKey().windowedBy(SlidingWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(20)))
                .reduce((left, right) -> left + right, Materialized.as("sliding-reduce"));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
                .reduce((left, right) -> left + right, Materialized.as("time-reduce"));
        stream.groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(2)))
                .count(Materialized.as("time-counts"))
                .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded().withLoggingDisabled()));

        String description = builder.build().describe().toString();
        assertThat(description).contains("session-counts", "session-reduce", "sliding-counts", "time-reduce");
    }

    @Test
    public void addsGlobalStoresThroughBothProcessorApis() {
        StreamsBuilder oldApiBuilder = new StreamsBuilder();
        oldApiBuilder.addGlobalStore(
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("old-store"), Serdes.String(), Serdes.String()),
                "old-source", Consumed.with(Serdes.String(), Serdes.String()),
                () -> new org.apache.kafka.streams.processor.AbstractProcessor<String, String>() {
                    @Override
                    public void process(String key, String value) {
                        context().forward(key, value);
                    }
                });
        assertThat(oldApiBuilder.build().describe().toString()).contains("old-store", "old-source");

        StreamsBuilder newApiBuilder = new StreamsBuilder();
        newApiBuilder.addGlobalStore(
                Stores.keyValueStoreBuilder(Stores.inMemoryKeyValueStore("new-store"), Serdes.String(), Serdes.String()),
                "new-source", Consumed.with(Serdes.String(), Serdes.String()),
                () -> new org.apache.kafka.streams.processor.api.Processor<String, String, Void, Void>() {
                    @Override
                    public void process(org.apache.kafka.streams.processor.api.Record<String, String> record) {
                    }
                });
        assertThat(newApiBuilder.build().describe().toString()).contains("new-store", "new-source");
    }
}
